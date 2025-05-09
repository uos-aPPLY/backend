package com.apply.diarypic.photo.dto;

import lombok.Data;
import java.time.LocalDateTime; // LocalDate는 이 DTO에서 직접 사용하지 않으므로 제거 가능

@Data
public class PhotoDto {
    private String photoUrl;
    private LocalDateTime shootingDateTime;
    private String location;
    private String detailedAddress;
    private Integer sequence;
}