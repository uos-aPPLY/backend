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
    private String location; // 위도,경도 문자열 (원본 GPS)

    private String countryName;
    private String adminAreaLevel1;
    private String locality;
    private Integer sequence; // 이 단계에서는 sequence가 없을 수 있음
    private LocalDateTime createdAt;

    public static PhotoResponse from(DiaryPhoto diaryPhoto) {
        if (diaryPhoto == null) {
            return null;
        }
        return PhotoResponse.builder()
                .id(diaryPhoto.getId())
                .photoUrl(diaryPhoto.getPhotoUrl())
                .shootingDateTime(diaryPhoto.getShootingDateTime())
                .location(diaryPhoto.getLocation())
                .countryName(diaryPhoto.getCountryName())
                .adminAreaLevel1(diaryPhoto.getAdminAreaLevel1())
                .locality(diaryPhoto.getLocality())
                .sequence(diaryPhoto.getSequence()) // 이 시점에는 null
                .createdAt(diaryPhoto.getCreatedAt())
                .build();
    }
}