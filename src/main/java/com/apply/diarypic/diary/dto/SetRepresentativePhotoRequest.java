package com.apply.diarypic.diary.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SetRepresentativePhotoRequest {
    @NotNull(message = "대표로 지정할 사진 ID는 필수입니다.")
    private Long photoId; // 대표로 지정할 DiaryPhoto의 ID
}