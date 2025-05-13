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
        private Long termsId;
        @NotNull
        private Boolean agreed;
    }
}