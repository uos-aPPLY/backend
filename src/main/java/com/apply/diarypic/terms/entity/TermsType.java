package com.apply.diarypic.terms.entity;

public enum TermsType {
    AGE_CONFIRMATION("만 14세 이상입니다.", 1), // [필수] 만 14세 이상입니다.
    SERVICE_TERMS("서비스 이용약관", 2),        // [필수] 서비스 이용약관
    PRIVACY_POLICY("개인정보 처리방침", 3),    // [필수] 개인정보 처리방침
    MARKETING_OPT_IN("마케팅 정보 수신 동의", 4); // [선택] 마케팅 정보 수신 동의

    private final String description;
    private final int displayOrder; // 표시 순서 필드 추가

    TermsType(String description, int displayOrder) {
        this.description = description;
        this.displayOrder = displayOrder;
    }

    public String getDescription() {
        return description;
    }

    public int getDisplayOrder() { // getter 추가
        return displayOrder;
    }
}