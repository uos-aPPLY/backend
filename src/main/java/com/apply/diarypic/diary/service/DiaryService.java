package com.apply.diarypic.diary.service;

import com.apply.diarypic.ai.dto.AiDiaryGenerateRequestDto;
import com.apply.diarypic.ai.dto.AiDiaryResponseDto;
import com.apply.diarypic.ai.dto.ImageInfoDto;
import com.apply.diarypic.ai.service.AiServerService;
import com.apply.diarypic.album.service.AlbumService;
import com.apply.diarypic.diary.dto.AiDiaryCreateRequest;
import com.apply.diarypic.diary.dto.DiaryRequest;
import com.apply.diarypic.diary.dto.DiaryResponse;
import com.apply.diarypic.diary.dto.FavoriteToggleRequest;
import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.diary.entity.DiaryPhoto;
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
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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
    private final AlbumService albumService;
    // private final DiaryAlbumRepository diaryAlbumRepository; // AlbumService가 처리하거나, 여기서 직접 사용

    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    // private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_DATE; // 필요시 사용

    @Transactional
    public DiaryResponse createDiaryWithAiAssistance(Long userId, LocalDate diaryDate, List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));

        String userWritingStyle = user.getWritingStylePrompt();
        if (userWritingStyle == null || userWritingStyle.isBlank()) {
            log.warn("User ID {} has no writingStylePrompt set. Using a default prompt.", userId);
            userWritingStyle = "오늘 있었던 일을 바탕으로 일기를 작성해줘.";
        }

        if (diaryDate == null) {
            diaryDate = LocalDate.now();
            log.warn("Diary date not provided for AI diary creation by user ID {}. Defaulting to today.", userId);
        }

        if (finalizedPhotoPayloads == null || finalizedPhotoPayloads.isEmpty() || finalizedPhotoPayloads.size() > 9) {
            throw new IllegalArgumentException("사진 정보가 올바르지 않습니다. 제공된 사진 수: " + (finalizedPhotoPayloads != null ? finalizedPhotoPayloads.size() : 0));
        }

        List<ImageInfoDto> imageInfoForAi = finalizedPhotoPayloads.stream()
                .map(payload -> {
                    DiaryPhoto diaryPhoto = photoRepository.findById(payload.getPhotoId())
                            .orElseThrow(() -> new IllegalArgumentException("사진 정보를 찾을 수 없습니다. ID: " + payload.getPhotoId()));
                    if (!diaryPhoto.getUserId().equals(userId)) {
                        throw new SecurityException("해당 사진에 대한 접근 권한이 없습니다. Photo ID: " + payload.getPhotoId());
                    }

                    // countryName, adminAreaLevel1, locality를 조합하여 detailedAddress 생성
                    String combinedAddress = Stream.of(diaryPhoto.getLocality(), diaryPhoto.getAdminAreaLevel1(), diaryPhoto.getCountryName())
                            .filter(StringUtils::hasText)
                            .collect(Collectors.joining(", "));
                    if (!StringUtils.hasText(combinedAddress)) {
                        combinedAddress = null; // 모든 정보가 없으면 null
                    }

                    return new ImageInfoDto(
                            diaryPhoto.getPhotoUrl(),
                            diaryPhoto.getShootingDateTime() != null ? diaryPhoto.getShootingDateTime().format(ISO_LOCAL_DATE_TIME_FORMATTER) : null,
                            combinedAddress, // 조합된 주소 전달
                            payload.getKeyword(),
                            payload.getSequence()
                    );
                })
                .collect(Collectors.toList());

        AiDiaryGenerateRequestDto aiRequest = new AiDiaryGenerateRequestDto(userWritingStyle, imageInfoForAi);
        AiDiaryResponseDto aiResponse = aiServerService.requestDiaryGeneration(aiRequest).block();

        if (aiResponse == null || !StringUtils.hasText(aiResponse.getDiary_text())) {
            log.error("AI 서버로부터 유효한 일기 내용을 받지 못했습니다. User ID: {}, AI 응답: {}", userId, aiResponse != null ? aiResponse.getDiary_text() : "null");
            throw new RuntimeException("AI 서버로부터 일기 내용을 생성하지 못했습니다.");
        }
        String autoContent = aiResponse.getDiary_text();

        Diary diary = createAndSaveDiaryAndAlbums(user, autoContent, diaryDate, finalizedPhotoPayloads, userId, true);
        return DiaryResponse.from(diary);
    }

    // createAndSaveDiaryAndAlbums 헬퍼 메소드는 앨범 이름 생성 규칙을 포함하여 AlbumService로 로직 이동 또는 여기서 호출
    private Diary createAndSaveDiaryAndAlbums(User user, String content, LocalDate diaryDate, List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads, Long userId, boolean isAiGenerated) {
        Diary diary = Diary.builder()
                .user(user)
                .content(content)
                .diaryDate(diaryDate)
                .emotionIcon(isAiGenerated ? "🙂" : (finalizedPhotoPayloads.isEmpty() ? "✏️" : "📷"))
                .isFavorited(false)
                .status(isAiGenerated ? "미확인" : "확인")
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

        // photoRepository.saveAll(diaryPhotosForDiaryEntities); // Diary에 Cascade 설정이 있다면 명시적 호출 불필요

        // 앨범 처리 로직: AlbumService의 메소드 호출
        if (!diaryPhotosForDiaryEntities.isEmpty()) {
            albumService.processDiaryAlbums(savedDiary, diaryPhotosForDiaryEntities);
        }

        // 키워드 처리 로직
        for (int i = 0; i < finalizedPhotoPayloads.size(); i++) {
            AiDiaryCreateRequest.FinalizedPhotoPayload payload = finalizedPhotoPayloads.get(i);
            DiaryPhoto currentDiaryPhoto = diaryPhotosForDiaryEntities.get(i);
            String keywordString = payload.getKeyword();
            if (StringUtils.hasText(keywordString)) {
                Arrays.stream(keywordString.split("\\s*,\\s*"))
                        .map(String::trim).filter(s -> !s.isEmpty()).distinct()
                        .forEach(kwText -> {
                            Keyword keywordEntity = keywordRepository.findByNameAndUser(kwText, user)
                                    .orElseGet(() -> keywordRepository.save(Keyword.builder().name(kwText).user(user).build()));
                            PhotoKeywordId pkId = new PhotoKeywordId(currentDiaryPhoto.getId(), keywordEntity.getId());
                            if(!photoKeywordRepository.existsById(pkId)){
                                photoKeywordRepository.save(PhotoKeyword.builder().diaryPhoto(currentDiaryPhoto).keyword(keywordEntity).build());
                            }
                        });
            }
        }
        return savedDiary;
    }

    @Transactional
    public DiaryResponse createDiary(DiaryRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        LocalDate diaryDate = request.getDiaryDate() != null ? request.getDiaryDate() : LocalDate.now();

        List<AiDiaryCreateRequest.FinalizedPhotoPayload> photoPayloadsForManualDiary = new ArrayList<>();
        if (request.getPhotoIds() != null) {
            for (int i = 0; i < request.getPhotoIds().size(); i++) {
                photoPayloadsForManualDiary.add(
                        new AiDiaryCreateRequest.FinalizedPhotoPayload(request.getPhotoIds().get(i), "", i + 1)
                );
            }
        }

        Diary diary = createAndSaveDiaryAndAlbums(user, request.getContent(), diaryDate, photoPayloadsForManualDiary, userId, false);
        return DiaryResponse.from(diary);
    }

    // ... (toggleDiaryFavorite, setDiaryFavorite, deleteDiary 메소드는 이전과 동일)
    @Transactional
    public DiaryResponse toggleDiaryFavorite(Long userId, Long diaryId) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new EntityNotFoundException("일기를 찾을 수 없습니다. ID: " + diaryId));
        if (!diary.getUser().getId().equals(userId)) {
            throw new SecurityException("해당 일기에 대한 수정 권한이 없습니다.");
        }
        diary.setIsFavorited(diary.getIsFavorited() == null ? true : !diary.getIsFavorited());
        Diary updatedDiary = diaryRepository.save(diary);
        log.info("User {} toggled favorite status for diary ID {} to {}", userId, diaryId, updatedDiary.getIsFavorited());
        return DiaryResponse.from(updatedDiary);
    }

    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public List<DiaryResponse> getFavoriteDiaries(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        List<Diary> favoriteDiaries = diaryRepository.findByUserAndIsFavoritedTrueOrderByDiaryDateDesc(user);

        if (favoriteDiaries.isEmpty()) {
            return Collections.emptyList();
        }

        return favoriteDiaries.stream()
                .map(DiaryResponse::from) // Diary를 DiaryResponse DTO로 변환
                .collect(Collectors.toList());
    }

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
                .orElseThrow(() -> new EntityNotFoundException("해당 일기를 찾을 수 없습니다."));
        if (!diary.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 일기에 대한 삭제 권한이 없습니다.");
        }
        diary.getDiaryPhotos().forEach(photo -> {
            log.warn("Diary deletion: S3 사진 삭제 로직이 임시로 비활성화되었습니다. URL: {}", photo.getPhotoUrl());
        });
        diaryRepository.delete(diary);
        log.info("일기 ID {} 삭제 완료.", diaryId);
    }
}