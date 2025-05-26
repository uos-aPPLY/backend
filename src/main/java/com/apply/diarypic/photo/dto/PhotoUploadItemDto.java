package com.apply.diarypic.photo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
public class PhotoUploadItemDto {
    private LocalDateTime shootingDateTime;
    private LocationDto location;
    private String countryName;
    private String adminAreaLevel1;
    private String locality;

    @Getter
    @Setter
    public static class LocationDto {
        private Double latitude;
        private Double longitude;
    }
}