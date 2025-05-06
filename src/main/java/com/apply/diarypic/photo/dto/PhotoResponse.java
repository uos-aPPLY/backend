package com.apply.diarypic.photo.dto;

import com.apply.diarypic.diary.entity.DiaryPhoto;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Data
public class PhotoResponse {
    private Long id;
    private String photoUrl;
    private LocalDateTime shootingDateTime;
    private String location;
    private String detailedAddress;
    private Boolean isRecommended;
    private Integer sequence;
    private LocalDateTime createdAt;

    public static PhotoResponse from(DiaryPhoto diaryPhoto) {
        PhotoResponse response = new PhotoResponse();
        response.setId(diaryPhoto.getId());
        response.setPhotoUrl(diaryPhoto.getPhotoUrl());
        response.setShootingDateTime(diaryPhoto.getShootingDateTime());
        response.setLocation(diaryPhoto.getLocation());
        response.setDetailedAddress(diaryPhoto.getDetailedAddress());
        response.setIsRecommended(diaryPhoto.getIsRecommended());
        response.setSequence(diaryPhoto.getSequence());
        response.setCreatedAt(diaryPhoto.getCreatedAt());
        return response;
    }
}
