package com.apply.diarypic.terms.repository;

import com.apply.diarypic.terms.entity.Terms;
import com.apply.diarypic.terms.entity.TermsType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TermsRepository extends JpaRepository<Terms, Long> {

    @Query(value = "SELECT ranked_terms.* FROM ( " +
            "  SELECT t.*, ROW_NUMBER() OVER (PARTITION BY t.terms_type ORDER BY t.version DESC) as rn " +
            "  FROM terms t WHERE t.effective_date <= CURRENT_TIMESTAMP " +
            ") ranked_terms WHERE ranked_terms.rn = 1 " +
            // 정렬 순서 변경: 1. 필수여부(내림차순), 2. display_order(오름차순), 3. ID(오름차순)
            "ORDER BY ranked_terms.required DESC, ranked_terms.display_order ASC, ranked_terms.id ASC", nativeQuery = true)
    List<Terms> findLatestActiveTerms();

    Optional<Terms> findByTermsTypeAndVersion(TermsType termsType, int version);

    Optional<Terms> findFirstByTermsTypeOrderByVersionDesc(TermsType termsType);
}