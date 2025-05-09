package com.apply.diarypic.terms.repository;

import com.apply.diarypic.terms.entity.Terms;
import com.apply.diarypic.terms.entity.TermsType; // Ensure TermsType Enum is imported
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
// import org.springframework.data.repository.query.Param; // Only if using @Param, not needed for these methods

import java.util.List;
import java.util.Optional;

public interface TermsRepository extends JpaRepository<Terms, Long> {

    /**
     * 현재 사용자에게 보여줄 (가장 최신 버전의) 모든 활성 약관 목록을 조회합니다.
     * 각 약관 타입(termsType)별로 가장 최신 버전이면서 효력 발생일(effectiveDate)이
     * 현재 시간 이전 또는 같은 약관들만 선택합니다.
     * 필수 약관이 먼저 정렬되고, 그 다음 ID 순으로 정렬됩니다.
     * (MySQL 8.0+ / H2 등 윈도우 함수 지원 DB에서 권장)
     */
    @Query(value = "SELECT ranked_terms.* FROM ( " +
            "  SELECT t.*, ROW_NUMBER() OVER (PARTITION BY t.terms_type ORDER BY t.version DESC) as rn " +
            "  FROM terms t WHERE t.effective_date <= CURRENT_TIMESTAMP " +
            ") ranked_terms WHERE ranked_terms.rn = 1 " +
            "ORDER BY ranked_terms.required DESC, ranked_terms.id ASC", nativeQuery = true)
    List<Terms> findLatestActiveTerms();

    /**
     * 특정 약관 타입(TermsType)과 버전(version)으로 약관을 조회합니다.
     * TermsService의 getTermsDetails 메소드에서 사용됩니다.
     */
    Optional<Terms> findByTermsTypeAndVersion(TermsType termsType, int version);

    /**
     * 특정 약관 타입(TermsType)의 가장 최신 버전 약관을 조회합니다.
     * TermsService의 updateUserAgreements (최신 버전 강제 로직) 및 DataInitializer에서 사용됩니다.
     */
    Optional<Terms> findFirstByTermsTypeOrderByVersionDesc(TermsType termsType);
}