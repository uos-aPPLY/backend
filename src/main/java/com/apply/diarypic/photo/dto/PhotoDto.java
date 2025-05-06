package com.apply.diarypic.photo.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PhotoDto {
    private String photoUrl;
    private LocalDateTime shootingDateTime;
    private String location;
    private String detailedAddress;
    private Boolean isRecommended;
    private Integer sequence;
}
