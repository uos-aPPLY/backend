package com.apply.diarypic.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiPhotoScoreRequestDto {
    private Long id;
    private String photoUrl;
    private String shootingDateTime;
    private String detailedAddress; // <<-- countryName, adminAreaLevel1, locality를 조합하여 채움
    private boolean isMandatory;
}