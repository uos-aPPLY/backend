package com.apply.diarypic.global.config;

import com.apply.diarypic.terms.entity.Terms;
import com.apply.diarypic.terms.entity.TermsType;
import com.apply.diarypic.terms.repository.TermsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final TermsRepository termsRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("초기 데이터 적재 시작...");
        initializeTerms();
        log.info("초기 데이터 적재 완료.");
    }

    private void initializeTerms() {
        log.info("약관 데이터 초기화 중...");
        createTermIfNotExists(TermsType.AGE_CONFIRMATION, 1, TermsType.AGE_CONFIRMATION.getDescription(), "본 서비스는 만 14세 이상 사용자만 이용 가능합니다. 동의하시면 만 14세 이상임을 확인하는 것입니다.", true, TermsType.AGE_CONFIRMATION.getDisplayOrder());
        createTermIfNotExists(TermsType.SERVICE_TERMS, 1, TermsType.SERVICE_TERMS.getDescription(), "제1조 (목적) ... (실제 약관 내용)", true, TermsType.SERVICE_TERMS.getDisplayOrder());
        // SERVICE_TERMS v2를 만들고 싶다면 (예시):
        // createTermIfNotExists(TermsType.SERVICE_TERMS, 2, "서비스 이용약관 v2", "제1조 (목적) ... (변경된 내용)", true, TermsType.SERVICE_TERMS.getDisplayOrder());
        createTermIfNotExists(TermsType.PRIVACY_POLICY, 1, TermsType.PRIVACY_POLICY.getDescription(), "제1조 (개인정보의 처리 목적) ... (실제 약관 내용)", true, TermsType.PRIVACY_POLICY.getDisplayOrder());
        createTermIfNotExists(TermsType.MARKETING_OPT_IN, 1, TermsType.MARKETING_OPT_IN.getDescription(), "새로운 소식, 이벤트 및 프로모션 정보를 이메일 또는 푸시 알림으로 받아보시겠습니까?", false, TermsType.MARKETING_OPT_IN.getDisplayOrder());
        log.info("약관 데이터 초기화 완료.");
    }

    private void createTermIfNotExists(TermsType termsType, int version, String title, String content, boolean required, int displayOrder) {
        if (!termsRepository.findByTermsTypeAndVersion(termsType, version).isPresent()) {
            if (!termsRepository.findFirstByTermsTypeOrderByVersionDesc(termsType).filter(t -> t.getVersion() > version).isPresent()) {
                Terms term = Terms.builder()
                        .termsType(termsType)
                        .version(version)
                        .title(title)
                        .content(content)
                        .required(required)
                        .effectiveDate(LocalDateTime.now())
                        .displayOrder(displayOrder)
                        .build();
                termsRepository.save(term);
                log.info("약관 '{}' (type: {}, v{}, order:{}) 저장 완료.", title, termsType.name(), version, displayOrder);
            } else {
                log.info("약관 '{}' (type: {}, v{}) 보다 최신 버전이 이미 존재합니다. (삽입 건너뜀)", title, termsType.name(), version);
            }
        } else {
            log.info("약관 '{}' (type: {}, v{}) (은)는 이미 정확히 일치하는 버전으로 존재합니다.", title, termsType.name(), version);
        }
    }
}