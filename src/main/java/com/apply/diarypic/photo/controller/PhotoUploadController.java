package com.apply.diarypic.photo.controller;

import com.apply.diarypic.photo.dto.PhotoMetadata;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/photos")
public class PhotoUploadController {

    private final ObjectMapper objectMapper;

    public PhotoUploadController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadPhotos(
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("metadata") String metadataJson  // <- JSON 문자열로 받기
    ) {
        try {
            // JSON 문자열 → List<PhotoMetadata> 변환
            List<PhotoMetadata> metadataList = objectMapper.readValue(
                    metadataJson,
                    new TypeReference<List<PhotoMetadata>>() {}
            );

            // 예시: 파일 수와 metadata 수 체크
            if (files.size() != metadataList.size()) {
                return ResponseEntity.badRequest().body("파일과 메타데이터 수 불일치");
            }

            // 파일과 메타데이터 처리
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                PhotoMetadata metadata = metadataList.get(i);

                System.out.println("파일명: " + file.getOriginalFilename());
                System.out.println("위도: " + (metadata.getLocation() != null ? metadata.getLocation().getLatitude() : "없음"));
                System.out.println("촬영시간: " + metadata.getShootingDateTime());
            }

            return ResponseEntity.ok("업로드 성공");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("처리 실패: " + e.getMessage());
        }
    }
}
