package com.apply.diarypic.photo.service;

import com.apply.diarypic.photo.entity.DiaryPhoto;
import com.apply.diarypic.global.s3.S3Uploader;
import com.apply.diarypic.photo.dto.PhotoResponse;
import com.apply.diarypic.photo.repository.PhotoRepository;
// import com.apply.diarypic.global.geocoding.GeocodingService; // GeocodingService 의존성 제거
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections; // Collections 임포트 (만약 uploadPhotosWithMetadata에서 사용한다면)
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoSelectionService {

    private final PhotoRepository photoRepository;
    private final S3Uploader s3Uploader;
    // private final GeocodingService geocodingService; // 주석 처리 또는 삭제

    // Presigned URL 관련 만료 시간 필드 제거

    @Transactional(readOnly = true)
    public List<PhotoResponse> getTemporaryPhotos(Long userId) {
        List<DiaryPhoto> tempPhotos = photoRepository.findByDiaryIsNullAndUserId(userId);
        return tempPhotos.stream()
                .map(PhotoResponse::from) // Public URL을 사용하는 from 메소드 호출
                .collect(Collectors.toList());
    }

    @Transactional
    public List<DiaryPhoto> finalizePhotoSelection(Long userId, List<Long> finalPhotoIds) {
        if (finalPhotoIds == null || finalPhotoIds.isEmpty()) {
            throw new IllegalArgumentException("최종 선택된 사진 ID 목록이 비어있습니다.");
        }
        if (finalPhotoIds.size() > 9) {
            throw new IllegalArgumentException("최종 선택 사진은 최대 9장까지 가능합니다.");
        }

        List<DiaryPhoto> userTempPhotos = photoRepository.findByDiaryIsNullAndUserId(userId);
        if (userTempPhotos.isEmpty() && !finalPhotoIds.isEmpty()) {
            log.error("사용자 ID {}: 임시 사진이 없는데 최종 선택 요청이 들어왔습니다. finalPhotoIds: {}", userId, finalPhotoIds);
            throw new EntityNotFoundException("최종 확정할 임시 사진을 찾을 수 없습니다. 서버 상태를 확인해주세요.");
        }

        List<DiaryPhoto> finalPhotos = new ArrayList<>();
        List<DiaryPhoto> photosToDelete = new ArrayList<>();

        for (DiaryPhoto tempPhoto : userTempPhotos) {
            if (finalPhotoIds.contains(tempPhoto.getId())) {
                finalPhotos.add(tempPhoto);
            } else {
                photosToDelete.add(tempPhoto);
            }
        }

        for (Long requestedId : finalPhotoIds) {
            if (finalPhotos.stream().noneMatch(p -> p.getId().equals(requestedId))) {
                log.error("사용자 ID {}: 요청된 최종 사진 ID {} 가 사용자의 임시 사진 목록에 없습니다.", userId, requestedId);
                throw new EntityNotFoundException("요청된 사진 ID " + requestedId + "를 찾을 수 없습니다. (서버 오류 또는 잘못된 요청)");
            }
        }

        for (int i = 0; i < finalPhotoIds.size(); i++) {
            Long photoId = finalPhotoIds.get(i);
            for (DiaryPhoto photo : finalPhotos) {
                if (photo.getId().equals(photoId)) {
                    photo.setSequence(i + 1);
                    // --- Geocoding 로직 제거 ---
                    // 이 단계에서는 DiaryPhoto에 이미 주소 정보가 있다고 가정
                    break;
                }
            }
        }

        photoRepository.saveAll(finalPhotos); // sequence 변경 사항 저장

        for (DiaryPhoto photoToDelete : photosToDelete) {
            try {
                if (StringUtils.hasText(photoToDelete.getPhotoUrl())) {
                    s3Uploader.deleteFileByUrl(photoToDelete.getPhotoUrl()); // Public URL 기반 삭제
                    log.info("S3에서 사진 삭제 성공 (URL: {})", photoToDelete.getPhotoUrl());
                }
                photoRepository.delete(photoToDelete);
                log.info("DB에서 사진 삭제 성공 (ID: {})", photoToDelete.getId());
            } catch (Exception e) {
                log.error("임시 사진 삭제 중 오류 발생 (Photo ID: {}): {}", photoToDelete.getId(), e.getMessage(), e);
            }
        }
        return finalPhotos;
    }

    @Transactional
    public void deletePhoto(Long userId, Long photoId) {
        DiaryPhoto photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new EntityNotFoundException("해당 사진이 존재하지 않습니다. ID: " + photoId));

        if (!photo.getUserId().equals(userId)) {
            throw new SecurityException("해당 사진에 대한 삭제 권한이 없습니다.");
        }
//        if (photo.getDiary() != null) {
//            throw new IllegalArgumentException("이미 일기에 등록된 사진은 이 경로로 삭제할 수 없습니다.");
//        }

        try {
            if (StringUtils.hasText(photo.getPhotoUrl())) {
                s3Uploader.deleteFileByUrl(photo.getPhotoUrl()); // Public URL 기반 삭제
                log.info("S3에서 사진 삭제 성공 (URL: {})", photo.getPhotoUrl());
            }
        } catch (Exception e) {
            log.error("S3 사진 삭제 실패 (Photo ID: {}): {}", photoId, e.getMessage(), e);
        }

        photoRepository.delete(photo);
        log.info("DB에서 사진 삭제 성공 (ID: {})", photoId);
    }
}