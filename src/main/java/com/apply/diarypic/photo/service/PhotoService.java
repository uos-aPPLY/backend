package com.apply.diarypic.photo.service;

import com.apply.diarypic.photo.dto.PhotoResponse;
import com.apply.diarypic.photo.dto.PhotoUploadItemDto;
import com.apply.diarypic.photo.entity.DiaryPhoto;
import com.apply.diarypic.global.geocoding.GeocodingService; // GeocodingService 임포트
import com.apply.diarypic.global.s3.S3Uploader;
import com.apply.diarypic.photo.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final S3Uploader s3Uploader;
    private final PhotoRepository photoRepository;
    private final GeocodingService geocodingService; // GeocodingService 주입

    @Transactional
    public List<PhotoResponse> uploadPhotosWithMetadata(List<MultipartFile> files,
                                                        List<PhotoUploadItemDto> metadataList,
                                                        Long userId) {
        if (files == null || metadataList == null || files.size() != metadataList.size()) {
            log.warn("업로드 파일 수와 메타데이터 수가 일치하지 않거나 null입니다. files: {}, metadataList: {}",
                    files != null ? files.size() : "null",
                    metadataList != null ? metadataList.size() : "null");
            return Collections.emptyList();
        }

        List<PhotoResponse> responses = new ArrayList<>();
        List<CompletableFuture<PhotoResponse>> futures = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            PhotoUploadItemDto metadataItem = metadataList.get(i);
            futures.add(processAndSavePhotoAsync(file, metadataItem, userId));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (CompletableFuture<PhotoResponse> future : futures) {
            try {
                PhotoResponse response = future.get();
                if (response != null) {
                    responses.add(response);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("사진 처리 비동기 작업 대기 중 InterruptedException (userId: {}): {}", userId, e.getMessage(), e);
            } catch (Exception e) {
                log.error("사진 처리 비동기 작업 결과 가져오는 중 Exception (userId: {}): {}", userId, e.getMessage(), e);
            }
        }

        log.info("총 {}개의 사진 중 {}개 업로드 및 처리 완료 (userId: {})", files.size(), responses.size(), userId);
        return responses;
    }

    @Async("photoUploadTaskExecutor") // 커스텀 스레드 풀 사용
    @Transactional
    public CompletableFuture<PhotoResponse> processAndSavePhotoAsync(MultipartFile file,
                                                                     PhotoUploadItemDto metadataItem,
                                                                     Long userId) {
        String s3Url = null;
        String originalFilename = file.getOriginalFilename();
        try {
            s3Url = s3Uploader.upload(file, "photos/" + userId); // Public URL 반환

            LocalDateTime shootingDateTime = metadataItem.getShootingDateTime();
            String locationString = null;
            String countryName = null; // Geocoding으로 채울 변수
            String adminAreaLevel1 = null; // Geocoding으로 채울 변수
            String locality = null; // Geocoding으로 채울 변수

            PhotoUploadItemDto.LocationDto locationDto = metadataItem.getLocation();
            if (locationDto != null && locationDto.getLatitude() != null && locationDto.getLongitude() != null) {
                double latitude = locationDto.getLatitude();
                double longitude = locationDto.getLongitude();
                locationString = latitude + "," + longitude;

                // --- Geocoding 수행 ---
                try {
                    GeocodingService.ParsedAddress parsedAddress = geocodingService.getParsedAddressFromCoordinates(latitude, longitude);
                    if (parsedAddress != null) {
                        countryName = parsedAddress.getCountryName();
                        adminAreaLevel1 = parsedAddress.getAdminAreaLevel1();
                        locality = parsedAddress.getLocality();
                        log.info("Photo (file: {}): Geocoding 완료 - {}, {}, {}", originalFilename, countryName, adminAreaLevel1, locality);
                    } else {
                        log.warn("Photo (file: {}): Geocoding 결과가 null입니다. 위경도: {},{}", originalFilename, latitude, longitude);
                    }
                } catch (Exception e) {
                    log.warn("Photo (file: {}): Geocoding 중 오류 발생: {}. 위경도: {},{}", originalFilename, e.getMessage(), latitude, longitude);
                    // Geocoding 실패 시에도 나머지 정보는 저장될 수 있도록 예외를 다시 던지지 않음 (정책에 따라 변경 가능)
                }
                // --- Geocoding 종료 ---

            } else if (metadataItem.getLocation() != null) {
                log.warn("userId: {}, 파일: {}, location 객체는 있으나 위도 또는 경도 값이 null입니다.", userId, originalFilename);
            } else {
                log.info("userId: {}, 파일: {}, 위치 정보(위경도)가 없습니다. Geocoding을 수행하지 않습니다.", userId, originalFilename);
            }


            DiaryPhoto diaryPhoto = DiaryPhoto.builder()
                    .photoUrl(s3Url)
                    .userId(userId)
                    .shootingDateTime(shootingDateTime)
                    .location(locationString)
                    .countryName(countryName) // Geocoding 결과 반영
                    .adminAreaLevel1(adminAreaLevel1) // Geocoding 결과 반영
                    .locality(locality) // Geocoding 결과 반영
                    .build();

            DiaryPhoto savedDiaryPhoto = photoRepository.save(diaryPhoto);
            log.info("비동기 처리: userId: {}, 파일: {} 업로드 및 DB 저장 성공. Photo ID: {}", userId, originalFilename, savedDiaryPhoto.getId());

            return CompletableFuture.completedFuture(PhotoResponse.from(savedDiaryPhoto));

        } catch (IOException e) {
            log.error("비동기 처리 (IO 오류): userId: {}, 파일: {}. 오류: {}", userId, originalFilename, e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("비동기 처리 (일반 오류): userId: {}, 파일: {}. 오류: {}", userId, originalFilename, e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        }
    }
}