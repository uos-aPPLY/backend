package com.apply.diarypic.diary.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DiaryAiUpdateRequest {

    @NotEmpty(message = "수정할 일기 내용은 비어있을 수 없습니다.")
    private String markedDiaryContent; // <edit token>이 포함된, 수정 대상 일기 내용

    @NotEmpty(message = "사용자 요청 사항은 비어있을 수 없습니다.")
    private String userRequest; // AI에게 전달할 사용자의 수정 요청 사항
}