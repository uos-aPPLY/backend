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
    private List<Long> uploadedPhotoIds; // 사용자가 현재 세션에서 업로드한 모든 임시 사진의 ID 목록

    // 사용자가 필수로 선택한 사진이 없을 수도 있으므로 @NotEmpty는 제외
    private List<Long> mandatoryPhotoIds; // 위 uploadedPhotoIds 중에서 사용자가 '반드시 포함할 사진'으로 선택한 ID 목록
}