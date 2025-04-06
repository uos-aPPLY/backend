package com.apply.diarypic.diary.dto;

import lombok.Data;
import java.util.List;

@Data
public class DiaryRequest {
    private String title;
    private String content;
    private String emotionIcon;
    // 선택된 사진 목록 (최종 선택된 사진들의 정보를 포함)
    private List<PhotoDto> photos;
}
