package com.apply.diarypic.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateAlarmRequest {
    @NotNull
    private Boolean enabled;

    @NotNull
    private Integer hour;  // 0~23

    @NotNull
    private Integer minute; // 0~59
}
