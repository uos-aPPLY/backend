package com.apply.diarypic.photo.controller;

import com.apply.diarypic.diary.entity.DiaryPhoto; // PhotoResponse 또는 DiaryPhoto 직접 반환 시 필요
import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import com.apply.diarypic.photo.dto.AiPhotoRecommendRequest; // AI 추천 요청 DTO
import com.apply.diarypic.photo.dto.AiPhotoRecommendResponse; // AI 추천 응답 DTO
import com.apply.diarypic.photo.dto.FinalizePhotoSelectionRequest;
// import com.apply.diarypic.photo.dto.PhotoResponse; // PhotoResponse를 사용한다면 import
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
@RequestMapping("/api/photos/selection") // 기존 경로 유지
@RequiredArgsConstructor
public class PhotoSelectionController {

    private final PhotoSelectionService photoSelectionService;
    private final PhotoRecommendationService photoRecommendationService; // AI 추천 서비스 주입

    @Operation(summary = "임시 업로드 사진 조회")
    @GetMapping("/temp")
    public ResponseEntity<List<DiaryPhoto>> getTemporaryPhotos(@CurrentUser UserPrincipal userPrincipal) {
        // 현재 이 메소드는 DiaryPhoto 엔티티를 직접 반환하고 있습니다.
        // 필요하다면 PhotoResponse DTO 등으로 변환하여 반환할 수 있습니다.
        List<DiaryPhoto> tempPhotos = photoSelectionService.getTemporaryPhotos(userPrincipal.getUserId());
        return ResponseEntity.ok(tempPhotos);
    }

    @Operation(summary = "AI에게 사진 추천 요청 (최대 9장 구성 위한)")
    @PostMapping("/ai-recommend") // 새로운 엔드포인트
    public ResponseEntity<AiPhotoRecommendResponse> getAiRecommendedPhotos(
            @CurrentUser UserPrincipal userPrincipal,
            @Valid @RequestBody AiPhotoRecommendRequest request) {

        // PhotoRecommendationService를 호출하여 AI 서버에 추천 요청
        List<Long> recommendedIds = photoRecommendationService.getRecommendedPhotosFromAI(
                userPrincipal.getUserId(),
                request.getUploadedPhotoIds(),
                request.getMandatoryPhotoIds()
        );

        // 응답 DTO 생성
        AiPhotoRecommendResponse response = new AiPhotoRecommendResponse(recommendedIds);
        return ResponseEntity.ok(response);

        // 만약 추천된 사진들의 상세 정보(URL 등)도 함께 내려주고 싶다면,
        // recommendedIds로 DB에서 DiaryPhoto 정보를 조회하여 AiPhotoRecommendResponse를 채울 수 있습니다.
        // 예:
        // List<DiaryPhoto> recommendedDiaryPhotos = photoRepository.findAllById(recommendedIds);
        // List<AiPhotoRecommendResponse.PhotoSimpleInfo> photoSimpleInfos = recommendedDiaryPhotos.stream()
        // .map(dp -> new AiPhotoRecommendResponse.PhotoSimpleInfo(dp.getId(), dp.getPhotoUrl()))
        // .collect(Collectors.toList());
        // AiPhotoRecommendResponse response = new AiPhotoRecommendResponse(photoSimpleInfos); // DTO 구조 변경 필요
        // return ResponseEntity.ok(response);
    }


    @Operation(summary = "최종 사진 선택 확정 (최종 9장 구성) - 사용자가 AI 추천 후 직접 선택/정렬 완료 시")
    @PostMapping("/finalize")
    public ResponseEntity<List<DiaryPhoto>> finalizePhotoSelection(
            @CurrentUser UserPrincipal userPrincipal,
            @Valid @RequestBody FinalizePhotoSelectionRequest request) {
        // 이 메소드는 사용자가 AI 추천을 참고하여 최종적으로 9장을 선택하고 순서까지 정렬한 후,
        // 그 결과를 서버에 "확정"하는 단계입니다.
        // request.getPhotoIds()는 사용자가 최종 선택한 사진 ID 목록 (순서대로)
        List<DiaryPhoto> finalPhotos = photoSelectionService.finalizePhotoSelection(userPrincipal.getUserId(), request.getPhotoIds());
        return ResponseEntity.ok(finalPhotos);
    }

    @Operation(summary = "임시 사진 개별 삭제")
    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> deletePhoto(@CurrentUser UserPrincipal userPrincipal,
                                            @PathVariable Long photoId) {
        photoSelectionService.deletePhoto(userPrincipal.getUserId(), photoId);
        return ResponseEntity.noContent().build();
    }
}