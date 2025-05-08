package com.apply.diarypic.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiPhotoScoreRequestDto {
    private Long id; // 사진의 DB PK. AI 서버의 PhotoInput.id 와 타입 매칭 주의 (AI서버는 int or str)
    private String photoUrl;
    private String shootingDateTime; // 예: "2025-05-09T14:30:00" (ISO 8601)
    private String detailedAddress;
    private boolean isMandatory;
}