package com.apply.diarypic.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImageInfoDto {
    private String photoUrl;
    private String shootingDateTime;
    private String detailedAddress;
    private String keyword;
    private int sequence;
}