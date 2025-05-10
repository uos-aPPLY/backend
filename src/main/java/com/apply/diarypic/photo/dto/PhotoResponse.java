package com.apply.diarypic.photo.dto;

import com.apply.diarypic.photo.entity.DiaryPhoto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data; // @Data는 @Getter, @Setter, @ToString, @EqualsAndHashCode, @RequiredArgsConstructor를 포함합니다.
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data // @Getter, @Setter 등 포함
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
    // private Long userId; // DiaryPhoto 엔티티에 userId가 있으므로, 응답 DTO에 꼭 필요하지 않을 수 있음. 필요시 유지.

    // 제공해주신 from 메소드 시그니처 유지
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
                .sequence(diaryPhoto.getSequence()) // 이 시점에는 null일 가능성 높음
                .createdAt(diaryPhoto.getCreatedAt())
                // .userId(diaryPhoto.getUserId()) // userId 필요시 주석 해제
                .build();
    }
}