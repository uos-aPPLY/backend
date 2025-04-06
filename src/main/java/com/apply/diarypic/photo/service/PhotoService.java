package com.apply.diarypic.photo.service;

import com.apply.diarypic.global.s3.S3Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private final S3Uploader s3Uploader;

    public String upload(MultipartFile file) {
        try {
            return s3Uploader.upload(file, "photos");
        } catch (IOException e) {
            throw new RuntimeException("파일 업로드 실패", e);
        }
    }

    public List<String> uploadAll(List<MultipartFile> files) {
        // 기존 단일 업로드 재사용
        return files.stream()
                .map(this::upload)
                .collect(Collectors.toList());
    }

}
