package com.apply.diarypic.terms.service;

import com.apply.diarypic.terms.dto.TermsDto;
import com.apply.diarypic.terms.dto.UserAgreementRequest;
import com.apply.diarypic.terms.entity.Terms;
import com.apply.diarypic.terms.entity.TermsType;
import com.apply.diarypic.terms.entity.UserTermsAgreement;
import com.apply.diarypic.terms.repository.TermsRepository;
import com.apply.diarypic.terms.repository.UserTermsAgreementRepository;
import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections; // 추가
import java.util.List;
import java.util.Map;
import java.util.Objects; // 추가
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
        if (latestTerms.isEmpty()) { // DataInitializer가 실행되지 않았거나 약관이 없는 경우
            log.warn("[TermsService] 표시할 최신 약관이 없습니다. DB에 약관 마스터 데이터가 있는지 확인하세요.");
            return Collections.emptyList();
        }
        log.info("[TermsService] 최신 약관 목록(latestTerms) 조회 완료. 개수: {}", latestTerms.size());

        // 사용자가 동의한(agreed=true) 약관 기록만 가져옴
        List<UserTermsAgreement> userAgreements = userTermsAgreementRepository.findByUserAndAgreedTrue(user);
        log.info("[TermsService] 사용자 동의 약관 목록(userAgreements) 조회 완료. 개수: {}", userAgreements.size());

        Map<Long, Boolean> agreedTermsMap = userAgreements.stream()
                .filter(uta -> uta.getTerms() != null) // 방어 코드
                .collect(Collectors.toMap(
                        uta -> uta.getTerms().getId(),
                        UserTermsAgreement::isAgreed,
                        (existing, replacement) -> existing // 혹시 모를 중복 키 처리 (이론상 isAgreed=true 조건으론 발생 안 함)
                ));
        log.info("[TermsService] 동의된 약관 Map 생성 완료. Map 크기: {}", agreedTermsMap.size());

        List<TermsDto> termsDtoList = latestTerms.stream()
                .map(term -> {
                    if (term == null || term.getTermsType() == null) { // 방어 코드
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
                            .agreed(agreedTermsMap.getOrDefault(term.getId(), false)) // 동의 기록 없으면 false
                            .build();
                })
                .filter(Objects::nonNull) // null DTO 제거
                .collect(Collectors.toList());

        log.info("[TermsService] TermsDto 리스트 변환 완료. 결과 개수: {}", termsDtoList.size());
        log.info("[TermsService] getLatestTermsForUser 종료 - userId: {}", userId);
        return termsDtoList;
    }

    @Transactional
    public void updateUserAgreements(Long userId, UserAgreementRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        for (UserAgreementRequest.AgreementItem item : request.getAgreements()) {
            Terms terms = termsRepository.findById(item.getTermsId())
                    .orElseThrow(() -> new EntityNotFoundException("Terms not found with id: " + item.getTermsId()));

            // 최신 버전 약관인지 다시 한번 확인 (클라이언트가 이전 버전 ID를 보낼 수도 있으므로)
            Terms latestVersionOfThisType = termsRepository.findFirstByTermsTypeOrderByVersionDesc(terms.getTermsType())
                    .orElseThrow(() -> new EntityNotFoundException("Cannot find latest version for terms type: " + terms.getTermsType()));

            if (!terms.getId().equals(latestVersionOfThisType.getId())) {
                log.warn("User {} attempted to agree to an outdated terms (ID: {}, Version: {}). Latest is Version: {}.",
                        userId, terms.getId(), terms.getVersion(), latestVersionOfThisType.getVersion());
                // 필요하다면 여기서 예외를 던지거나, 클라이언트가 반드시 최신 약관 ID를 보내도록 강제해야 함.
                // 현재 로직은 클라이언트가 보낸 termsId에 해당하는 약관이 최신 버전이 아니면 예외를 던짐. (제공해주신 코드 유지)
                throw new IllegalArgumentException("동의하려는 약관 '" + terms.getTitle() + "'이(가) 최신 버전(" + latestVersionOfThisType.getVersion() + ")이 아닙니다. 현재 버전: " + terms.getVersion());
            }

            UserTermsAgreement agreement = userTermsAgreementRepository.findByUserAndTermsId(user, terms.getId())
                    .orElseGet(() -> UserTermsAgreement.builder()
                            .user(user)
                            .terms(terms) // 동의하는 특정 버전의 약관
                            .build());

            agreement.setAgreed(item.getAgreed());
            agreement.setAgreedAt(LocalDateTime.now()); // 동의/철회 시각 업데이트
            userTermsAgreementRepository.save(agreement);

            log.info("User {} {} terms '{}' (ID: {}, type: {}, version {}) at {}",
                    userId, item.getAgreed() ? "agreed to" : "revoked agreement for",
                    terms.getTitle(), terms.getId(), terms.getTermsType().name(), terms.getVersion(), agreement.getAgreedAt());
        }
    }

    public boolean hasAgreedToAllRequiredTerms(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        // DB에서 '현재 활성화된 & 필수인' 최신 버전 약관들만 가져옴
        List<Terms> latestRequiredTerms = termsRepository.findLatestActiveTerms().stream()
                .filter(Terms::isRequired)
                .toList();

        if (latestRequiredTerms.isEmpty()) {
            log.info("[TermsService] 필수 약관이 설정되어 있지 않습니다. userId: {}", userId);
            return true; // 필수 약관이 없으면 항상 true
        }

        // 해당 사용자가 '동의(agreed=true)'한 모든 약관 동의 기록을 가져옴
        List<UserTermsAgreement> agreedUserAgreements = userTermsAgreementRepository.findByUserAndAgreedTrue(user);

        for (Terms requiredTerm : latestRequiredTerms) {
            // 사용자가 동의한 기록 중에서 현재 필수 약관(ID 기준)과 일치하는 것이 있는지 확인
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

    // getTermsDetails 메소드는 필요하다면 그대로 유지 (현재 제공된 코드에는 없음)
}