package com.apply.diarypic.photo.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiPhotoRecommendRequest {

    @NotEmpty(message = "업로드된 전체 사진 ID 목록은 비어있을 수 없습니다.")
    private List<Long> uploadedPhotoIds;

    private List<Long> mandatoryPhotoIds; // 비어있을 수 있음
}