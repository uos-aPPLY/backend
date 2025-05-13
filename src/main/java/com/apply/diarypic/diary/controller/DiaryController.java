package com.apply.diarypic.diary.controller;

import com.apply.diarypic.diary.dto.*;
import com.apply.diarypic.diary.service.DiaryService;
import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag; // Tag 어노테이션 추가
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Diary API", description = "일기 관련 API") // Swagger 태그 추가
@RestController
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @Operation(summary = "특정 일기 상세 조회 (활성 상태)")
    @GetMapping("/{diaryId}")
    public ResponseEntity<DiaryResponse> getDiaryById(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long diaryId) {
        DiaryResponse response = diaryService.getDiaryById(userPrincipal.getUserId(), diaryId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "사용자의 활성 일기 목록 조회 (페이징, 최신순)")
    @GetMapping
    public ResponseEntity<Page<DiaryResponse>> getUserDiaries(
            @CurrentUser UserPrincipal userPrincipal,
            @PageableDefault(size = 10, sort = "diaryDate", direction = Sort.Direction.DESC)
            @Parameter(hidden = true) Pageable pageable) {
        Page<DiaryResponse> response = diaryService.getDiariesByUser(userPrincipal.getUserId(), pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "사용자가 직접 작성한 일기 생성")
    @PostMapping
    public ResponseEntity<DiaryResponse> createDiary(@CurrentUser UserPrincipal userPrincipal,
                                                     @Valid @RequestBody DiaryRequest diaryRequest) {
        DiaryResponse response = diaryService.createDiary(diaryRequest, userPrincipal.getUserId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "AI 자동 일기 생성")
    @PostMapping("/auto")
    public ResponseEntity<DiaryResponse> createAiDiary(@CurrentUser UserPrincipal userPrincipal,
                                                       @Valid @RequestBody AiDiaryCreateRequest aiDiaryCreateRequest) {
        DiaryResponse response = diaryService.createDiaryWithAiAssistance(
                userPrincipal.getUserId(),
                aiDiaryCreateRequest
        );
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "일기 삭제 (휴지통으로 이동 - 소프트 삭제)")
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

    @Operation(summary = "일기 내용 검색 (최신순, 페이징, 두 글자 이상)")
    @GetMapping("/search")
    public ResponseEntity<Page<DiaryResponse>> searchDiaries(
            @CurrentUser UserPrincipal userPrincipal,
            @Parameter(description = "검색할 키워드 (두 글자 이상)", required = true)
            @RequestParam
            @Size(min = 2, message = "검색어는 두 글자 이상 입력해주세요.")
            String keyword,
            @PageableDefault(size = 10, sort = "diaryDate", direction = Sort.Direction.DESC)
            @Parameter(hidden = true) Pageable pageable) {

        Page<DiaryResponse> response = diaryService.searchDiariesByContent(userPrincipal.getUserId(), keyword, pageable);
        return ResponseEntity.ok(response);
    }
}