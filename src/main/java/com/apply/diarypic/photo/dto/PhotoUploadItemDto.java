package com.apply.diarypic.photo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat; // ISO 날짜/시간 파싱

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class PhotoUploadItemDto {

    // 내부 Location DTO 정의
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LocationDto {
        private Double latitude;
        private Double longitude;
    }

    private LocationDto location; // 중첩된 객체로 받음

    // 클라이언트가 UTC (ISO 8601 'Z' 접미사)로 보낼 경우,
    // Spring은 기본적으로 LocalDateTime으로 변환할 때 시스템 기본 시간대를 가정할 수 있습니다.
    // 만약 UTC로 정확히 해석하고 싶다면, Instant로 받거나 문자열로 받아 수동 파싱하는 것이 더 안전할 수 있습니다.
    // 또는 Jackson 설정을 통해 UTC를 기본으로 다루도록 할 수 있습니다.
    // 여기서는 @DateTimeFormat을 사용하여 Spring의 변환 기능을 활용합니다.
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime shootingDateTime;
}