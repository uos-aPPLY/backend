package com.apply.diarypic.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiDiaryGenerateRequestDto {
    private String user_speech; // AI 서버의 DiaryRequest.user_speech 필드명과 일치
    private List<ImageInfoDto> image_info; // AI 서버의 DiaryRequest.image_info 필드명과 일치
}