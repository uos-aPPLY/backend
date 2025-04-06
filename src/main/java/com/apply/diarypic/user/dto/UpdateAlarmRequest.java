package com.apply.diarypic.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalTime;

@Getter
@Setter
public class UpdateAlarmRequest {
    @NotNull
    private Boolean enabled;
    @DateTimeFormat(pattern = "HH:mm:ss")
    private LocalTime alarmTime;
    private Boolean random;
}
