package com.apply.diarypic.diary.dto;

import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.photo.dto.PhotoResponse;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiaryResponse {
    private Long id;
    private String content;
    private String emotionIcon;
    private Boolean isFavorited;
    private String status;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate diaryDate;

    private String representativePhotoUrl;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime deletedAt;

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
        response.setDeletedAt(diary.getDeletedAt());

        if (diary.getDiaryPhotos() != null) {
            response.setPhotos(diary.getDiaryPhotos().stream()
                    .sorted(Comparator.comparingInt(p -> p.getSequence() != null ? p.getSequence() : Integer.MAX_VALUE))
                    .map(PhotoResponse::from)
                    .collect(Collectors.toList()));
        } else {
            response.setPhotos(Collections.emptyList());
        }

        // status 필드를 deletedAt 값에 따라 동적으로 설정 (선택적)
        if (diary.getDeletedAt() != null) {
            response.setStatus("휴지통");
        }
        return response;
    }
}