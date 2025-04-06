package com.apply.diarypic.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateEmotionRequest {
    @NotBlank
    private String emotion;
}
