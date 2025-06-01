package com.apply.diarypic.terms.entity;

import lombok.Getter;

@Getter
public enum TermsType {
    AGE_CONFIRMATION("만 14세 이상 확인", 1),
    SERVICE_TERMS("서비스 이용약관", 2),
    PRIVACY_POLICY("개인정보 처리방침", 3),
    PERSONAL_INFO_COLLECTION_AGREEMENT("개인정보 수집-이용 동의", 4),
    MARKETING_OPT_IN("마케팅 정보 수신 동의", 5);

    private final String description;
    private final int displayOrder;

    TermsType(String description, int displayOrder) {
        this.description = description;
        this.displayOrder = displayOrder;
    }
}