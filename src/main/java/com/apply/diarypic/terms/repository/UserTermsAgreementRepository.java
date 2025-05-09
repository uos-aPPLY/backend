package com.apply.diarypic.terms.repository;

import com.apply.diarypic.terms.entity.TermsType;
import com.apply.diarypic.terms.entity.UserTermsAgreement;
import com.apply.diarypic.terms.entity.UserTermsAgreementId;
import com.apply.diarypic.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;
import java.util.Optional;

public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, UserTermsAgreementId> {

    Optional<UserTermsAgreement> findByUserAndTermsId(User user, Long termsId);

    // 특정 사용자가 동의한 모든 (최신 버전의) 약관 정보
    List<UserTermsAgreement> findByUserAndAgreedTrue(User user);

    // 특정 사용자가 특정 타입의 최신 약관에 동의했는지 확인
    @Query("SELECT uta FROM UserTermsAgreement uta " +
            "WHERE uta.user = :user AND uta.agreed = true AND uta.terms.termsType = :termsType " +
            "AND uta.terms.version = (SELECT MAX(t.version) FROM Terms t WHERE t.termsType = :termsType)")
    Optional<UserTermsAgreement> findUserAgreementForLatestTermsByType(@Param("user") User user, @Param("termsType") TermsType termsType);
}