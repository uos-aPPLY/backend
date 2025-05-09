package com.apply.diarypic.diary.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FavoriteToggleRequest {
    @NotNull(message = "좋아요 상태는 필수입니다.")
    private Boolean isFavorited;
}