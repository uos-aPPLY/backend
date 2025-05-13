package com.apply.diarypic.terms.entity;

import com.apply.diarypic.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_terms_agreements")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@IdClass(UserTermsAgreementId.class)
public class UserTermsAgreement {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terms_id", nullable = false)
    private Terms terms;

    @Column(nullable = false)
    private boolean agreed;

    @Column(nullable = false)
    private LocalDateTime agreedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (this.agreedAt == null) {
            this.agreedAt = LocalDateTime.now();
        }
    }
}