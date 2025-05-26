package com.apply.diarypic.diary.dto;

import com.apply.diarypic.diary.entity.Diary;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class CalendarDiaryInfoResponse {
    private Long diaryId;
    private String emotionIcon;
    private String status;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate diaryDate;
    private String representativePhotoUrl;

    public static CalendarDiaryInfoResponse from(Diary diary) {
        if (diary == null) {
            return null;
        }
        return CalendarDiaryInfoResponse.builder()
                .diaryId(diary.getId())
                .emotionIcon(diary.getEmotionIcon())
                .status(diary.getStatus())
                .diaryDate(diary.getDiaryDate())
                .representativePhotoUrl(diary.getRepresentativePhotoUrl())
                .build();
    }
}