package com.apply.diarypic.diary.dto;

import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.photo.dto.PhotoResponse;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class DiaryResponse {
    private Long id;
    private String content;
    private String emotionIcon;
    private Boolean isFavorited;
    private String status;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate diaryDate;

    private String representativePhotoUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PhotoResponse> photos;

    public static DiaryResponse from(Diary diary) {
        DiaryResponse response = new DiaryResponse();
        response.setId(diary.getId());
        response.setContent(diary.getContent());
        response.setEmotionIcon(diary.getEmotionIcon());
        response.setIsFavorited(diary.getIsFavorited());
        response.setStatus(diary.getStatus());
        response.setDiaryDate(diary.getDiaryDate());
        response.setRepresentativePhotoUrl(diary.getRepresentativePhotoUrl());
        response.setCreatedAt(diary.getCreatedAt());
        response.setUpdatedAt(diary.getUpdatedAt());
        if (diary.getDiaryPhotos() != null) {
            response.setPhotos(diary.getDiaryPhotos().stream()
                    .sorted(Comparator.comparingInt(p -> p.getSequence() != null ? p.getSequence() : 0)) // 순서대로 정렬
                    .map(PhotoResponse::from)
                    .collect(Collectors.toList()));
        } else {
            response.setPhotos(Collections.emptyList());
        }
        return response;
    }
}