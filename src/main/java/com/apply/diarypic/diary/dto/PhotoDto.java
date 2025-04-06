package com.apply.diarypic.diary.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class PhotoDto {
    private String photoUrl;
    private LocalDate shootingDate;
    private String location;
    private Boolean isRecommended;
    private Integer sequence;
}
