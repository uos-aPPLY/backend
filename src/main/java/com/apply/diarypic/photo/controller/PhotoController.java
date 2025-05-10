package com.apply.diarypic.photo.controller;

import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import com.apply.diarypic.photo.dto.PhotoResponse;
import com.apply.diarypic.photo.dto.PhotoUploadItemDto; // 수정된 DTO 사용
import com.apply.diarypic.photo.service.PhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Photo", description = "사진 업로드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/photos")
@Slf4j
public class PhotoController {

    private final PhotoService photoService;

    @Operation(summary = "사진 여러 장 업로드 (S3 및 DB 임시 저장, 프론트 제공 메타데이터 사용)")
    @PostMapping(value = "/upload", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<List<PhotoResponse>> uploadPhotos(
            @CurrentUser UserPrincipal userPrincipal,
            @Parameter(description = "업로드할 이미지 파일들 (files 파트)")
            @RequestPart("files") List<MultipartFile> files,
            @Parameter(description = "각 파일에 대한 메타데이터 배열 (JSON 형식, metadata 파트). " +
                    "files 파트의 파일 순서와 일치해야 함.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "array", implementation = PhotoUploadItemDto.class))
            )
            @RequestPart("metadata") @Valid List<PhotoUploadItemDto> metadataList) {

        if (userPrincipal == null) {
            log.warn("[PhotoController] UserPrincipal is null. Unauthorized access attempt.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (files.isEmpty()) {
            log.warn("[PhotoController] No files uploaded by user {}.", userPrincipal.getUserId());
            return ResponseEntity.badRequest().build();
        }

        if (files.size() != metadataList.size()) {
            log.warn("[PhotoController] Mismatch between number of files ({}) and metadata entries ({}) for user {}.",
                    files.size(), metadataList.size(), userPrincipal.getUserId());
            return ResponseEntity.badRequest().build();
        }

        log.info("[PhotoController] uploadPhotos 호출 - userId: {}, 파일 개수: {}, 메타데이터 개수: {}",
                userPrincipal.getUserId(), files.size(), metadataList.size());

        List<PhotoResponse> uploadedPhotoResponses = photoService.uploadPhotosWithMetadata(
                files,
                metadataList,
                userPrincipal.getUserId()
        );

        if (uploadedPhotoResponses.isEmpty() && !files.isEmpty()) {
            log.error("[PhotoController] All photo uploads failed for user {}.", userPrincipal.getUserId());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        log.info("[PhotoController] uploadPhotos 완료 - userId: {}, 업로드된 사진 개수: {}",
                userPrincipal.getUserId(), uploadedPhotoResponses.size());
        return ResponseEntity.ok(uploadedPhotoResponses);
    }
}