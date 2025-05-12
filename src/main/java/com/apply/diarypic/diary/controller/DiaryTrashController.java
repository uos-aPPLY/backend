package com.apply.diarypic.diary.controller;

import com.apply.diarypic.diary.dto.DiaryResponse;
import com.apply.diarypic.diary.service.DiaryService;
import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Diary Trash API", description = "일기 휴지통 관련 API")
@RestController
@RequestMapping("/api/diaries/trash")
@RequiredArgsConstructor
public class DiaryTrashController {

    private final DiaryService diaryService;

    @Operation(summary = "휴지통 일기 목록 조회 (최근 삭제된 순)")
    @GetMapping
    public ResponseEntity<Page<DiaryResponse>> getTrashedDiaries(
            @CurrentUser UserPrincipal userPrincipal,
            @PageableDefault(size = 10, sort = "deletedAt", direction = Sort.Direction.DESC)
            @Parameter(hidden = true) Pageable pageable) {
        Page<DiaryResponse> response = diaryService.getTrashedDiaries(userPrincipal.getUserId(), pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "휴지통에서 특정 일기 복원")
    @PatchMapping("/{diaryId}/restore")
    public ResponseEntity<DiaryResponse> restoreDiary(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long diaryId) {
        DiaryResponse response = diaryService.restoreDiary(userPrincipal.getUserId(), diaryId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "휴지통에서 특정 일기 영구 삭제")
    @DeleteMapping("/{diaryId}")
    public ResponseEntity<Void> permanentlyDeleteTrashedDiary(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long diaryId) {
        diaryService.permanentlyDeleteDiary(userPrincipal.getUserId(), diaryId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "사용자 휴지통 전체 비우기")
    @DeleteMapping("/all")
    public ResponseEntity<Void> emptyUserTrash(
            @CurrentUser UserPrincipal userPrincipal) {
        diaryService.emptyUserTrash(userPrincipal.getUserId());
        return ResponseEntity.noContent().build();
    }
}