package com.apply.diarypic.diary.controller;

// import com.apply.diarypic.diary.dto.DiaryAutoRequest; // 기존 DTO 대신 새로운 DTO 사용
import com.apply.diarypic.diary.dto.AiDiaryCreateRequest; // 새로 정의한 AI 일기 생성 요청 DTO
import com.apply.diarypic.diary.dto.DiaryRequest;
import com.apply.diarypic.diary.dto.DiaryResponse;
import com.apply.diarypic.diary.service.DiaryService;
import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid; // @Valid 추가
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @Operation(summary = "사용자가 직접 작성한 일기 생성")
    @PostMapping
    public ResponseEntity<DiaryResponse> createDiary(@CurrentUser UserPrincipal userPrincipal,
                                                     @Valid @RequestBody DiaryRequest diaryRequest) { // @Valid 추가
        // 이 부분은 AI와 직접적인 관련이 없으므로, DiaryRequest DTO가 사진 ID 목록을 가지고
        // DiaryService.createDiary 메소드가 이를 처리하는 기존 로직을 유지한다고 가정합니다.
        // 만약 이 부분도 최종 9장의 사진을 사용하는 것이라면 DiaryRequest DTO도 수정 필요.
        DiaryResponse response = diaryService.createDiary(diaryRequest, userPrincipal.getUserId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "최종 사진 및 사용자 설정을 통한 AI 자동 일기 생성")
    @PostMapping("/auto")
    public ResponseEntity<DiaryResponse> createAiDiary(@CurrentUser UserPrincipal userPrincipal,
                                                       @Valid @RequestBody AiDiaryCreateRequest aiDiaryCreateRequest) { // @Valid 및 DTO 변경
        // DiaryService에 새로운 메소드 또는 기존 createDiaryAuto를 수정하여 호출
        // 여기서는 이전 답변에서 제안한 createDiaryWithAiAssistance를 호출한다고 가정
        // 해당 메소드는 내부적으로 AiServerService를 호출하여 AI와 통신
        DiaryResponse response = diaryService.createDiaryWithAiAssistance(
                userPrincipal.getUserId(),
                aiDiaryCreateRequest.getUserSpeech(),
                aiDiaryCreateRequest.getFinalizedPhotos() // 이 리스트는 FinalizedPhotoPayload 타입
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{diaryId}")
    public ResponseEntity<Void> deleteDiary(@CurrentUser UserPrincipal userPrincipal,
                                            @PathVariable Long diaryId) {
        diaryService.deleteDiary(userPrincipal.getUserId(), diaryId);
        return ResponseEntity.noContent().build();
    }
}