package com.apply.diarypic.photo.service;

import com.apply.diarypic.photo.dto.PhotoResponse;
import com.apply.diarypic.photo.dto.PhotoUploadItemDto;
import com.apply.diarypic.photo.entity.DiaryPhoto;
// import com.apply.diarypic.global.geocoding.GeocodingService; // GeocodingService 제거
import com.apply.diarypic.global.s3.S3Uploader;
import com.apply.diarypic.photo.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value; // 추가
import org.springframework.scheduling.annotation.Async; // @Async 추가
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections; // 추가
import java.util.List;
import java.util.concurrent.CompletableFuture; // 추가 (비동기 결과 처리용)

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final S3Uploader s3Uploader;
    private final PhotoRepository photoRepository;

    /**
     * 여러 사진 파일과 각 파일의 메타데이터를 받아 업로드하고 DB에 저장합니다.
     * 각 사진 처리는 비동기로 수행될 수 있도록 변경 고려.
     */
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
                responses.add(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("사진 처리 비동기 작업 중 InterruptedException 발생 (userId: {}): {}", userId, e.getMessage(), e);
            } catch (Exception e) {
                log.error("사진 처리 비동기 작업 중 Exception 발생 (userId: {}): {}", userId, e.getMessage(), e);
            }
        }

        responses.removeIf(java.util.Objects::isNull);

        return responses;
    }

    @Async
    @Transactional
    public CompletableFuture<PhotoResponse> processAndSavePhotoAsync(MultipartFile file, PhotoUploadItemDto metadataItem, Long userId) {
        String s3Url = null;
        try {
            s3Url = s3Uploader.upload(file, "photos/" + userId);

            LocalDateTime shootingDateTime = metadataItem.getShootingDateTime();
            String locationString = null;
            String countryName = null;
            String adminAreaLevel1 = null;
            String locality = null;

            PhotoUploadItemDto.LocationDto locationDto = metadataItem.getLocation();
            if (locationDto != null && locationDto.getLatitude() != null && locationDto.getLongitude() != null) {
                double latitude = locationDto.getLatitude();
                double longitude = locationDto.getLongitude();
                locationString = latitude + "," + longitude;

                countryName = metadataItem.getCountryName();
                adminAreaLevel1 = metadataItem.getAdminAreaLevel1();
                locality = metadataItem.getLocality();

                if (countryName == null && adminAreaLevel1 == null && locality == null) {
                    log.warn("userId: {}, 파일: {}, locationDto는 있으나 파싱된 주소 정보가 없습니다.", userId, file.getOriginalFilename());
                }

            } else if (metadataItem.getLocation() != null) {
                log.warn("userId: {}, 파일: {}, location 객체는 있으나 위도 또는 경도 값이 null입니다.", userId, file.getOriginalFilename());
            }


            DiaryPhoto diaryPhoto = DiaryPhoto.builder()
                    .photoUrl(s3Url)
                    .userId(userId)
                    .shootingDateTime(shootingDateTime)
                    .location(locationString)
                    .countryName(countryName)
                    .adminAreaLevel1(adminAreaLevel1)
                    .locality(locality)
                    .build();

            DiaryPhoto savedDiaryPhoto = photoRepository.save(diaryPhoto);
            log.info("비동기 처리: userId: {}, 파일: {} 업로드 및 DB 저장 성공. Photo ID: {}", userId, file.getOriginalFilename(), savedDiaryPhoto.getId());

            return CompletableFuture.completedFuture(PhotoResponse.from(savedDiaryPhoto));

        } catch (IOException e) {
            log.error("비동기 처리: userId: {}, 파일: {} 업로드 중 IO 오류 발생: {}", userId, file.getOriginalFilename(), e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("비동기 처리: userId: {}, 파일: {} 업로드 중 심각한 오류 발생: {}", userId, file.getOriginalFilename(), e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        }
    }
}