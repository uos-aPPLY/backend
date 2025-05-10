package com.apply.diarypic.photo.dto;

import com.apply.diarypic.photo.entity.DiaryPhoto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhotoResponse {
    private Long id;
    private String photoUrl;
    private LocalDateTime shootingDateTime;
    private String location;
    private String detailedAddress;
    private Integer sequence;
    private LocalDateTime createdAt;
    private Long userId;

    public static PhotoResponse from(DiaryPhoto diaryPhoto) {
        if (diaryPhoto == null) {
            return null;
        }
        return PhotoResponse.builder()
                .id(diaryPhoto.getId())
                .photoUrl(diaryPhoto.getPhotoUrl())
                .shootingDateTime(diaryPhoto.getShootingDateTime())
                .location(diaryPhoto.getLocation())
                .detailedAddress(diaryPhoto.getDetailedAddress())
                .sequence(diaryPhoto.getSequence())
                .createdAt(diaryPhoto.getCreatedAt())
                .userId(diaryPhoto.getUserId())
                .build();
    }
}