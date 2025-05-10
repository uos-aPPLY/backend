package com.apply.diarypic.diary.service;

import com.apply.diarypic.ai.dto.AiDiaryGenerateRequestDto;
import com.apply.diarypic.ai.dto.AiDiaryResponseDto;
import com.apply.diarypic.ai.dto.ImageInfoDto;
import com.apply.diarypic.ai.service.AiServerService;
import com.apply.diarypic.diary.dto.AiDiaryCreateRequest;
import com.apply.diarypic.diary.dto.DiaryRequest;
import com.apply.diarypic.diary.dto.DiaryResponse;
import com.apply.diarypic.diary.dto.FavoriteToggleRequest;
import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.photo.entity.DiaryPhoto;
import com.apply.diarypic.diary.repository.DiaryRepository;
import com.apply.diarypic.global.s3.S3Uploader;
import com.apply.diarypic.keyword.entity.Keyword;
import com.apply.diarypic.keyword.entity.PhotoKeyword;
import com.apply.diarypic.keyword.entity.PhotoKeywordId;
import com.apply.diarypic.keyword.repository.KeywordRepository;
import com.apply.diarypic.keyword.repository.PhotoKeywordRepository;
import com.apply.diarypic.photo.repository.PhotoRepository;
import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final AiServerService aiServerService;
    private final S3Uploader s3Uploader;
    private final KeywordRepository keywordRepository;
    private final PhotoKeywordRepository photoKeywordRepository;

    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_DATE;


    @Transactional
    public DiaryResponse createDiaryWithAiAssistance(Long userId, LocalDate diaryDate, List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));

        String userWritingStyle = user.getWritingStylePrompt();
        if (userWritingStyle == null || userWritingStyle.isBlank()) {
            log.warn("User ID {} has no writingStylePrompt set. Using a default prompt.", userId);
            userWritingStyle = "오늘 있었던 일을 바탕으로 일기를 작성해줘."; // 기본 프롬프트
        }

        if (finalizedPhotoPayloads == null || finalizedPhotoPayloads.isEmpty()) {
            throw new IllegalArgumentException("AI 일기 생성을 위한 사진 정보가 없습니다.");
        }
        if (finalizedPhotoPayloads.size() > 9) {
            throw new IllegalArgumentException("AI 일기 생성은 최대 9장의 사진까지만 가능합니다.");
        }
        if (diaryDate == null) {
            diaryDate = LocalDate.now(); // 프론트에서 안넘어오면 오늘 날짜로
            log.warn("Diary date not provided for AI diary creation by user ID {}. Defaulting to today.", userId);
        }


        List<ImageInfoDto> imageInfoForAi = finalizedPhotoPayloads.stream()
                .map(payload -> {
                    DiaryPhoto diaryPhoto = photoRepository.findById(payload.getPhotoId())
                            .orElseThrow(() -> new IllegalArgumentException("사진 정보를 찾을 수 없습니다. ID: " + payload.getPhotoId()));
                    if (!diaryPhoto.getUserId().equals(userId)) {
                        throw new SecurityException("해당 사진에 대한 접근 권한이 없습니다. Photo ID: " + payload.getPhotoId());
                    }
                    return new ImageInfoDto(
                            diaryPhoto.getPhotoUrl(),
                            diaryPhoto.getShootingDateTime() != null ? diaryPhoto.getShootingDateTime().format(ISO_LOCAL_DATE_TIME_FORMATTER) : null,
                            diaryPhoto.getDetailedAddress(),
                            payload.getKeyword(),
                            payload.getSequence()
                    );
                })
                .collect(Collectors.toList());

        AiDiaryGenerateRequestDto aiRequest = new AiDiaryGenerateRequestDto(userWritingStyle, imageInfoForAi);
        AiDiaryResponseDto aiResponse = aiServerService.requestDiaryGeneration(aiRequest).block();

        if (aiResponse == null || aiResponse.getDiary_text() == null || aiResponse.getDiary_text().isEmpty()) {
            log.error("AI 서버로부터 유효한 일기 내용을 받지 못했습니다. User ID: {}, AI 응답: {}", userId, aiResponse != null ? aiResponse.getDiary_text() : "null");
            throw new RuntimeException("AI 서버로부터 일기 내용을 생성하지 못했습니다.");
        }
        String autoContent = aiResponse.getDiary_text();

        Diary diary = createAndSaveDiaryEntity(user, autoContent, diaryDate, finalizedPhotoPayloads, userId);
        return DiaryResponse.from(diary);
    }

    private Diary createAndSaveDiaryEntity(User user, String content, LocalDate diaryDate, List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads, Long userId) {
        Diary diary = Diary.builder()
                .user(user)
                .content(content)
                .diaryDate(diaryDate)
                .emotionIcon("happy") // 기본 감정
                .isFavorited(false)
                .status("미확인")
                .diaryPhotos(new ArrayList<>())
                .build();
        Diary savedDiary = diaryRepository.save(diary);

        List<DiaryPhoto> diaryPhotosForDiaryEntities = finalizedPhotoPayloads.stream()
                .map(payload -> photoRepository.findById(payload.getPhotoId())
                        .map(dp -> {
                            if (!dp.getUserId().equals(userId)) {
                                throw new SecurityException("해당 사진에 대한 접근 권한이 없습니다. Photo ID: " + payload.getPhotoId());
                            }
                            dp.setDiary(savedDiary);
                            dp.setSequence(payload.getSequence());
                            savedDiary.getDiaryPhotos().add(dp);
                            return dp;
                        }).orElseThrow(() -> new IllegalArgumentException("저장할 사진 정보를 찾을 수 없습니다. ID: " + payload.getPhotoId()))
                ).collect(Collectors.toList());

        // 키워드 처리 로직
        for (int i = 0; i < finalizedPhotoPayloads.size(); i++) {
            AiDiaryCreateRequest.FinalizedPhotoPayload payload = finalizedPhotoPayloads.get(i);
            DiaryPhoto currentDiaryPhoto = diaryPhotosForDiaryEntities.get(i); // 이미 조회된 DiaryPhoto 사용
            String keywordString = payload.getKeyword();
            if (keywordString != null && !keywordString.isBlank()) {
                Arrays.stream(keywordString.split("\\s*,\\s*"))
                        .map(String::trim)
                        .filter(kwText -> !kwText.isEmpty())
                        .distinct()
                        .forEach(kwText -> {
                            Keyword keywordEntity = keywordRepository.findByNameAndUser(kwText, user)
                                    .orElseGet(() -> {
                                        Keyword newKeyword = Keyword.builder().name(kwText).user(user).build();
                                        return keywordRepository.save(newKeyword);
                                    });
                            PhotoKeywordId photoKeywordId = new PhotoKeywordId(currentDiaryPhoto.getId(), keywordEntity.getId());
                            if (!photoKeywordRepository.existsById(photoKeywordId)) {
                                PhotoKeyword newPhotoKeyword = PhotoKeyword.builder()
                                        .diaryPhoto(currentDiaryPhoto)
                                        .keyword(keywordEntity)
                                        .build();
                                photoKeywordRepository.save(newPhotoKeyword);
                            }
                        });
            }
        }
        return savedDiary;
    }

    @Transactional
    public DiaryResponse createDiary(DiaryRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        LocalDate diaryDate = request.getDiaryDate();
        if (diaryDate == null) {
            diaryDate = LocalDate.now(); // 프론트에서 안넘어오면 오늘 날짜로
            log.warn("Diary date not provided for manual diary creation by user ID {}. Defaulting to today.", userId);
        }

        List<Long> photoIds = request.getPhotoIds() != null ? request.getPhotoIds() : new ArrayList<>();
        List<DiaryPhoto> photosFromDb = photoRepository.findAllById(photoIds);

        if (photosFromDb.size() != photoIds.size()) {
            List<Long> foundIds = photosFromDb.stream().map(DiaryPhoto::getId).collect(Collectors.toList());
            List<Long> missingIds = photoIds.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toList());
            throw new IllegalArgumentException("유효하지 않거나 찾을 수 없는 photoId가 포함되어 있습니다. Missing IDs: " + missingIds);
        }

        List<DiaryPhoto> photosForDiary = new ArrayList<>();
        // 요청받은 photoIds의 순서를 유지하기 위해 루프 사용
        for (Long photoId : photoIds) {
            DiaryPhoto photo = photosFromDb.stream()
                    .filter(p -> p.getId().equals(photoId))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("Photo not found in fetched list: " + photoId));

            if(!photo.getUserId().equals(userId)){
                throw new SecurityException("해당 사진에 대한 접근 권한이 없습니다. Photo ID: " + photo.getId());
            }
            photosForDiary.add(photo);
        }

        for (int i = 0; i < photosForDiary.size(); i++) {
            photosForDiary.get(i).setSequence(i + 1);
        }

        Diary diary = Diary.builder()
                .user(user)
                .content(request.getContent())
                .diaryDate(diaryDate)
                .emotionIcon(request.getEmotionIcon())
                .isFavorited(false)
                .status("확인")
                .diaryPhotos(new ArrayList<>())
                .build();

        Diary savedDiary = diaryRepository.save(diary);

        photosForDiary.forEach(photo -> {
            photo.setDiary(savedDiary);
            savedDiary.getDiaryPhotos().add(photo);
        });
        return DiaryResponse.from(savedDiary);
    }

    /**
     * 일기의 좋아요(즐겨찾기) 상태를 토글합니다.
     * @param userId 현재 사용자 ID
     * @param diaryId 좋아요 상태를 변경할 일기의 ID
     * @return 업데이트된 일기 정보
     */
    @Transactional
    public DiaryResponse toggleDiaryFavorite(Long userId, Long diaryId) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new EntityNotFoundException("일기를 찾을 수 없습니다. ID: " + diaryId));

        // 해당 일기가 현재 사용자의 것인지 확인
        if (!diary.getUser().getId().equals(userId)) {
            throw new SecurityException("해당 일기에 대한 수정 권한이 없습니다.");
        }

        // isFavorited 값을 토글 (null인 경우 true로 시작)
        diary.setIsFavorited(diary.getIsFavorited() == null ? true : !diary.getIsFavorited());
        // 또는 명시적 토글 메소드 사용: diary.toggleFavorite();

        Diary updatedDiary = diaryRepository.save(diary);
        log.info("User {} toggled favorite status for diary ID {} to {}", userId, diaryId, updatedDiary.getIsFavorited());
        return DiaryResponse.from(updatedDiary);
    }

    /**
     * 일기의 좋아요(즐겨찾기) 상태를 특정 값으로 설정합니다. (선택적 방법)
     * @param userId 현재 사용자 ID
     * @param diaryId 상태를 변경할 일기의 ID
     * @param request 좋아요 상태를 담은 요청 DTO
     * @return 업데이트된 일기 정보
     */
    @Transactional
    public DiaryResponse setDiaryFavorite(Long userId, Long diaryId, FavoriteToggleRequest request) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new EntityNotFoundException("일기를 찾을 수 없습니다. ID: " + diaryId));

        if (!diary.getUser().getId().equals(userId)) {
            throw new SecurityException("해당 일기에 대한 수정 권한이 없습니다.");
        }

        diary.setIsFavorited(request.getIsFavorited());
        Diary updatedDiary = diaryRepository.save(diary);
        log.info("User {} set favorite status for diary ID {} to {}", userId, diaryId, updatedDiary.getIsFavorited());
        return DiaryResponse.from(updatedDiary);
    }

    @Transactional
    public void deleteDiary(Long userId, Long diaryId) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new IllegalArgumentException("해당 일기를 찾을 수 없습니다."));
        if (!diary.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 일기에 대한 삭제 권한이 없습니다.");
        }
        diary.getDiaryPhotos().forEach(photo -> {
            try {
                s3Uploader.delete(photo.getPhotoUrl());
            } catch (Exception e) {
                log.error("S3 사진 삭제 실패: {}", photo.getPhotoUrl(), e);
            }
        });
        diaryRepository.delete(diary);
    }
}