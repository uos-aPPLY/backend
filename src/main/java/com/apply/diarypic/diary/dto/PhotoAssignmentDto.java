package com.apply.diarypic.diary.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor // 편의를 위해 추가
public class PhotoAssignmentDto {
    @NotNull(message = "사진 ID는 필수입니다.")
    private Long photoId;

    @NotNull(message = "사진 순서는 필수입니다.")
    private Integer sequence;
}