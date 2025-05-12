package com.apply.diarypic.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiImageScoringResponseDto {
    @JsonProperty("recommendedPhotoIds") // AI 서버 Pydantic 모델 필드명과 일치
    private List<Object> recommendedPhotoIds; // Union[int, str]을 Java에서 Object로 받고, 서비스에서 Long으로 변환
}