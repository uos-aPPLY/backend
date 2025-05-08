package com.apply.diarypic.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiDiaryResponseDto {
    private String diary_text; // AI 서버가 {"diary_text": "..."} 형태로 응답한다고 가정
    // 만약 순수 텍스트로 응답한다면, 이 DTO는 필요 없고 String으로 받습니다.
}