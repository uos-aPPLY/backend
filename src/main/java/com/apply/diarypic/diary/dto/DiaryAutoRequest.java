package com.apply.diarypic.diary.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class DiaryAutoRequest {
    @NotEmpty(message = "최종 사진 ID 목록은 비어있을 수 없습니다.")
    private List<Long> photoIds;
}
