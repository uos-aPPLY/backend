package com.apply.diarypic.terms.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserTermsAgreementId implements Serializable {
    private Long user;    // User 엔티티의 id 필드명
    private Long terms;   // Terms 엔티티의 id 필드명
}