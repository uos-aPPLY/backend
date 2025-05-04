package com.apply.diarypic.diary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DiaryRequest {
    private String title;
    private String content;
    private String emotionIcon;

    @Size(max=9)
    private List<Long> photoIds = new ArrayList<>();
}
