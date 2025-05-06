package com.apply.diarypic.photo.service;

import com.apply.diarypic.diary.entity.DiaryPhoto;
import com.apply.diarypic.global.geocoding.GeocodingService;
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
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성
public class PhotoService {

    private final S3Uploader s3Uploader;
    private final PhotoRepository photoRepository;
    private final GeocodingService geocodingService; // GeocodingService 주입

    @Transactional
    public String upload(MultipartFile file, Long userId) {
        try {
            // 1. S3에 업로드
            String url = s3Uploader.upload(file, "photos");

            // 2. EXIF 메타데이터 파싱
            LocalDateTime shootingDateTime = null;
            String location = null; // "위도,경도" 형식의 문자열
            String detailedAddress = null; // 변환된 상세 주소

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
                        // Geocoding API 호출하여 상세 주소 가져오기
                        detailedAddress = geocodingService.getAddressFromCoordinates(geo.getLatitude(), geo.getLongitude());
                    }
                }
            } catch (Exception e) {
                log.warn("EXIF 메타데이터 파싱 또는 주소 변환 실패: 파일명={}, 오류={}", file.getOriginalFilename(), e.getMessage());
            }

            // 3. DB에 저장
            DiaryPhoto diaryPhoto = DiaryPhoto.builder()
                    .photoUrl(url)
                    .userId(userId)
                    .shootingDateTime(shootingDateTime)
                    .location(location)
                    .detailedAddress(detailedAddress) // 상세 주소 추가
                    .createdAt(LocalDateTime.now())
                    .build();
            photoRepository.save(diaryPhoto);

            return url;
        } catch (Exception e) {
            // S3 업로드 자체의 실패 등 더 큰 범위의 예외 처리
            log.error("파일 업로드 또는 메타데이터 저장 실패: {}", e.getMessage(), e);
            throw new RuntimeException("파일 업로드 또는 메타데이터 저장 실패", e);
        }
    }

    public List<String> uploadAll(List<MultipartFile> files, Long userId) {
        return files.stream()
                .map(file -> upload(file, userId))
                .collect(Collectors.toList());
    }
}