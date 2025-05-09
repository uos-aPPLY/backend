package com.apply.diarypic.photo.controller;

import com.apply.diarypic.diary.entity.DiaryPhoto;
import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import com.apply.diarypic.photo.dto.FinalizePhotoSelectionRequest;
import com.apply.diarypic.photo.dto.PhotoResponse;
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

    @Operation(summary = "임시 업로드 사진 조회")
    @GetMapping("/temp")
    public ResponseEntity<List<PhotoResponse>> getTemporaryPhotos(@CurrentUser UserPrincipal userPrincipal) { // 반환 타입 DTO로 변경
        List<PhotoResponse> tempPhotos = photoSelectionService.getTemporaryPhotos(userPrincipal.getUserId());
        return ResponseEntity.ok(tempPhotos);
    }

    @Operation(summary = "최종 사진 선택 확정 (최종 9장 또는 10장 구성 - 코드 확인 필요)")
    @PostMapping("/finalize")
    public ResponseEntity<List<PhotoResponse>> finalizePhotoSelection( // 반환 타입을 List<PhotoResponse>로 변경
                                                                       @CurrentUser UserPrincipal userPrincipal,
                                                                       @Valid @RequestBody FinalizePhotoSelectionRequest request) {
        List<DiaryPhoto> finalDiaryPhotos = photoSelectionService.finalizePhotoSelection(userPrincipal.getUserId(), request.getPhotoIds());
        // DiaryPhoto를 PhotoResponse DTO로 변환하여 반환
        List<PhotoResponse> finalPhotoResponses = finalDiaryPhotos.stream()
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