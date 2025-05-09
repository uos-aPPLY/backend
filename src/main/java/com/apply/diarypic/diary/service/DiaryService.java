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
import com.apply.diarypic.keyword.entity.Keyword;
import com.apply.diarypic.keyword.entity.PhotoKeyword;
import com.apply.diarypic.keyword.entity.PhotoKeywordId;
import com.apply.diarypic.keyword.repository.KeywordRepository;
import com.apply.diarypic.keyword.repository.PhotoKeywordRepository;
import com.apply.diarypic.keyword.service.KeywordService;
import com.apply.diarypic.photo.repository.PhotoRepository;
import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
    private final KeywordRepository keywordRepository; // KeywordRepository 주입
    private final PhotoKeywordRepository photoKeywordRepository; // PhotoKeywordRepository 주입
    private final KeywordService keywordService;

    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Transactional
    public DiaryResponse createDiaryWithAiAssistance(Long userId, List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

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
                            payload.getKeyword(), // 프론트에서 받은 쉼표 구분 키워드 문자열
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
                    // 기존 키워드 연결 정보 초기화 (선택적: 덮어쓰기 방식이라면)
                    // photoKeywordRepository.deleteByDiaryPhotoId(dp.getId());
                    // photoCustomKeywordRepository.deleteByDiaryPhotoId(dp.getId());
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
                .diaryPhotos(new ArrayList<>()) // 초기에는 비워두고 아래에서 채움 (JPA LifeCycle 고려)
                .build();

        // 먼저 Diary를 저장하여 ID를 할당받음 (DiaryPhoto의 FK로 사용하기 위해)
        Diary savedDiary = diaryRepository.save(diary);

        List<DiaryPhoto> diaryPhotosForDiaryEntities = finalizedPhotoPayloads.stream()
                .map(payload -> photoRepository.findById(payload.getPhotoId())
                        .map(dp -> {
                            if (!dp.getUserId().equals(userId)) {
                                throw new SecurityException("해당 사진에 대한 접근 권한이 없습니다. Photo ID: " + payload.getPhotoId());
                            }
                            dp.setDiary(savedDiary); // DiaryPhoto에 Diary 설정
                            dp.setSequence(payload.getSequence());
                            savedDiary.getDiaryPhotos().add(dp); // Diary 컬렉션에도 추가
                            return dp;
                        }).orElseThrow(() -> new IllegalArgumentException("저장할 사진 정보를 찾을 수 없습니다. ID: " + payload.getPhotoId()))
                ).collect(Collectors.toList());

        for (int i = 0; i < finalizedPhotoPayloads.size(); i++) {
            AiDiaryCreateRequest.FinalizedPhotoPayload payload = finalizedPhotoPayloads.get(i);
            DiaryPhoto currentDiaryPhoto = diaryPhotosForDiaryEntities.get(i);

            String keywordString = payload.getKeyword(); // "키워드1, 키워드2, 자유입력키워드"
            if (keywordString != null && !keywordString.isBlank()) {
                Arrays.stream(keywordString.split("\\s*,\\s*"))
                        .map(String::trim)
                        .filter(kwText -> !kwText.isEmpty())
                        .distinct()
                        .forEach(kwText -> {
                            // 1. 사용자의 개인 키워드로 생성 또는 조회
                            // KeywordService의 createOrGetPersonalKeyword는 KeywordDto를 반환하므로,
                            // Keyword 엔티티를 직접 다루려면 Repository를 사용하거나 KeywordService에 엔티티 반환 메소드 추가 필요.
                            // 여기서는 Repository를 직접 사용한다고 가정.
                            Keyword keywordEntity = keywordRepository.findByNameAndUser(kwText, user)
                                    .orElseGet(() -> {
                                        Keyword newKeyword = Keyword.builder()
                                                .name(kwText)
                                                .user(user)
                                                .build();
                                        return keywordRepository.save(newKeyword);
                                    });

                            // 2. photo_keywords 테이블에 매핑 (중복 방지)
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
        return savedDiary; // 키워드까지 처리된 Diary 반환
    }

    // deleteDiary, createDiary 메소드는 이전과 동일
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

        List<DiaryPhoto> photosFromDb = photoRepository.findAllById(request.getPhotoIds());
        if (photosFromDb.size() != request.getPhotoIds().size()) {
            throw new IllegalArgumentException("유효하지 않은 photoId가 포함되어 있습니다.");
        }

        List<DiaryPhoto> photosForDiary = new ArrayList<>();
        for (Long photoId : request.getPhotoIds()) { // 요청받은 ID 순서대로 처리
            DiaryPhoto photo = photosFromDb.stream()
                    .filter(p -> p.getId().equals(photoId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Photo not found with id: " + photoId)); // 논리적으로 발생하기 어려움

            if(!photo.getUserId().equals(userId)){
                throw new SecurityException("해당 사진에 대한 접근 권한이 없습니다. Photo ID: " + photo.getId());
            }
            photosForDiary.add(photo);
        }


        for (int i = 0; i < photosForDiary.size(); i++) {
            photosForDiary.get(i).setSequence(i + 1); // 요청받은 photoIds 순서대로 sequence 부여
        }

        Diary diary = Diary.builder()
                .user(user)
                .title(request.getTitle() != null && !request.getTitle().isBlank() ? request.getTitle() : "나의 일기")
                .content(request.getContent())
                .emotionIcon(request.getEmotionIcon())
                // .diaryPhotos(photosForDiary) // 아래에서 처리
                .isFavorited(false)
                .status("확인")
                .build();

        Diary savedDiary = diaryRepository.save(diary); // 먼저 Diary 저장

        photosForDiary.forEach(photo -> {
            photo.setDiary(savedDiary); // DiaryPhoto에 Diary 설정
            savedDiary.getDiaryPhotos().add(photo); // Diary 컬렉션에도 추가
        });
        // photoRepository.saveAll(photosForDiary); // 명시적으로 호출하지 않아도 Cascade로 처리될 수 있음

        // createAndSaveDiaryEntity 헬퍼 메소드를 호출하도록 리팩토링 가능 (키워드 저장 로직 포함시키려면)
        // 하지만 이 createDiary는 프론트에서 keyword 문자열을 직접 보내지 않으므로, 키워드 처리 로직은 여기에 없음.
        // 만약 DiaryRequest DTO에도 keyword 문자열을 추가한다면, 위 AI 생성 로직과 유사하게 키워드 저장 가능.

        return DiaryResponse.from(savedDiary);
    }
}