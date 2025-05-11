package com.apply.diarypic.photo.controller;

import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import com.apply.diarypic.photo.dto.PhotoMetadata;
import com.apply.diarypic.photo.dto.PhotoResponse;
import com.apply.diarypic.photo.dto.PhotoUploadItemDto;
import com.apply.diarypic.photo.service.PhotoService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/photos")
public class PhotoUploadController {

    private final ObjectMapper objectMapper;
    private final PhotoService photoService;          // ★ 추가

    @PostMapping("/upload")
    public ResponseEntity<List<PhotoResponse>> uploadPhotos(
            @CurrentUser UserPrincipal user,
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("metadata") String metadataJson
    ) {
        List<PhotoUploadItemDto> metadataList;
        try {
            metadataList = objectMapper.readValue(
                    metadataJson,
                    new TypeReference<List<PhotoUploadItemDto>>() {});
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }

        if (files.size() != metadataList.size()) {
            return ResponseEntity.badRequest().body(null);
        }

        /* ★ 실제 저장 · S3 업로드 호출 */
        List<PhotoResponse> responses =
                photoService.uploadPhotosWithMetadata(files, metadataList, user.getUserId());

        return ResponseEntity.ok(responses);
    }
}
