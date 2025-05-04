package com.apply.diarypic.diary.service;

import com.apply.diarypic.diary.dto.DiaryAutoRequest;
import com.apply.diarypic.diary.dto.DiaryRequest;
import com.apply.diarypic.diary.dto.DiaryResponse;
import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.diary.entity.DiaryPhoto;
import com.apply.diarypic.diary.repository.DiaryRepository;
import com.apply.diarypic.global.s3.S3Uploader;
import com.apply.diarypic.photo.repository.PhotoRepository;
import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final AiDiaryService aiDiaryService;
    private S3Uploader s3Uploader;

    @Transactional
    public DiaryResponse createDiary(DiaryRequest request, Long userId) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // DiaryRequest의 사진 정보를 DiaryPhoto 엔티티로 매핑
        List<DiaryPhoto> diaryPhotos = request.getPhotos().stream()
                .map(photoDto -> DiaryPhoto.builder()
                        .photoUrl(photoDto.getPhotoUrl())
                        .shootingDate(photoDto.getShootingDate())
                        .location(photoDto.getLocation())
                        .isRecommended(photoDto.getIsRecommended())
                        .sequence(photoDto.getSequence())
                        .userId(userId)
                        .createdAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());

        // Diary 엔티티 생성 (Cascade 옵션으로 DiaryPhoto도 함께 저장)
        Diary diary = Diary.builder()
                .user(user)
                .title(request.getTitle())
                .content(request.getContent())
                .emotionIcon(request.getEmotionIcon())
                .isFavorited(false)
                .status("미확인")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .diaryPhotos(diaryPhotos)
                .build();

        // DiaryPhoto와 Diary의 양방향 연관관계 설정
        diaryPhotos.forEach(photo -> photo.setDiary(diary));

        Diary savedDiary = diaryRepository.save(diary);

        return DiaryResponse.from(savedDiary);
    }

    /**
     * 최종 사진 리스트를 바탕으로 AI 서버에 자동 일기 내용을 생성 요청한 후 Diary를 생성합니다.
     *
     * @param request DiaryAutoRequest (최종 사진 리스트 포함)
     * @param userId  현재 사용자 ID
     * @return 생성된 Diary의 응답 DTO
     */
    @Transactional
    public DiaryResponse createDiaryAuto(DiaryAutoRequest request, Long userId) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 전달받은 photoIds로 DB에서 최종 선택된 DiaryPhoto 조회
        List<DiaryPhoto> diaryPhotos = photoRepository.findAllById(request.getPhotoIds())
                .stream()
                // 해당 사용자가 업로드했고, 아직 Diary에 연결되지 않은 사진만 필터링
                .filter(photo -> photo.getUserId().equals(userId) && photo.getDiary() == null)
                .collect(Collectors.toList());

        if(diaryPhotos.isEmpty()) {
            throw new IllegalArgumentException("최종 선택된 사진이 없습니다.");
        }

        // 중복된 사진 제거 (URL 기준)
        List<DiaryPhoto> distinctDiaryPhotos = diaryPhotos.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(DiaryPhoto::getPhotoUrl, photo -> photo, (existing, replacement) -> existing),
                        map -> map.values().stream().collect(Collectors.toList())
                ));

        // 최종 사진 URL 리스트 생성
        List<String> photoUrls = distinctDiaryPhotos.stream()
                .map(DiaryPhoto::getPhotoUrl)
                .collect(Collectors.toList());

        // AI 서버를 호출해 자동 생성된 일기 내용을 받아옴
        String autoContent = aiDiaryService.generateDiaryContent(photoUrls);

        // Diary 엔티티 생성. (타이틀은 고정 혹은 AI에서 별도로 제공 가능)
        Diary diary = Diary.builder()
                .user(user)
                .title("자동 생성 일기")
                .content(autoContent)
                .emotionIcon("🙂")
                .isFavorited(false)
                .status("미확인")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .diaryPhotos(distinctDiaryPhotos) // 이미 DB에 저장된 DiaryPhoto를 재사용
                .build();

        // 각 DiaryPhoto에 Diary 연관관계 설정
        distinctDiaryPhotos.forEach(photo -> photo.setDiary(diary));

        Diary savedDiary = diaryRepository.save(diary);
        return DiaryResponse.from(savedDiary);
    }

    @Transactional
    public void deleteDiary(Long userId, Long diaryId) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new IllegalArgumentException("해당 일기를 찾을 수 없습니다."));
        if (!diary.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 일기에 대한 삭제 권한이 없습니다.");
        }
        // 연관된 사진 S3에서 삭제
        diary.getDiaryPhotos().forEach(photo -> {
            try {
                s3Uploader.delete(photo.getPhotoUrl());
            } catch (Exception e) {
                // 로그 남기고 계속
            }
        });
        diaryRepository.delete(diary);
    }
}
