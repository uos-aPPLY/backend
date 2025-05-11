package com.apply.diarypic.photo.service;

import com.apply.diarypic.photo.entity.DiaryPhoto;
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
import java.time.ZoneId; // ZoneId 임포트
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService_test {

    private final S3Uploader s3Uploader;
    private final PhotoRepository photoRepository;
    private final GeocodingService geocodingService;

    @Transactional
    public String upload(MultipartFile file, Long userId) {
        try {
            String s3Url = s3Uploader.upload(file, "photos");

            LocalDateTime shootingDateTime = null;
            String locationString = null; // 위도,경도 문자열
            String countryName = null;
            String adminAreaLevel1 = null;
            String locality = null;

            try (InputStream is = file.getInputStream()) {
                Metadata metadata = ImageMetadataReader.readMetadata(is);
                ExifSubIFDDirectory exifDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
                if (exifDir != null && exifDir.getDateOriginal() != null) {
                    shootingDateTime = exifDir.getDateOriginal().toInstant()
                            .atZone(ZoneId.systemDefault()) // 사용자 시스템 시간대 사용
                            .toLocalDateTime();
                }
                GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
                if (gpsDir != null) {
                    GeoLocation geo = gpsDir.getGeoLocation();
                    if (geo != null && !geo.isZero()) {
                        locationString = geo.getLatitude() + "," + geo.getLongitude();
                        GeocodingService.ParsedAddress parsedAddress = geocodingService.getParsedAddressFromCoordinates(geo.getLatitude(), geo.getLongitude());
                        if (parsedAddress != null) {
                            countryName = parsedAddress.getCountryName();
                            adminAreaLevel1 = parsedAddress.getAdminAreaLevel1();
                            locality = parsedAddress.getLocality();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("EXIF 메타데이터 파싱 또는 주소 변환 실패: 파일명={}, 오류={}", file.getOriginalFilename(), e.getMessage());
            }

            DiaryPhoto diaryPhoto = DiaryPhoto.builder()
                    .photoUrl(s3Url)
                    .userId(userId)
                    .shootingDateTime(shootingDateTime)
                    .location(locationString) // 원본 GPS 좌표 문자열 저장
                    .countryName(countryName)
                    .adminAreaLevel1(adminAreaLevel1)
                    .locality(locality)
                    // .detailedAddress() 필드 제거됨
                    .createdAt(LocalDateTime.now()) // 명시적 설정
                    .build();
            photoRepository.save(diaryPhoto);

            return s3Url;
        } catch (Exception e) {
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