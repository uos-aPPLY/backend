package com.apply.diarypic.photo.controller;

import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import com.apply.diarypic.photo.dto.PhotoResponse;
import com.apply.diarypic.photo.dto.PhotoUploadItemDto; // 수정된 DTO 사용
import com.apply.diarypic.photo.service.PhotoService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "Photo", description = "사진 업로드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/photoss")
@Slf4j
public class PhotoController {

    private final PhotoService photoService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "사진 여러 장 업로드 (S3 및 DB 임시 저장, 프론트 제공 메타데이터 사용)")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<PhotoResponse>> uploadPhotos(
            @CurrentUser UserPrincipal user,          // 인증 주체
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("metadata") String metadataJson   // ← 문자열로 받음
    ) {
        if (files.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        /* 1. JSON → DTO 리스트 */
        List<PhotoUploadItemDto> metadataList;
        try {
            metadataList = new ObjectMapper().readValue(
                    metadataJson,
                    new TypeReference<List<PhotoUploadItemDto>>() {}
            );
        } catch (Exception e) {
            log.error("metadata 파싱 실패: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }

        /* 2. 개수 일치 확인 */
        if (files.size() != metadataList.size()) {
            return ResponseEntity.badRequest().build();
        }

        /* 3. 서비스 호출 */
        List<PhotoResponse> result = photoService.uploadPhotosWithMetadata(
                files, metadataList, user.getUserId());

        return ResponseEntity.ok(result);
    }


}