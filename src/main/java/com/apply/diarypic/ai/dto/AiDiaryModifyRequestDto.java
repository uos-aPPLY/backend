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
    private String userSpeech;
    private String diary;
    private String userRequest;
}