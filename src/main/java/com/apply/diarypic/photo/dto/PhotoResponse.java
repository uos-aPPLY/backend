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

    // 새로운 위치 필드
    private String countryName;
    private String adminAreaLevel1;
    private String locality;

    // detailedAddress 필드 제거
    // private String detailedAddress;

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
                .countryName(diaryPhoto.getCountryName()) // 새 필드 매핑
                .adminAreaLevel1(diaryPhoto.getAdminAreaLevel1()) // 새 필드 매핑
                .locality(diaryPhoto.getLocality()) // 새 필드 매핑
                .sequence(diaryPhoto.getSequence())
                .createdAt(diaryPhoto.getCreatedAt())
                .userId(diaryPhoto.getUserId())
                .build();
    }
}