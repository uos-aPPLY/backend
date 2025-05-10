package com.apply.diarypic.photo.service;

import com.apply.diarypic.diary.entity.DiaryPhoto;
import com.apply.diarypic.global.s3.S3Uploader;
import com.apply.diarypic.photo.dto.PhotoResponse;
import com.apply.diarypic.photo.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoSelectionService {

    private final PhotoRepository photoRepository;
    private final S3Uploader s3Uploader;

    @Transactional(readOnly = true)
    public List<PhotoResponse> getTemporaryPhotos(Long userId) {
        List<DiaryPhoto> tempPhotos = photoRepository.findByDiaryIsNullAndUserId(userId);
        return tempPhotos.stream()
                .map(PhotoResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<DiaryPhoto> finalizePhotoSelection(Long userId, List<Long> finalPhotoIds) {
        List<DiaryPhoto> tempPhotos = photoRepository.findByDiaryIsNullAndUserId(userId);
        if (tempPhotos.isEmpty()) {
            throw new IllegalArgumentException("임시 업로드된 사진이 없습니다.");
        }

        List<DiaryPhoto> finalPhotos = tempPhotos.stream()
                .filter(photo -> finalPhotoIds.contains(photo.getId()))
                .collect(Collectors.toList());

        if (finalPhotos.size() > 9) {
            throw new IllegalArgumentException("최종 선택 사진은 최대 9장까지 가능합니다.");
        }

        List<DiaryPhoto> photosToDelete = tempPhotos.stream()
                .filter(photo -> !finalPhotoIds.contains(photo.getId()))
                .collect(Collectors.toList());

        for (int i = 0; i < finalPhotoIds.size(); i++) {
            Long photoId = finalPhotoIds.get(i);
            for (DiaryPhoto photo : finalPhotos) {
                if (photo.getId().equals(photoId)) {
                    photo.setSequence(i + 1);
                    break;
                }
            }
        }
        // photoRepository.saveAll(finalPhotos); // 변경된 finalPhotos 저장 (JPA Dirty Checking으로 자동 업데이트될 수 있지만 명시적 save 권장)

        for (DiaryPhoto photo : photosToDelete) {
            // S3 삭제 로직 임시 주석 처리 시작
            /*
            try {
                s3Uploader.delete(photo.getPhotoUrl());
                log.info("S3에서 사진 삭제 성공: {}", photo.getPhotoUrl());
            } catch (Exception e) {
                log.error("S3 사진 삭제 실패 (임시로 DB만 삭제 진행): {}", photo.getPhotoUrl(), e);
                // S3 삭제 실패 시에도 DB 삭제는 진행하도록 throw 주석 처리
                // throw new RuntimeException("사진 삭제 중 S3 오류가 발생했습니다.");
            }
            */
            log.warn("S3 사진 삭제 로직이 임시로 비활성화되었습니다. URL: {}", photo.getPhotoUrl());
            // S3 삭제 로직 임시 주석 처리 끝

            photoRepository.delete(photo);
            log.info("DB에서 사진 삭제 성공: ID {}", photo.getId());
        }

        return finalPhotos;
    }

    @Transactional
    public void deletePhoto(Long userId, Long photoId) {
        DiaryPhoto photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사진이 존재하지 않습니다."));

        if (!photo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("해당 사진에 대한 삭제 권한이 없습니다.");
        }
        // if (photo.getDiary() != null) {
        // throw new IllegalArgumentException("이미 일기에 등록된 사진은 삭제할 수 없습니다.");
        // }

        // S3 삭제 로직 임시 주석 처리 시작
        /*
        try {
            s3Uploader.delete(photo.getPhotoUrl());
            log.info("S3에서 사진 삭제 성공: {}", photo.getPhotoUrl());
        } catch (Exception e) {
            log.error("S3 사진 삭제 실패 (임시로 DB만 삭제 진행): {}", photo.getPhotoUrl(), e);
            // S3 삭제 실패 시에도 DB 삭제는 진행하도록 throw 주석 처리
            // throw new RuntimeException("사진 삭제 중 S3 오류가 발생했습니다.");
        }
        */
        log.warn("S3 사진 삭제 로직이 임시로 비활성화되었습니다. URL: {}", photo.getPhotoUrl());
        // S3 삭제 로직 임시 주석 처리 끝

        photoRepository.delete(photo);
        log.info("DB에서 사진 삭제 성공: ID {}", photoId);
    }
}