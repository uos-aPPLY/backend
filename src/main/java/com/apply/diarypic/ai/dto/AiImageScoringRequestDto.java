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
    @JsonProperty("images")
    private List<AiPhotoInputDto> images;

    @JsonProperty("reference_images")
    private List<AiPhotoInputDto> referenceImages;
}