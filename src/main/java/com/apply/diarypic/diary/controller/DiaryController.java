package com.apply.diarypic.diary.controller;

import com.apply.diarypic.diary.dto.DiaryAutoRequest;
import com.apply.diarypic.diary.dto.DiaryRequest;
import com.apply.diarypic.diary.dto.DiaryResponse;
import com.apply.diarypic.diary.service.DiaryService;
import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
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
                                                     @RequestBody DiaryRequest diaryRequest) {
        DiaryResponse response = diaryService.createDiary(diaryRequest, userPrincipal.getUserId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "최종 사진을 통한 AI 자동 일기 생성")
    @PostMapping("/auto")
    public ResponseEntity<DiaryResponse> createDiaryAuto(@CurrentUser UserPrincipal userPrincipal,
                                                         @RequestBody DiaryAutoRequest diaryAutoRequest) {
        DiaryResponse response = diaryService.createDiaryAuto(diaryAutoRequest, userPrincipal.getUserId());
        return ResponseEntity.ok(response);
    }
}
