package com.apply.diarypic.diary.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiaryStatusResponse {
    private String date;    // "YYYY-MM-DD"
    private String status;  // "none", "generating", "exists"
    private Long diaryId;   // status가 "exists" 또는 "generating"일 때의 일기 ID
}