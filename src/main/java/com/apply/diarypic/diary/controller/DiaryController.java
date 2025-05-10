package com.apply.diarypic.diary.controller;

import com.apply.diarypic.diary.dto.AiDiaryCreateRequest;
import com.apply.diarypic.diary.dto.DiaryRequest;
import com.apply.diarypic.diary.dto.DiaryResponse;
import com.apply.diarypic.diary.dto.FavoriteToggleRequest;
import com.apply.diarypic.diary.dto.SetRepresentativePhotoRequest;
import com.apply.diarypic.diary.service.DiaryService;
import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter; // Parameter 임포트
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page; // Page 임포트
import org.springframework.data.domain.Pageable; // Pageable 임포트
import org.springframework.data.domain.Sort; // Sort 임포트
import org.springframework.data.web.PageableDefault; // PageableDefault 임포트
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @Operation(summary = "특정 일기 상세 조회")
    @GetMapping("/{diaryId}")
    public ResponseEntity<DiaryResponse> getDiaryById(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long diaryId) {
        DiaryResponse response = diaryService.getDiaryById(userPrincipal.getUserId(), diaryId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "사용자의 일기 목록 조회 (페이징, 최신순)")
    @GetMapping
    public ResponseEntity<Page<DiaryResponse>> getUserDiaries(
            @CurrentUser UserPrincipal userPrincipal,
            @PageableDefault(size = 10, sort = "diaryDate", direction = Sort.Direction.DESC) // 기본 페이징: 10개씩, diaryDate 최신순
            @Parameter(hidden = true) Pageable pageable) { // Swagger에서 pageable 파라미터 숨김 (필요시)
        Page<DiaryResponse> response = diaryService.getDiariesByUser(userPrincipal.getUserId(), pageable);
        return ResponseEntity.ok(response);
    }

    // ... (createDiary, createAiDiary, deleteDiary, toggleDiaryFavorite, setDiaryFavorite, setRepresentativePhoto 메소드는 이전과 동일) ...
    @Operation(summary = "사용자가 직접 작성한 일기 생성")
    @PostMapping
    public ResponseEntity<DiaryResponse> createDiary(@CurrentUser UserPrincipal userPrincipal,
                                                     @Valid @RequestBody DiaryRequest diaryRequest) {
        DiaryResponse response = diaryService.createDiary(diaryRequest, userPrincipal.getUserId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "최종 사진 및 사용자 설정을 통한 AI 자동 일기 생성")
    @PostMapping("/auto")
    public ResponseEntity<DiaryResponse> createAiDiary(@CurrentUser UserPrincipal userPrincipal,
                                                       @Valid @RequestBody AiDiaryCreateRequest aiDiaryCreateRequest) {
        DiaryResponse response = diaryService.createDiaryWithAiAssistance(
                userPrincipal.getUserId(),
                aiDiaryCreateRequest.getDiaryDate(),
                aiDiaryCreateRequest.getFinalizedPhotos()
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{diaryId}")
    public ResponseEntity<Void> deleteDiary(@CurrentUser UserPrincipal userPrincipal,
                                            @PathVariable Long diaryId) {
        diaryService.deleteDiary(userPrincipal.getUserId(), diaryId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "일기 좋아요(즐겨찾기) 상태 토글")
    @PatchMapping("/{diaryId}/favorite/toggle")
    public ResponseEntity<DiaryResponse> toggleDiaryFavorite(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long diaryId) {
        DiaryResponse response = diaryService.toggleDiaryFavorite(userPrincipal.getUserId(), diaryId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "일기 좋아요(즐겨찾기) 상태 설정")
    @PatchMapping("/{diaryId}/favorite")
    public ResponseEntity<DiaryResponse> setDiaryFavorite(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long diaryId,
            @Valid @RequestBody FavoriteToggleRequest request) {
        DiaryResponse response = diaryService.setDiaryFavorite(userPrincipal.getUserId(), diaryId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "일기 대표 사진 지정")
    @PatchMapping("/{diaryId}/representative-photo")
    public ResponseEntity<DiaryResponse> setRepresentativePhoto(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long diaryId,
            @Valid @RequestBody SetRepresentativePhotoRequest request) {
        DiaryResponse response = diaryService.setRepresentativePhoto(
                userPrincipal.getUserId(),
                diaryId,
                request.getPhotoId()
        );
        return ResponseEntity.ok(response);
    }
}