package com.apply.diarypic.diary.controller;

import com.apply.diarypic.diary.dto.AiDiaryCreateRequest;
import com.apply.diarypic.diary.dto.DiaryRequest;
import com.apply.diarypic.diary.dto.DiaryResponse;
import com.apply.diarypic.diary.dto.FavoriteToggleRequest;
import com.apply.diarypic.diary.service.DiaryService;
import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
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
                                                     @Valid @RequestBody DiaryRequest diaryRequest) {
        // DiaryRequest에 diaryDate가 포함되어 DiaryService.createDiary로 전달됨
        DiaryResponse response = diaryService.createDiary(diaryRequest, userPrincipal.getUserId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "최종 사진 및 사용자 설정을 통한 AI 자동 일기 생성")
    @PostMapping("/auto")
    public ResponseEntity<DiaryResponse> createAiDiary(@CurrentUser UserPrincipal userPrincipal,
                                                       @Valid @RequestBody AiDiaryCreateRequest aiDiaryCreateRequest) {
        // AiDiaryCreateRequest에 diaryDate가 포함되어 DiaryService.createDiaryWithAiAssistance로 전달됨
        DiaryResponse response = diaryService.createDiaryWithAiAssistance(
                userPrincipal.getUserId(),
                aiDiaryCreateRequest.getDiaryDate(), // diaryDate 전달
                aiDiaryCreateRequest.getFinalizedPhotos()
        );
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "일기 좋아요(즐겨찾기) 상태 토글")
    @PatchMapping("/{diaryId}/favorite/toggle") // 토글 방식 엔드포인트
    public ResponseEntity<DiaryResponse> toggleDiaryFavorite(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long diaryId) {
        DiaryResponse response = diaryService.toggleDiaryFavorite(userPrincipal.getUserId(), diaryId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "일기 좋아요(즐겨찾기) 상태 설정")
    @PatchMapping("/{diaryId}/favorite") // 특정 상태로 설정하는 엔드포인트
    public ResponseEntity<DiaryResponse> setDiaryFavorite(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long diaryId,
            @Valid @RequestBody FavoriteToggleRequest request) {
        DiaryResponse response = diaryService.setDiaryFavorite(userPrincipal.getUserId(), diaryId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{diaryId}")
    public ResponseEntity<Void> deleteDiary(@CurrentUser UserPrincipal userPrincipal,
                                            @PathVariable Long diaryId) {
        diaryService.deleteDiary(userPrincipal.getUserId(), diaryId);
        return ResponseEntity.noContent().build();
    }
}