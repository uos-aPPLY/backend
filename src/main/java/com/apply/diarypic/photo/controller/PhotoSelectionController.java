package com.apply.diarypic.photo.controller;

import com.apply.diarypic.diary.entity.DiaryPhoto;
import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import com.apply.diarypic.photo.dto.FinalizePhotoSelectionRequest;
import com.apply.diarypic.photo.service.PhotoSelectionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/photos/selection")
@RequiredArgsConstructor
public class PhotoSelectionController {

    private final PhotoSelectionService photoSelectionService;

    @Operation(summary = "임시 업로드 사진 조회")
    @GetMapping("/temp")
    public ResponseEntity<List<DiaryPhoto>> getTemporaryPhotos(@CurrentUser UserPrincipal userPrincipal) {
        List<DiaryPhoto> tempPhotos = photoSelectionService.getTemporaryPhotos(userPrincipal.getUserId());
        return ResponseEntity.ok(tempPhotos);
    }

    @Operation(summary = "최종 사진 선택 확정 (최종 9장 구성)")
    @PostMapping("/finalize")
    public ResponseEntity<List<DiaryPhoto>> finalizePhotoSelection(
            @CurrentUser UserPrincipal userPrincipal,
            @Valid @RequestBody FinalizePhotoSelectionRequest request) {
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
