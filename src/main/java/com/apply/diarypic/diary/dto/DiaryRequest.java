package com.apply.diarypic.diary.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class DiaryRequest {

    @NotNull(message = "일기 날짜는 필수입니다.")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate diaryDate;

    @NotNull(message = "일기 내용은 필수입니다.")
    private String content;

    private String emotionIcon;

    @Size(max = 9, message = "사진은 최대 9장까지 선택 가능합니다.")
    private List<Long> photoIds = new ArrayList<>();

    private Long representativePhotoId;
}