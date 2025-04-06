package com.apply.diarypic.photo.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class FinalizePhotoSelectionRequest {
    @NotEmpty(message = "최종 선택할 사진 ID 목록이 비어있을 수 없습니다.")
    private List<Long> photoIds;
}
