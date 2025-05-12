package com.apply.diarypic.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiDiaryModifyRequestDto {
    private String userSpeech; // 사용자의 글쓰기 스타일 프롬프트
    private String diary;      // <edit token>이 포함된 수정 대상 일기 내용
    private String userRequest;// 사용자의 구체적인 수정 요청 사항
}