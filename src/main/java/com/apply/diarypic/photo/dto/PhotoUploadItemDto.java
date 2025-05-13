package com.apply.diarypic.photo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class PhotoUploadItemDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LocationDto {
        private Double latitude;
        private Double longitude;
    }

    private LocationDto location;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime shootingDateTime;
}