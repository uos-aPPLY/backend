package com.apply.diarypic.photo.service;

import com.apply.diarypic.diary.entity.DiaryPhoto;
import com.apply.diarypic.global.s3.S3Uploader;
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
    private final S3Uploader s3Uploader; // S3 파일 삭제 기능 포함

    /**
     * 현재 사용자의 임시 업로드 사진 조회 (diary가 아직 연결되지 않은 사진)
     */
    @Transactional(readOnly = true)
    public List<DiaryPhoto> getTemporaryPhotos(Long userId) {
        return photoRepository.findByDiaryIsNullAndUserId(userId);
    }

    /**
     * 최종 선택 확정
     * - 사용자가 선택한 사진 ID 목록(순서대로)을 기반으로 최종 목록을 구성
     * - 최종 선택에 포함되지 않은 사진은 S3와 DB에서 삭제
     * - 최종 선택 사진에는 순서(sequence)를 업데이트
     *
     * @param userId       현재 사용자 ID
     * @param finalPhotoIds 최종 선택된 사진 ID 목록
     * @return 최종 선택된 사진 목록
     */
    @Transactional
    public List<DiaryPhoto> finalizePhotoSelection(Long userId, List<Long> finalPhotoIds) {
        // 현재 사용자의 임시 사진 조회
        List<DiaryPhoto> tempPhotos = photoRepository.findByDiaryIsNullAndUserId(userId);
        if (tempPhotos.isEmpty()) {
            throw new IllegalArgumentException("임시 업로드된 사진이 없습니다.");
        }

        // 최종 선택 사진 필터링
        List<DiaryPhoto> finalPhotos = tempPhotos.stream()
                .filter(photo -> finalPhotoIds.contains(photo.getId()))
                .collect(Collectors.toList());

        if (finalPhotos.size() > 10) {
            throw new IllegalArgumentException("최종 선택 사진은 최대 10장까지 가능합니다.");
        }

        // 최종 선택에 포함되지 않은 사진은 삭제 대상
        List<DiaryPhoto> photosToDelete = tempPhotos.stream()
                .filter(photo -> !finalPhotoIds.contains(photo.getId()))
                .collect(Collectors.toList());

        // 최종 사진 순서 업데이트 (리스트 순서대로 sequence 값 지정)
        for (int i = 0; i < finalPhotoIds.size(); i++) {
            Long photoId = finalPhotoIds.get(i);
            for (DiaryPhoto photo : finalPhotos) {
                if (photo.getId().equals(photoId)) {
                    photo.setSequence(i + 1); // sequence 1부터 시작
                    break;
                }
            }
        }

        // 최종 선택에 포함되지 않은 사진 S3 및 DB 삭제
        for (DiaryPhoto photo : photosToDelete) {
            try {
                s3Uploader.delete(photo.getPhotoUrl());
                log.info("S3에서 사진 삭제 성공: {}", photo.getPhotoUrl());
            } catch (Exception e) {
                log.error("S3 사진 삭제 실패: {}", photo.getPhotoUrl(), e);
            }
            photoRepository.delete(photo);
            log.info("DB에서 사진 삭제 성공: ID {}", photo.getId());
        }

        return finalPhotos;
    }

    /**
     * 임시 사진 개별 삭제
     *
     * @param userId  현재 사용자 ID
     * @param photoId 삭제할 사진 ID
     */
    @Transactional
    public void deletePhoto(Long userId, Long photoId) {
        DiaryPhoto photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사진이 존재하지 않습니다."));
//        if (photo.getDiary() != null) {
//            throw new IllegalArgumentException("이미 일기에 등록된 사진은 삭제할 수 없습니다.");
//        }
        if (!photo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("해당 사진에 대한 삭제 권한이 없습니다.");
        }
        try {
            s3Uploader.delete(photo.getPhotoUrl());
            log.info("S3에서 사진 삭제 성공: {}", photo.getPhotoUrl());
        } catch (Exception e) {
            log.error("S3 사진 삭제 실패: {}", photo.getPhotoUrl(), e);
            throw new RuntimeException("사진 삭제 중 오류가 발생했습니다.");
        }
        photoRepository.delete(photo);
        log.info("DB에서 사진 삭제 성공: ID {}", photoId);
    }
}
