package com.apply.diarypic.photo.service;

import com.apply.diarypic.diary.entity.DiaryPhoto;
import com.apply.diarypic.global.s3.S3Uploader;
import com.apply.diarypic.photo.dto.PhotoResponse; // DTO 임포트
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

    /**
     * 현재 사용자의 임시 업로드 사진 조회 (diary가 아직 연결되지 않은 사진)
     * DTO를 사용하여 필요한 데이터만 반환하고 LazyInitializationException 방지.
     */
    @Transactional(readOnly = true) // DTO 변환 중 엔티티의 다른 필드 접근 가능성 고려
    public List<PhotoResponse> getTemporaryPhotos(Long userId) {
        List<DiaryPhoto> tempPhotos = photoRepository.findByDiaryIsNullAndUserId(userId);
        // DiaryPhoto 엔티티를 PhotoResponse DTO로 변환
        return tempPhotos.stream()
                .map(PhotoResponse::from) // PhotoResponse.from 정적 메소드 사용
                .collect(Collectors.toList());
    }

    /**
     * 최종 선택 확정
     * (이 메소드도 List<PhotoResponse>를 반환하도록 변경하는 것이 일관성 있고 좋습니다.)
     */
    @Transactional
    public List<DiaryPhoto> finalizePhotoSelection(Long userId, List<Long> finalPhotoIds) {
        List<DiaryPhoto> tempPhotos = photoRepository.findByDiaryIsNullAndUserId(userId);
        if (tempPhotos.isEmpty()) {
            throw new IllegalArgumentException("임시 업로드된 사진이 없습니다.");
        }

        List<DiaryPhoto> finalPhotos = tempPhotos.stream()
                .filter(photo -> finalPhotoIds.contains(photo.getId()))
                .collect(Collectors.toList());

        if (finalPhotos.size() > 10) { // 기획은 9장이었으나, 코드에는 10장으로 되어 있음
            throw new IllegalArgumentException("최종 선택 사진은 최대 10장까지 가능합니다.");
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
        // 변경된 finalPhotos를 저장해야 sequence가 DB에 반영됩니다.
        // photoRepository.saveAll(finalPhotos); // JPA Dirty Checking으로 자동 업데이트될 수도 있지만, 명시적 save 권장

        for (DiaryPhoto photo : photosToDelete) {
            try {
                s3Uploader.delete(photo.getPhotoUrl());
                log.info("S3에서 사진 삭제 성공: {}", photo.getPhotoUrl());
            } catch (Exception e) {
                log.error("S3 사진 삭제 실패: {}", photo.getPhotoUrl(), e);
                // S3 삭제 실패 시 롤백 여부 등 정책 필요
            }
            photoRepository.delete(photo);
            log.info("DB에서 사진 삭제 성공: ID {}", photo.getId());
        }

        return finalPhotos; // 이 부분도 DTO로 변환하여 반환하는 것을 고려
    }

    /**
     * 임시 사진 개별 삭제
     */
    @Transactional
    public void deletePhoto(Long userId, Long photoId) {
        DiaryPhoto photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사진이 존재하지 않습니다."));

        if (!photo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("해당 사진에 대한 삭제 권한이 없습니다.");
        }
        // if (photo.getDiary() != null) { // 일기 포함 사진 삭제 방지 로직 (주석)
        // throw new IllegalArgumentException("이미 일기에 등록된 사진은 삭제할 수 없습니다.");
        // }
        try {
            s3Uploader.delete(photo.getPhotoUrl());
            log.info("S3에서 사진 삭제 성공: {}", photo.getPhotoUrl());
        } catch (Exception e) {
            log.error("S3 사진 삭제 실패: {}", photo.getPhotoUrl(), e);
            throw new RuntimeException("사진 삭제 중 S3 오류가 발생했습니다.");
        }
        photoRepository.delete(photo);
        log.info("DB에서 사진 삭제 성공: ID {}", photoId);
    }
}