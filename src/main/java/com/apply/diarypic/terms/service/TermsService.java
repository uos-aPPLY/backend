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
import java.util.List;
import java.util.Map;
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        List<Terms> latestTerms = termsRepository.findLatestActiveTerms();
        List<UserTermsAgreement> userAgreements = userTermsAgreementRepository.findByUserAndAgreedTrue(user);

        Map<Long, Boolean> agreedTermsMap = userAgreements.stream()
                .collect(Collectors.toMap(uta -> uta.getTerms().getId(), UserTermsAgreement::isAgreed));

        return latestTerms.stream()
                .map(terms -> TermsDto.builder() // TermsDto.fromEntityWithAgreement 대신 빌더 직접 사용 또는 fromEntity 수정
                        .id(terms.getId())
                        .termsType(terms.getTermsType().name()) // Enum.name()으로 문자열 변환
                        .title(terms.getTitle())
                        .content(terms.getContent())
                        .version(terms.getVersion())
                        .required(terms.isRequired())
                        .effectiveDate(terms.getEffectiveDate())
                        .agreed(agreedTermsMap.getOrDefault(terms.getId(), false))
                        .build())
                .collect(Collectors.toList());
    }

    public TermsDto getTermsDetails(TermsType termsType, int version) {
        Terms terms = termsRepository.findByTermsTypeAndVersion(termsType, version)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Terms not found with type: " + termsType + " and version: " + version));
        return TermsDto.fromEntity(terms); // TermsDto.fromEntity가 TermsType Enum을 처리하도록 확인
    }

    @Transactional
    public void updateUserAgreements(Long userId, UserAgreementRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        for (UserAgreementRequest.AgreementItem item : request.getAgreements()) {
            Terms terms = termsRepository.findById(item.getTermsId())
                    .orElseThrow(() -> new EntityNotFoundException("Terms not found with id: " + item.getTermsId()));

            // (선택적) 최신 버전 약관 동의 강제 로직
            Terms latestVersionOfThisType = termsRepository.findFirstByTermsTypeOrderByVersionDesc(terms.getTermsType())
                    .orElseThrow(() -> new EntityNotFoundException("Cannot find latest version for terms type: " + terms.getTermsType()));
            if (!terms.getId().equals(latestVersionOfThisType.getId())) {
                log.warn("Attempt to agree to an outdated terms version. User: {}, Terms ID: {}, Submitted Version: {}, Latest Version: {}",
                        userId, terms.getId(), terms.getVersion(), latestVersionOfThisType.getVersion());
                throw new IllegalArgumentException("동의하려는 약관 '" + terms.getTitle() + "'이(가) 최신 버전이 아닙니다. 페이지를 새로고침 후 다시 시도해주세요.");
            }

            UserTermsAgreement agreement = userTermsAgreementRepository.findByUserAndTermsId(user, terms.getId())
                    .orElseGet(() -> UserTermsAgreement.builder()
                            .user(user)
                            .terms(terms)
                            .build());

            agreement.setAgreed(item.getAgreed());
            agreement.setAgreedAt(LocalDateTime.now());
            userTermsAgreementRepository.save(agreement);

            log.info("User {} {} terms '{}' (type: {}, version {}) at {}",
                    userId, item.getAgreed() ? "agreed to" : "revoked agreement for",
                    terms.getTitle(), terms.getTermsType().name(), terms.getVersion(), agreement.getAgreedAt());
        }
    }

    public boolean hasAgreedToAllRequiredTerms(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        List<Terms> latestRequiredTerms = termsRepository.findLatestActiveTerms().stream()
                .filter(Terms::isRequired)
                .toList();

        if (latestRequiredTerms.isEmpty()) {
            return true;
        }

        List<UserTermsAgreement> userAgreements = userTermsAgreementRepository.findByUserAndAgreedTrue(user);

        Map<TermsType, Integer> agreedLatestVersionsMap = userAgreements.stream()
                .filter(uta -> uta.getTerms().getEffectiveDate() != null && uta.getTerms().getEffectiveDate().isBefore(LocalDateTime.now()))
                .collect(Collectors.toMap(
                        uta -> uta.getTerms().getTermsType(), // 이제 TermsType Enum 직접 사용
                        uta -> uta.getTerms().getVersion(),
                        Integer::max
                ));

        for (Terms requiredTerm : latestRequiredTerms) {
            TermsType termsTypeKey = requiredTerm.getTermsType();
            Integer agreedVersion = agreedLatestVersionsMap.get(termsTypeKey);

            if (agreedVersion == null || agreedVersion < requiredTerm.getVersion()) {
                log.warn("User {} has not agreed to the latest required terms: {} (v{}) or agreed to an older version (v{}).",
                        userId, termsTypeKey.name(), requiredTerm.getVersion(), agreedVersion == null ? "N/A" : agreedVersion);
                return false;
            }
        }
        return true;
    }
}