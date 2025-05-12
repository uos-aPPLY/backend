package com.apply.diarypic.diary.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DiaryManualUpdateRequest {

    @Size(max = 10000, message = "일기 내용은 최대 10000자까지 입력 가능합니다.") // 예시 제약조건, 실제 Diary 엔티티와 맞추세요.
    private String content; // 수정할 일기 내용 (선택적)

    @Size(max = 50, message = "이모티콘 아이콘 이름은 최대 50자까지 가능합니다.") // 예시 제약조건
    private String emotionIcon; // 수정할 이모티콘 아이콘 (선택적)
}