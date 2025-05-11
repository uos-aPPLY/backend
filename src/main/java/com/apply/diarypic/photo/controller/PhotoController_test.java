package com.apply.diarypic.photo.controller;

import com.apply.diarypic.global.security.CurrentUser;
import com.apply.diarypic.global.security.UserPrincipal;
import com.apply.diarypic.photo.service.PhotoService_test;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Photo", description = "swagger용 사진 업로드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/photos_test")
public class PhotoController_test {

    private final PhotoService_test photoService;

    @Operation(summary = "사진 여러 장 업로드 (S3 및 DB 임시 저장)")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<String>> uploadPhotos(
            @CurrentUser UserPrincipal userPrincipal,
            @Parameter(description = "업로드할 이미지 파일들", content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
            @RequestPart("files") List<MultipartFile> files) {

        List<String> uploadedUrls = photoService.uploadAll(files, userPrincipal.getUserId());
        return ResponseEntity.ok(uploadedUrls);
    }
}