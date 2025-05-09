package com.apply.diarypic.photo.controller;

import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import com.apply.diarypic.photo.dto.AiPhotoRecommendRequest;  // AI 추천 요청 DTO
import com.apply.diarypic.photo.dto.AiPhotoRecommendResponse; // AI 추천 응답 DTO
import com.apply.diarypic.photo.dto.FinalizePhotoSelectionRequest;
import com.apply.diarypic.photo.dto.PhotoResponse;
import com.apply.diarypic.photo.service.PhotoRecommendationService;
import com.apply.diarypic.photo.service.PhotoSelectionService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/photos/selection")
@RequiredArgsConstructor
public class PhotoSelectionController {

    private final PhotoSelectionService photoSelectionService;
    private final PhotoRecommendationService photoRecommendationService; // 주입

    @Operation(summary = "임시 업로드 사진 조회")
    @GetMapping("/temp")
    public ResponseEntity<List<PhotoResponse>> getTemporaryPhotos(@CurrentUser UserPrincipal userPrincipal) {
        List<PhotoResponse> tempPhotos = photoSelectionService.getTemporaryPhotos(userPrincipal.getUserId());
        return ResponseEntity.ok(tempPhotos);
    }

    @Operation(summary = "AI에게 사진 추천 요청 (최대 9장 구성 위한)")
    @PostMapping("/ai-recommend") // 복구된 엔드포인트
    public ResponseEntity<AiPhotoRecommendResponse> getAiRecommendedPhotos(
            @CurrentUser UserPrincipal userPrincipal,
            @Valid @RequestBody AiPhotoRecommendRequest request) {

        List<Long> recommendedIds = photoRecommendationService.getRecommendedPhotosFromAI(
                userPrincipal.getUserId(),
                request.getUploadedPhotoIds(),
                request.getMandatoryPhotoIds()
        );
        return ResponseEntity.ok(new AiPhotoRecommendResponse(recommendedIds));
    }

    @Operation(summary = "최종 사진 선택 확정 (최종 9장 구성)")
    @PostMapping("/finalize")
    public ResponseEntity<List<PhotoResponse>> finalizePhotoSelection(
            @CurrentUser UserPrincipal userPrincipal,
            @Valid @RequestBody FinalizePhotoSelectionRequest request) {
        // PhotoSelectionService.finalizePhotoSelection이 List<DiaryPhoto>를 반환하면 DTO로 변환
        List<PhotoResponse> finalPhotoResponses = photoSelectionService.finalizePhotoSelection(userPrincipal.getUserId(), request.getPhotoIds())
                .stream()
                .map(PhotoResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(finalPhotoResponses);
    }

    @Operation(summary = "임시 사진 개별 삭제")
    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> deletePhoto(@CurrentUser UserPrincipal userPrincipal,
                                            @PathVariable Long photoId) {
        photoSelectionService.deletePhoto(userPrincipal.getUserId(), photoId);
        return ResponseEntity.noContent().build();
    }
}