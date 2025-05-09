package com.apply.diarypic.diary.service;

import com.apply.diarypic.ai.dto.AiDiaryGenerateRequestDto;
import com.apply.diarypic.ai.dto.AiDiaryResponseDto;
import com.apply.diarypic.ai.dto.ImageInfoDto;
import com.apply.diarypic.ai.service.AiServerService;
import com.apply.diarypic.diary.dto.AiDiaryCreateRequest;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final UserRepository userRepository; // UserRepository 주입
    private final PhotoRepository photoRepository;
    private final AiServerService aiServerService;
    private final S3Uploader s3Uploader;

    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * AI를 활용하여 자동 일기를 생성합니다.
     *
     * @param userId 현재 사용자 ID
     * @param finalizedPhotoPayloads 프론트엔드에서 전달받은 최종 사진 정보 (ID, 키워드, 순서)
     * @return 생성된 일기 정보
     */
    @Transactional
    public DiaryResponse createDiaryWithAiAssistance(Long userId, /* String userSpeech 파라미터 제거 */ List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // 사용자의 writingStylePrompt 가져오기
        String userWritingStyle = user.getWritingStylePrompt();
        if (userWritingStyle == null || userWritingStyle.isBlank()) {
            log.warn("User ID {} has no writingStylePrompt set. Using a default or empty prompt.", userId);
            userWritingStyle = "오늘 있었던 일을 바탕으로 일기를 작성해줘.";
        }


        if (finalizedPhotoPayloads == null || finalizedPhotoPayloads.isEmpty()) {
            throw new IllegalArgumentException("AI 일기 생성을 위한 사진 정보가 없습니다.");
        }
        if (finalizedPhotoPayloads.size() > 9) {
            throw new IllegalArgumentException("AI 일기 생성은 최대 9장의 사진까지만 가능합니다.");
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

        // AiDiaryGenerateRequestDto 생성 시 userWritingStyle 사용
        AiDiaryGenerateRequestDto aiRequest = new AiDiaryGenerateRequestDto(userWritingStyle, imageInfoForAi);

        AiDiaryResponseDto aiResponse = aiServerService.requestDiaryGeneration(aiRequest).block(); // 동기 처리 (테스트용)

        if (aiResponse == null || aiResponse.getDiary_text() == null || aiResponse.getDiary_text().isEmpty()) {
            log.error("AI 서버로부터 유효한 일기 내용을 받지 못했습니다. User ID: {}, AI 응답: {}", userId, aiResponse != null ? aiResponse.getDiary_text() : "null");
            throw new RuntimeException("AI 서버로부터 일기 내용을 생성하지 못했습니다.");
        }
        String autoContent = aiResponse.getDiary_text();

        Diary diary = createAndSaveDiaryEntity(user, autoContent, finalizedPhotoPayloads, userId);
        return DiaryResponse.from(diary);
    }

    private Diary createAndSaveDiaryEntity(User user, String content, List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads, Long userId) {
        List<DiaryPhoto> diaryPhotosForDiary = finalizedPhotoPayloads.stream()
                .map(payload -> {
                    DiaryPhoto dp = photoRepository.findById(payload.getPhotoId())
                            .orElseThrow(() -> new IllegalArgumentException("저장할 사진 정보를 찾을 수 없습니다. ID: " + payload.getPhotoId()));
                    if (!dp.getUserId().equals(userId)) {
                        throw new SecurityException("해당 사진에 대한 접근 권한이 없습니다. Photo ID: " + payload.getPhotoId());
                    }
                    dp.setSequence(payload.getSequence());
                    return dp;
                })
                .collect(Collectors.toList());

        Diary diary = Diary.builder()
                .user(user)
                .title("AI 자동 생성 일기 - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .content(content)
                .emotionIcon("🙂")
                .isFavorited(false)
                .status("미확인")
                .diaryPhotos(diaryPhotosForDiary)
                .build();

        for (DiaryPhoto dp : diaryPhotosForDiary) {
            dp.setDiary(diary);
        }
        return diaryRepository.save(diary);
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

    @Transactional
    public DiaryResponse createDiary(DiaryRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        List<DiaryPhoto> photos = photoRepository.findAllById(request.getPhotoIds());
        if (photos.size() != request.getPhotoIds().size()) {
            throw new IllegalArgumentException("유효하지 않은 photoId가 포함되어 있습니다.");
        }
        for(DiaryPhoto photo : photos){
            if(!photo.getUserId().equals(userId)){
                throw new SecurityException("해당 사진에 대한 접근 권한이 없습니다. Photo ID: " + photo.getId());
            }
        }

        for (int i = 0; i < photos.size(); i++) {
            photos.get(i).setSequence(i + 1);
        }

        Diary diary = Diary.builder()
                .user(user)
                .title(request.getTitle() != null ? request.getTitle() : "나의 일기")
                .content(request.getContent())
                .emotionIcon(request.getEmotionIcon())
                .diaryPhotos(photos)
                .isFavorited(false)
                .status("확인")
                .build();

        photos.forEach(photo -> photo.setDiary(diary));
        Diary saved = diaryRepository.save(diary);
        return DiaryResponse.from(saved);
    }
}