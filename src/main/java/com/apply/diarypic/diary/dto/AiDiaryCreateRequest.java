package com.apply.diarypic.diary.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiDiaryCreateRequest {

    @NotEmpty(message = "최종 사진 정보 목록은 비어있을 수 없습니다.")
    @Size(max = 9, message = "사진은 최대 9장까지 선택 가능합니다.")
    private List<FinalizedPhotoPayload> finalizedPhotos; // 사용자가 최종 선택 및 정렬한 사진 정보 리스트

    // 내부 클래스 또는 별도 파일로 정의 가능
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinalizedPhotoPayload {
        @NotNull(message = "사진 ID는 필수입니다.")
        private Long photoId; // DiaryPhoto의 ID (DB에 저장된 사진의 PK)

        @NotNull(message = "키워드는 필수입니다. 없으면 빈 문자열이라도 전달해야 합니다.")
        private String keyword; // 사용자가 입력한 키워드

        @NotNull(message = "사진 순서는 필수입니다.")
        private Integer sequence; // 사용자가 지정한 순서 (1부터 시작)
    }
}