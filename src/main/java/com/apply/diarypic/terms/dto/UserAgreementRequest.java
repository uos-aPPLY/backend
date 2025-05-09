package com.apply.diarypic.terms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class UserAgreementRequest {

    @NotNull
    private List<AgreementItem> agreements;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AgreementItem {
        @NotNull
        private Long termsId; // 동의/비동의하는 약관의 ID (Terms 엔티티의 PK)
        @NotNull
        private Boolean agreed; // 동의 여부 (true/false)
    }
}