package com.apply.diarypic.terms.entity;

public enum TermsType {
    AGE_CONFIRMATION("만 14세 이상입니다."), // [필수] 만 14세 이상입니다.
    SERVICE_TERMS("서비스 이용약관"),        // [필수] 서비스 이용약관
    PRIVACY_POLICY("개인정보 처리방침"),    // [필수] 개인정보 처리방침
    MARKETING_OPT_IN("마케팅 정보 수신 동의"); // [선택] 마케팅 정보 수신 동의

    private final String description;

    TermsType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}