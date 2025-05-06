package com.apply.diarypic.photo.service;

import com.apply.diarypic.diary.entity.DiaryPhoto;
import com.apply.diarypic.global.s3.S3Uploader;
import com.apply.diarypic.photo.repository.PhotoRepository;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.lang.GeoLocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final S3Uploader s3Uploader;
    private final PhotoRepository photoRepository;

    @Transactional
    public String upload(MultipartFile file, Long userId) {
        try {
            // 1. S3에 업로드
            String url = s3Uploader.upload(file, "photos");

            // 2. EXIF 메타데이터 파싱
            LocalDateTime shootingDateTime = null;
            String location = null;
            try (InputStream is = file.getInputStream()) {
                Metadata metadata = ImageMetadataReader.readMetadata(is);
                ExifSubIFDDirectory exifDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
                if (exifDir != null && exifDir.getDateOriginal() != null) {
                    shootingDateTime = exifDir.getDateOriginal()
                            .toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime();
                }
                GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
                if (gpsDir != null) {
                    GeoLocation geo = gpsDir.getGeoLocation();
                    if (geo != null && !geo.isZero()) {
                        location = geo.getLatitude() + "," + geo.getLongitude();
                    }
                }
            } catch (Exception e) {
                log.warn("EXIF 메타데이터 파싱 실패: 파일명={}, 오류={}", file.getOriginalFilename(), e.getMessage());
            }

            // 3. DB에 임시 저장 (diary 연결은 아직 하지 않음)
            DiaryPhoto diaryPhoto = DiaryPhoto.builder()
                    .photoUrl(url)
                    .userId(userId)
                    .shootingDateTime(shootingDateTime)
                    .location(location)
                    .createdAt(LocalDateTime.now())
                    .build();
            photoRepository.save(diaryPhoto);

            return url;
        } catch (Exception e) {
            throw new RuntimeException("파일 업로드 또는 메타데이터 저장 실패", e);
        }
    }

    public List<String> uploadAll(List<MultipartFile> files, Long userId) {
        return files.stream()
                .map(file -> upload(file, userId))
                .collect(Collectors.toList());
    }
}
