package com.apply.diarypic.diary.dto;

import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.photo.dto.PhotoResponse;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class DiaryResponse {
    private Long id;
    private String title;
    private String content;
    private String emotionIcon;
    private Boolean isFavorited;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PhotoResponse> photos;

    public static DiaryResponse from(Diary diary) {
        DiaryResponse response = new DiaryResponse();
        response.setId(diary.getId());
        response.setTitle(diary.getTitle());
        response.setContent(diary.getContent());
        response.setEmotionIcon(diary.getEmotionIcon());
        response.setIsFavorited(diary.getIsFavorited());
        response.setStatus(diary.getStatus());
        response.setCreatedAt(diary.getCreatedAt());
        response.setUpdatedAt(diary.getUpdatedAt());
        if (diary.getDiaryPhotos() != null) {
            response.setPhotos(diary.getDiaryPhotos().stream()
                    .map(PhotoResponse::from)
                    .collect(Collectors.toList()));
        }
        return response;
    }
}
