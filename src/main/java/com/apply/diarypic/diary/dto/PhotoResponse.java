package com.apply.diarypic.diary.dto;

import com.apply.diarypic.diary.entity.DiaryPhoto;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Data
public class PhotoResponse {
    private Long id;
    private String photoUrl;
    private LocalDate shootingDate;
    private String location;
    private Boolean isRecommended;
    private Integer sequence;
    private LocalDateTime createdAt;

    public static PhotoResponse from(DiaryPhoto diaryPhoto) {
        PhotoResponse response = new PhotoResponse();
        response.setId(diaryPhoto.getId());
        response.setPhotoUrl(diaryPhoto.getPhotoUrl());
        response.setShootingDate(diaryPhoto.getShootingDate());
        response.setLocation(diaryPhoto.getLocation());
        response.setIsRecommended(diaryPhoto.getIsRecommended());
        response.setSequence(diaryPhoto.getSequence());
        response.setCreatedAt(diaryPhoto.getCreatedAt());
        return response;
    }
}
