package com.apply.diarypic.util.dto; // 예시 패키지

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DailyQuoteResponse {
    private int dayOfWeek; // 1 (월요일) ~ 7 (일요일)
    private String quote;
}