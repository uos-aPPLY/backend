// com.apply.diarypic.terms.service.TermsService.java
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
    private final UserRepository userRepository; // UserRepository가 이미 주입되어 있다고 가정

    // DataInitializer의 createTermIfNotExists와 유사한 로직 (TermsService 내부에서만 사용)
    private Terms createTermIfNotExistsInternal(TermsType termsType, int version, String title, String content, boolean required) {
        return termsRepository.findByTermsTypeAndVersion(termsType, version)
                .orElseGet(() -> {
                    Terms term = Terms.builder()
                            .termsType(termsType)
                            .version(version)
                            .title(title)
                            .content(content)
                            .required(required)
                            .effectiveDate(LocalDateTime.now()) // 생성 시점을 기준으로 발효
                            .build();
                    Terms savedTerm = termsRepository.save(term);
                    log.info("약관 '{}' (type: {}, v{})이(가) DB에 저장되었습니다.", title, termsType.name(), version);
                    return savedTerm;
                });
    }

    @Transactional
    public void initializeTermsAndCreateAgreementsForNewUser(User user) {
        log.info("신규 사용자 {}를 위한 약관 마스터 데이터 확인/생성 및 UserTermsAgreement 초기화 시작...", user.getId());

        // 1. 약관 마스터 데이터(Terms) 생성 또는 확인 (DataInitializer의 역할 수행)
        //    각 약관 타입에 대해 최신 버전이 없으면 생성하도록 할 수 있으나, 여기서는 고정된 초기 버전을 생성합니다.
        //    실제 운영에서는 버전 관리가 더 중요합니다.
        Terms ageConfirmTerm = createTermIfNotExistsInternal(TermsType.AGE_CONFIRMATION, 1, TermsType.AGE_CONFIRMATION.getDescription(), "본 서비스는 만 14세 이상 사용자만 이용 가능합니다. 동의하시면 만 14세 이상임을 확인하는 것입니다.", true);
        Terms serviceTermsTerm = createTermIfNotExistsInternal(TermsType.SERVICE_TERMS, 1, TermsType.SERVICE_TERMS.getDescription(), "제1조 (목적) ... (실제 약관 내용)", true);
        Terms privacyPolicyTerm = createTermIfNotExistsInternal(TermsType.PRIVACY_POLICY, 1, TermsType.PRIVACY_POLICY.getDescription(), "제1조 (개인정보의 처리 목적) ... (실제 약관 내용)", true);
        Terms marketingOptInTerm = createTermIfNotExistsInternal(TermsType.MARKETING_OPT_IN, 1, TermsType.MARKETING_OPT_IN.getDescription(), "새로운 소식, 이벤트 및 프로모션 정보를 이메일 또는 푸시 알림으로 받아보시겠습니까?", false);

        // 생성(또는 조회)된 Terms 객체 리스트 (여기서는 개별 객체로 바로 사용)
        List<Terms> allInitializedTerms = List.of(ageConfirmTerm, serviceTermsTerm, privacyPolicyTerm, marketingOptInTerm);

        // 2. 해당 사용자에 대한 UserTermsAgreement 레코드 생성 (미동의 상태)
        for (Terms term : allInitializedTerms) {
            if (term != null) { // createTermIfNotExistsInternal이 항상 객체를 반환하므로 null 체크는 사실상 불필요
                // 이미 해당 사용자의 약관 동의 정보가 있는지 확인 (신규 사용자이므로 보통 없음)
                if (!userTermsAgreementRepository.findByUserAndTermsId(user, term.getId()).isPresent()) {
                    UserTermsAgreement initialAgreement = UserTermsAgreement.builder()
                            .user(user)
                            .terms(term)
                            .agreed(false) // 기본적으로 미동의 상태
                            .agreedAt(LocalDateTime.now()) // 또는 null로 두고 실제 동의 시 설정
                            .build();
                    userTermsAgreementRepository.save(initialAgreement);
                    log.info("신규 사용자 {}를 위해 약관 '{}'에 대한 초기 UserTermsAgreement 레코드 생성 (미동의 상태)", user.getId(), term.getTitle());
                } else {
                    log.info("신규 사용자 {}는 이미 약관 '{}'에 대한 UserTermsAgreement 레코드가 존재합니다.", user.getId(), term.getTitle());
                }
            }
        }
        log.info("신규 사용자 {}를 위한 약관 마 litros터 데이터 확인/생성 및 UserTermsAgreement 초기화 완료.", user.getId());
    }


    // --- 기존 TermsService 메소드들 ---
    public List<TermsDto> getLatestTermsForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        List<Terms> latestTerms = termsRepository.findLatestActiveTerms();
        List<UserTermsAgreement> userAgreements = userTermsAgreementRepository.findByUserAndAgreedTrue(user);

        Map<Long, Boolean> agreedTermsMap = userAgreements.stream()
                .collect(Collectors.toMap(uta -> uta.getTerms().getId(), UserTermsAgreement::isAgreed));

        return latestTerms.stream()
                .map(terms -> TermsDto.builder()
                        .id(terms.getId())
                        .termsType(terms.getTermsType().name())
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
        return TermsDto.fromEntity(terms);
    }

    @Transactional
    public void updateUserAgreements(Long userId, UserAgreementRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        for (UserAgreementRequest.AgreementItem item : request.getAgreements()) {
            Terms terms = termsRepository.findById(item.getTermsId())
                    .orElseThrow(() -> new EntityNotFoundException("Terms not found with id: " + item.getTermsId()));

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
                        uta -> uta.getTerms().getTermsType(),
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