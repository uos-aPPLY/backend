package com.apply.diarypic.diary.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DiaryManualUpdateRequest {

    @Size(max = 10000, message = "일기 내용은 최대 10000자까지 입력 가능합니다.")
    private String content;

    private String emotionIcon;
}