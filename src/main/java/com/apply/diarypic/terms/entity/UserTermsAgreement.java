package com.apply.diarypic.terms.entity;

import com.apply.diarypic.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_terms_agreements")
@Getter
@Setter // 동의 철회 시 상태 변경 등을 위해 Setter 허용
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@IdClass(UserTermsAgreementId.class) // 복합키 클래스
public class UserTermsAgreement {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terms_id", nullable = false)
    private Terms terms; // 동의한 약관 (특정 버전 포함)

    @Column(nullable = false)
    private boolean agreed; // true: 동의, false: 철회 (또는 레코드 삭제로 철회 표현)

    @Column(nullable = false)
    private LocalDateTime agreedAt; // 동의/철회 시각

    // 필요하다면, 동의 당시의 IP 주소 등 추가 정보 기록 가능
    // private String agreedFromIp;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (this.agreedAt == null) {
            this.agreedAt = LocalDateTime.now();
        }
    }
}