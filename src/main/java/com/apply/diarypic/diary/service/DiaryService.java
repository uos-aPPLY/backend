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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final AiDiaryService aiDiaryService;
    private final S3Uploader s3Uploader;

    @Transactional
    public DiaryResponse createDiary(DiaryRequest request, Long userId) {
        // 1) 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 2) photoIds로 DB에서 DiaryPhoto 엔티티 조회
        List<DiaryPhoto> photos = photoRepository.findAllById(request.getPhotoIds());
        if (photos.size() != request.getPhotoIds().size()) {
            throw new IllegalArgumentException("유효하지 않은 photoId가 포함되어 있습니다.");
        }

        // 3) sequence 설정 및 일기 연관
        for (int i = 0; i < photos.size(); i++) {
            photos.get(i).setSequence(i + 1);
        }

        // 4) Diary 엔티티 생성
        Diary diary = Diary.builder()
                .user(user)
                .title(request.getTitle())
                .content(request.getContent())
                .emotionIcon(request.getEmotionIcon())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .diaryPhotos(photos)
                .build();

        // 양방향 연관관계 설정
        photos.forEach(photo -> photo.setDiary(diary));

        // 5) 저장 및 응답
        Diary saved = diaryRepository.save(diary);
        return DiaryResponse.from(saved);
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
        // 1) 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 2) photoIds로 DB에서 최종 선택된 DiaryPhoto 조회
        List<DiaryPhoto> diaryPhotos = photoRepository.findAllById(request.getPhotoIds())
                .stream()
                .filter(photo -> photo.getUserId().equals(userId) && photo.getDiary() == null)
                .toList();

        if (diaryPhotos.isEmpty()) {
            throw new IllegalArgumentException("최종 선택된 사진이 없습니다.");
        }

        // 3) 중복 제거 (photoUrl 기준) + 순서 보존
        List<DiaryPhoto> distinctDiaryPhotos = diaryPhotos.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                DiaryPhoto::getPhotoUrl,
                                Function.identity(),
                                (existing, replacement) -> existing,
                                LinkedHashMap::new
                        ),
                        m -> new ArrayList<>(m.values())
                ));

        // 4) sequence 필드 설정
        for (int i = 0; i < distinctDiaryPhotos.size(); i++) {
            distinctDiaryPhotos.get(i).setSequence(i + 1);
        }

        // 5) AI 서버에 전달할 URL 리스트 생성
        List<String> photoUrls = distinctDiaryPhotos.stream()
                .map(DiaryPhoto::getPhotoUrl)
                .collect(Collectors.toList());

        // 6) AI 서버 호출하여 자동 생성된 일기 내용 받아오기
        String autoContent = aiDiaryService.generateDiaryContent(photoUrls);

        // 7) Diary 엔티티 생성 (연관된 DiaryPhoto 재사용)
        Diary diary = Diary.builder()
                .user(user)
                .title("자동 생성 일기")
                .content(autoContent)
                .emotionIcon("🙂")
                .isFavorited(false)
                .status("미확인")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .diaryPhotos(distinctDiaryPhotos)
                .build();

        // 8) 양방향 연관관계 설정
        distinctDiaryPhotos.forEach(photo -> photo.setDiary(diary));

        // 9) 저장 및 응답
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
                // 로그만 남기고 계속 진행
                log.error("S3 사진 삭제 실패: {}", photo.getPhotoUrl(), e);
            }
        });
        diaryRepository.delete(diary);
    }
}
