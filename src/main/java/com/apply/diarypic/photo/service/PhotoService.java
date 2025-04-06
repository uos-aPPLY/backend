package com.apply.diarypic.photo.service;

import com.apply.diarypic.diary.entity.DiaryPhoto;
import com.apply.diarypic.global.s3.S3Uploader;
import com.apply.diarypic.photo.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private final S3Uploader s3Uploader;
    private final PhotoRepository photoRepository;

    public String upload(MultipartFile file, Long userId) {
        try {
            String url = s3Uploader.upload(file, "photos");
            // 임시 저장용 DiaryPhoto 엔티티 생성 (diary 연결은 아직 하지 않음)
            DiaryPhoto diaryPhoto = DiaryPhoto.builder()
                    .photoUrl(url)
                    .userId(userId)
                    .createdAt(LocalDateTime.now())
                    .build();
            photoRepository.save(diaryPhoto);
            return url;
        } catch (IOException e) {
            throw new RuntimeException("파일 업로드 실패", e);
        }
    }

    public List<String> uploadAll(List<MultipartFile> files, Long userId) {
        // 단일 업로드 재사용 후 URL 목록 반환
        return files.stream()
                .map(file -> upload(file, userId))
                .collect(Collectors.toList());
    }
}
