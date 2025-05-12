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
public class AiImageScoringRequestDto {
    @JsonProperty("images") // AI 서버 Pydantic 모델 필드명과 일치
    private List<AiPhotoInputDto> images;

    @JsonProperty("reference_images") // AI 서버 Pydantic 모델 필드명과 일치z
    private List<AiPhotoInputDto> referenceImages;
}