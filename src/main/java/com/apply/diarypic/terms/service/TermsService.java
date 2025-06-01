package com.apply.diarypic.terms.service;

import com.apply.diarypic.terms.dto.TermsDto;
import com.apply.diarypic.terms.dto.UserAgreementRequest;
import com.apply.diarypic.terms.entity.Terms;
import com.apply.diarypic.terms.entity.TermsType;
import com.apply.diarypic.terms.entity.UserTermsAgreement;
import com.apply.diarypic.terms.repository.TermsRepository;
import com.apply.diarypic.terms.repository.UserTermsAgreementRepository;
import com.apply.diarypic.user.dto.UserNicknameResponseDto;
import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TermsService {

    private final TermsRepository termsRepository;
    private final UserTermsAgreementRepository userTermsAgreementRepository;
    private final UserRepository userRepository;


    public List<TermsDto> getLatestTermsForUser(Long userId) {
        log.info("[TermsService] getLatestTermsForUser 시작 - userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("[TermsService] User not found with id: {}", userId);
                    return new EntityNotFoundException("User not found with id: " + userId);
                });
        log.info("[TermsService] 사용자 조회 완료: userId={}", user.getId());

        List<Terms> latestTerms = termsRepository.findLatestActiveTerms();
        if (latestTerms.isEmpty()) {
            log.warn("[TermsService] 표시할 최신 약관이 없습니다. DB에 약관 마스터 데이터가 있는지 확인하세요.");
            return Collections.emptyList();
        }
        log.info("[TermsService] 최신 약관 목록(latestTerms) 조회 완료. 개수: {}", latestTerms.size());

        List<UserTermsAgreement> userAgreements = userTermsAgreementRepository.findByUserAndAgreedTrue(user);
        log.info("[TermsService] 사용자 동의 약관 목록(userAgreements) 조회 완료. 개수: {}", userAgreements.size());

        Map<Long, Boolean> agreedTermsMap = userAgreements.stream()
                .filter(uta -> uta.getTerms() != null)
                .collect(Collectors.toMap(
                        uta -> uta.getTerms().getId(),
                        UserTermsAgreement::isAgreed,
                        (existing, replacement) -> existing
                ));
        log.info("[TermsService] 동의된 약관 Map 생성 완료. Map 크기: {}", agreedTermsMap.size());

        List<TermsDto> termsDtoList = latestTerms.stream()
                .map(term -> {
                    if (term == null || term.getTermsType() == null) {
                        log.error("[TermsService] Terms 객체 또는 TermsType이 null입니다. term: {}", term);
                        return null;
                    }
                    return TermsDto.builder()
                            .id(term.getId())
                            .termsType(term.getTermsType().name())
                            .title(term.getTitle())
                            .content(term.getContent())
                            .version(term.getVersion())
                            .required(term.isRequired())
                            .effectiveDate(term.getEffectiveDate())
                            .agreed(agreedTermsMap.getOrDefault(term.getId(), false))
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("[TermsService] TermsDto 리스트 변환 완료. 결과 개수: {}", termsDtoList.size());
        log.info("[TermsService] getLatestTermsForUser 종료 - userId: {}", userId);
        return termsDtoList;
    }

    @Transactional
    public UserNicknameResponseDto updateUserAgreements(Long userId, UserAgreementRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("[TermsService] updateUserAgreements - User not found with id: {}", userId);
                    return new EntityNotFoundException("User not found with id: " + userId);
                });
        log.info("[TermsService] updateUserAgreements - 사용자 조회 완료: userId={}", user.getId());

        for (UserAgreementRequest.AgreementItem item : request.getAgreements()) {
            Terms terms = termsRepository.findById(item.getTermsId())
                    .orElseThrow(() -> {
                        log.error("[TermsService] updateUserAgreements - Terms not found with id: {}", item.getTermsId());
                        return new EntityNotFoundException("Terms not found with id: " + item.getTermsId());
                    });

            Terms latestVersionOfThisType = termsRepository.findFirstByTermsTypeOrderByVersionDesc(terms.getTermsType())
                    .orElseThrow(() -> {
                        log.error("[TermsService] updateUserAgreements - Cannot find latest version for terms type: {}", terms.getTermsType());
                        return new EntityNotFoundException("Cannot find latest version for terms type: " + terms.getTermsType());
                    });

            if (!terms.getId().equals(latestVersionOfThisType.getId())) {
                log.warn("[TermsService] updateUserAgreements - User {} attempted to agree to an outdated terms (ID: {}, Version: {}). Latest is Version: {}.",
                        userId, terms.getId(), terms.getVersion(), latestVersionOfThisType.getVersion());
                throw new IllegalArgumentException("동의하려는 약관 '" + terms.getTitle() + "'이(가) 최신 버전(" + latestVersionOfThisType.getVersion() + ")이 아닙니다. 현재 버전: " + terms.getVersion());
            }

            UserTermsAgreement agreement = userTermsAgreementRepository.findByUserAndTermsId(user, terms.getId())
                    .orElseGet(() -> UserTermsAgreement.builder()
                            .user(user)
                            .terms(terms)
                            .build());

            agreement.setAgreed(item.getAgreed());
            agreement.setAgreedAt(LocalDateTime.now());
            userTermsAgreementRepository.save(agreement);

            log.info("[TermsService] updateUserAgreements - User {} {} terms '{}' (ID: {}, type: {}, version {}) at {}",
                    userId, item.getAgreed() ? "agreed to" : "revoked agreement for",
                    terms.getTitle(), terms.getId(), terms.getTermsType().name(), terms.getVersion(), agreement.getAgreedAt());
        }
        log.info("[TermsService] updateUserAgreements - 사용자 약관 동의 처리 완료. userId: {}", userId);

        return new UserNicknameResponseDto(user.getNickname());
    }

    public boolean hasAgreedToAllRequiredTerms(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        List<Terms> latestRequiredTerms = termsRepository.findLatestActiveTerms().stream()
                .filter(Terms::isRequired)
                .toList();

        if (latestRequiredTerms.isEmpty()) {
            log.info("[TermsService] 필수 약관이 설정되어 있지 않습니다. userId: {}", userId);
            return true;
        }

        List<UserTermsAgreement> agreedUserAgreements = userTermsAgreementRepository.findByUserAndAgreedTrue(user);

        for (Terms requiredTerm : latestRequiredTerms) {
            boolean hasAgreed = agreedUserAgreements.stream()
                    .anyMatch(agreement -> agreement.getTerms().getId().equals(requiredTerm.getId()));

            if (!hasAgreed) {
                log.warn("[TermsService] User {} has not agreed to the required term: {} (ID: {}, v{})",
                        userId, requiredTerm.getTermsType().name(), requiredTerm.getId(), requiredTerm.getVersion());
                return false;
            }
        }

        log.info("[TermsService] User {} has agreed to all required terms.", userId);
        return true;
    }

    /**
     * 특정 타입의 최신 유효 약관 내용을 조회합니다. (HTML String 반환)
     *
     * @param termsType 조회할 약관의 타입
     * @return 해당 약관 타입의 최신 버전 내용 (HTML)
     * @throws EntityNotFoundException 해당 타입의 약관을 찾을 수 없는 경우
     */
    public String getLatestActiveTermsContentByType(TermsType termsType) {
        log.info("[TermsService] getLatestActiveTermsContentByType 시작 - termsType: {}", termsType.name());

        Terms latestTerm = termsRepository.findFirstByTermsTypeOrderByVersionDesc(termsType)
                .filter(term -> term.getEffectiveDate() != null && term.getEffectiveDate().isBefore(LocalDateTime.now().plusSeconds(1)))
                .orElseThrow(() -> {
                    log.warn("[TermsService] 유효한 최신 약관을 찾을 수 없습니다. termsType: {}", termsType.name());
                    return new EntityNotFoundException("현재 유효한 " + termsType.getDescription() + " 약관을 찾을 수 없습니다.");
                });

        log.info("[TermsService] 최신 약관 조회 완료: title='{}', version={}, termsType={}", latestTerm.getTitle(), latestTerm.getVersion(), termsType.name());
        log.info("[TermsService] getLatestActiveTermsContentByType 종료 - termsType: {}", termsType.name());
        return latestTerm.getContent();
    }
}