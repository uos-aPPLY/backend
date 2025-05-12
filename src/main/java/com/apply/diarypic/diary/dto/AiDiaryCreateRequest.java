package com.apply.diarypic.diary.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiDiaryCreateRequest {

    @NotNull(message = "일기 날짜는 필수입니다.")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate diaryDate;

    @NotEmpty(message = "최종 사진 정보 목록은 비어있을 수 없습니다.")
    @Size(max = 9, message = "사진은 최대 9장까지 선택 가능합니다.")
    private List<FinalizedPhotoPayload> finalizedPhotos;

    private Long representativePhotoId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinalizedPhotoPayload {
        @NotNull(message = "사진 ID는 필수입니다.")
        private Long photoId;

        @NotNull(message = "키워드는 필수입니다. 없으면 빈 문자열이라도 전달해야 합니다.")
        private String keyword;

        @NotNull(message = "사진 순서는 필수입니다.")
        private Integer sequence;
    }
}