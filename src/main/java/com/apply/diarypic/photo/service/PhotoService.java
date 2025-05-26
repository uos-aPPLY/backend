package com.apply.diarypic.photo.service;

import com.apply.diarypic.photo.dto.PhotoResponse;
import com.apply.diarypic.photo.dto.PhotoUploadItemDto;
import com.apply.diarypic.photo.entity.DiaryPhoto;
import com.apply.diarypic.global.s3.S3Uploader;
import com.apply.diarypic.photo.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async; // @Async 임포트
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

    @Async("photoUploadTaskExecutor")
    @Transactional
    public CompletableFuture<PhotoResponse> processAndSavePhotoAsync(MultipartFile file,
                                                                     PhotoUploadItemDto metadataItem,
                                                                     Long userId) {

        String s3Url = null;
        String originalFilename = file.getOriginalFilename(); // 로그용
        try {
            s3Url = s3Uploader.upload(file, "photos/" + userId);

            LocalDateTime shootingDateTime = metadataItem.getShootingDateTime();
            String locationString = null;
            String countryName = metadataItem.getCountryName();
            String adminAreaLevel1 = metadataItem.getAdminAreaLevel1();
            String locality = metadataItem.getLocality();

            PhotoUploadItemDto.LocationDto locationDto = metadataItem.getLocation();
            if (locationDto != null && locationDto.getLatitude() != null && locationDto.getLongitude() != null) {
                locationString = locationDto.getLatitude() + "," + locationDto.getLongitude();
                if (countryName == null && adminAreaLevel1 == null && locality == null) {
                    log.warn("userId: {}, 파일: {}, locationDto는 있으나 파싱된 주소 정보가 DTO에 없습니다.", userId, originalFilename);
                }
            } else if (metadataItem.getLocation() != null) {
                log.warn("userId: {}, 파일: {}, location 객체는 있으나 위도 또는 경도 값이 null입니다.", userId, originalFilename);
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
            log.info("비동기 처리: userId: {}, 파일: {} 업로드 및 DB 저장 성공. Photo ID: {}", userId, originalFilename, savedDiaryPhoto.getId());

            return CompletableFuture.completedFuture(PhotoResponse.from(savedDiaryPhoto));

        } catch (IOException e) {
            log.error("비동기 처리 (IO 오류): userId: {}, 파일: {}. 오류: {}", userId, originalFilename, e.getMessage(), e);
            return CompletableFuture.completedFuture(null); // 실패 시 null 반환
        } catch (Exception e) {
            log.error("비동기 처리 (일반 오류): userId: {}, 파일: {}. 오류: {}", userId, originalFilename, e.getMessage(), e);
            return CompletableFuture.completedFuture(null); // 실패 시 null 반환
        }
    }
}