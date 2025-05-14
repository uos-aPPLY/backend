package com.apply.diarypic.diary.service;

import com.apply.diarypic.ai.dto.AiDiaryGenerateRequestDto;
import com.apply.diarypic.ai.dto.AiDiaryModifyRequestDto;
import com.apply.diarypic.ai.dto.AiDiaryResponseDto;
import com.apply.diarypic.ai.dto.ImageInfoDto;
import com.apply.diarypic.ai.service.AiServerService;
import com.apply.diarypic.album.repository.DiaryAlbumRepository;
import com.apply.diarypic.album.service.AlbumService;
import com.apply.diarypic.diary.dto.*;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
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
    private final DiaryAlbumRepository diaryAlbumRepository;

    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;


    @Transactional(readOnly = true)
    public DiaryResponse getDiaryById(Long userId, Long diaryId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));
        Diary diary = diaryRepository.findByIdAndUserAndDeletedAtIsNull(diaryId, user)
                .orElseThrow(() -> new EntityNotFoundException("일기를 찾을 수 없습니다. ID: " + diaryId));
        return DiaryResponse.from(diary);
    }

    @Transactional(readOnly = true)
    public Page<DiaryResponse> getDiariesByUser(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));
        Page<Diary> diariesPage = diaryRepository.findActiveDiariesByUser(user, pageable);
        return diariesPage.map(DiaryResponse::from);
    }

    @Transactional(readOnly = true)
    public DiaryResponse getDiaryByDate(Long userId, LocalDate diaryDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));
        Diary diary = diaryRepository.findByUserAndDiaryDateAndDeletedAtIsNull(user, diaryDate)
                .orElseThrow(() -> new EntityNotFoundException("해당 날짜(" + diaryDate + ")에 작성된 일기를 찾을 수 없습니다."));
        return DiaryResponse.from(diary);
    }

    @Transactional
    public DiaryResponse createDiary(DiaryRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        LocalDate diaryDate = request.getDiaryDate() != null ? request.getDiaryDate() : LocalDate.now();

        List<AiDiaryCreateRequest.FinalizedPhotoPayload> photoPayloads = new ArrayList<>();
        List<Long> currentPhotoIds = new ArrayList<>();

        if (!CollectionUtils.isEmpty(request.getPhotoIds())) {
            for (int i = 0; i < request.getPhotoIds().size(); i++) {
                Long photoId = request.getPhotoIds().get(i);
                photoPayloads.add(new AiDiaryCreateRequest.FinalizedPhotoPayload(photoId, "", i + 1));
                currentPhotoIds.add(photoId);
            }
        }

        Diary diary = createAndSaveDiaryAndAlbums(user, request.getContent(), request.getEmotionIcon(), diaryDate, photoPayloads, userId, false);

        if (request.getRepresentativePhotoId() != null) {
            setExplicitRepresentativePhoto(diary, request.getRepresentativePhotoId(), userId, currentPhotoIds);
        } else {
            setInitialRepresentativePhoto(diary);
        }
        return DiaryResponse.from(diaryRepository.save(diary));
    }

    @Transactional
    public DiaryResponse createDiaryWithAiAssistance(Long userId, AiDiaryCreateRequest aiDiaryCreateRequest) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        String userWritingStyle = user.getWritingStylePrompt();
        if (!StringUtils.hasText(userWritingStyle)) userWritingStyle = "오늘 있었던 일을 바탕으로 일기를 작성해줘.";

        LocalDate diaryDate = aiDiaryCreateRequest.getDiaryDate();
        if (diaryDate == null) diaryDate = LocalDate.now();

        List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads = aiDiaryCreateRequest.getFinalizedPhotos();
        if (finalizedPhotoPayloads == null || finalizedPhotoPayloads.isEmpty() || finalizedPhotoPayloads.size() > 9) {
            throw new IllegalArgumentException("사진 정보가 올바르지 않습니다.");
        }

        List<ImageInfoDto> imageInfoForAi = finalizedPhotoPayloads.stream()
                .map(payload -> {
                    DiaryPhoto diaryPhoto = photoRepository.findById(payload.getPhotoId()).orElseThrow(() -> new IllegalArgumentException("Photo not found: " + payload.getPhotoId()));
                    if (!diaryPhoto.getUserId().equals(userId)) throw new SecurityException("Photo access denied: " + payload.getPhotoId());
                    String combinedAddress = Stream.of(diaryPhoto.getLocality(), diaryPhoto.getAdminAreaLevel1(), diaryPhoto.getCountryName())
                            .filter(StringUtils::hasText).collect(Collectors.joining(", "));
                    return new ImageInfoDto(diaryPhoto.getPhotoUrl(), diaryPhoto.getShootingDateTime() != null ? diaryPhoto.getShootingDateTime().format(ISO_LOCAL_DATE_TIME_FORMATTER) : null,
                            StringUtils.hasText(combinedAddress) ? combinedAddress : null, payload.getKeyword(), payload.getSequence());
                }).collect(Collectors.toList());

        AiDiaryGenerateRequestDto aiRequest = new AiDiaryGenerateRequestDto(userWritingStyle, imageInfoForAi);
        AiDiaryResponseDto aiResponse = aiServerService.requestDiaryGeneration(aiRequest).block();

        if (aiResponse == null || !StringUtils.hasText(aiResponse.getDiary())) {
            throw new RuntimeException("AI 서버로부터 일기 내용을 생성하지 못했습니다.");
        }
        String autoContent = aiResponse.getDiary();
        String autoEmoji = aiResponse.getEmoji();

        Diary diary = createAndSaveDiaryAndAlbums(user, autoContent, autoEmoji, diaryDate, finalizedPhotoPayloads, userId, true);

        if (aiDiaryCreateRequest.getRepresentativePhotoId() != null) {
            setExplicitRepresentativePhoto(diary, aiDiaryCreateRequest.getRepresentativePhotoId(), userId, finalizedPhotoPayloads.stream().map(AiDiaryCreateRequest.FinalizedPhotoPayload::getPhotoId).collect(Collectors.toList()));
        } else {
            setInitialRepresentativePhoto(diary);
        }
        return DiaryResponse.from(diaryRepository.save(diary));
    }

    private Diary createAndSaveDiaryAndAlbums(User user, String content, String emoji, LocalDate diaryDate, List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads, Long userId, boolean isAiGenerated) {
        Diary diary = Diary.builder()
                .user(user)
                .content(content)
                .emotionIcon(emoji)
                .diaryDate(diaryDate)
                .isFavorited(false)
                .status(isAiGenerated ? "미확인" : "확인")
                .diaryPhotos(new ArrayList<>())
                .build();
        Diary savedDiary = diaryRepository.save(diary);

        List<DiaryPhoto> diaryPhotosForDiaryEntities = new ArrayList<>();
        if (finalizedPhotoPayloads != null) {
            finalizedPhotoPayloads.sort(Comparator.comparingInt(AiDiaryCreateRequest.FinalizedPhotoPayload::getSequence));
            for (AiDiaryCreateRequest.FinalizedPhotoPayload payload : finalizedPhotoPayloads) {
                DiaryPhoto diaryPhoto = photoRepository.findById(payload.getPhotoId())
                        .map(dp -> {
                            if (!dp.getUserId().equals(userId)) {
                                throw new SecurityException("해당 사진에 대한 접근 권한이 없습니다. Photo ID: " + payload.getPhotoId());
                            }
                            dp.setDiary(savedDiary);
                            dp.setSequence(payload.getSequence());
                            savedDiary.getDiaryPhotos().add(dp);
                            return dp;
                        }).orElseThrow(() -> new IllegalArgumentException("저장할 사진 정보를 찾을 수 없습니다. ID: " + payload.getPhotoId()));
                diaryPhotosForDiaryEntities.add(diaryPhoto);

                String keywordStringFromFrontend = payload.getKeyword();
                if (StringUtils.hasText(keywordStringFromFrontend)) {
                    Arrays.stream(keywordStringFromFrontend.split("\\s*,\\s*"))
                            .map(String::trim)
                            .filter(kwText -> !kwText.isEmpty())
                            .forEach(kwText -> {
                                Optional<Keyword> keywordEntityOpt = keywordRepository.findByNameAndUser(kwText, user);

                                if (keywordEntityOpt.isPresent()) {
                                    Keyword foundKeyword = keywordEntityOpt.get();
                                    PhotoKeyword newPhotoKeyword = PhotoKeyword.builder()
                                            .diaryPhoto(diaryPhoto)
                                            .keyword(foundKeyword)
                                            .build();
                                    photoKeywordRepository.save(newPhotoKeyword);
                                    log.debug("사진 ID {}에 기존 개인 키워드 '{}'(ID:{}) 매핑 저장 (중복 허용).", diaryPhoto.getId(), kwText, foundKeyword.getId());
                                } else {
                                    log.debug("사진 ID {}에 대한 자유 입력 키워드 '{}'는 사용자 {}의 개인 키워드 목록에 없으므로 DB에 매핑하지 않음. AI 전달용으로만 사용됨.", diaryPhoto.getId(), kwText, user.getId());
                                }
                            });
                }
            }
        }

        if (!diaryPhotosForDiaryEntities.isEmpty()) {
            albumService.processDiaryAlbums(savedDiary, diaryPhotosForDiaryEntities);
        }

        return savedDiary;
    }

    @Transactional
    public DiaryResponse updateDiaryManual(Long userId, Long diaryId, DiaryManualUpdateRequest request) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new EntityNotFoundException("수정할 일기를 찾을 수 없습니다. ID: " + diaryId));

        if (!diary.getUser().getId().equals(userId)) {
            throw new SecurityException("해당 일기에 대한 수정 권한이 없습니다.");
        }

        boolean updated = false;
        if (StringUtils.hasText(request.getContent())) {
            diary.setContent(request.getContent());
            updated = true;
        }
        if (StringUtils.hasText(request.getEmotionIcon())) {
            diary.setEmotionIcon(request.getEmotionIcon());
            updated = true;
        }

        if (updated) {
            return DiaryResponse.from(diaryRepository.save(diary));
        }
        return DiaryResponse.from(diary);
    }

    @Transactional
    public DiaryResponse updateDiaryWithAiAssistance(Long userId, Long diaryId, DiaryAiUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new EntityNotFoundException("수정할 일기를 찾을 수 없습니다. ID: " + diaryId));

        if (!diary.getUser().getId().equals(userId)) {
            throw new SecurityException("해당 일기에 대한 수정 권한이 없습니다.");
        }

        String userWritingStyle = user.getWritingStylePrompt();
        if (!StringUtils.hasText(userWritingStyle)) {
            userWritingStyle = "오늘 있었던 일을 바탕으로 일기를 작성해줘.";
        }

        AiDiaryModifyRequestDto aiModifyRequest = new AiDiaryModifyRequestDto(
                userWritingStyle,
                request.getMarkedDiaryContent(),
                request.getUserRequest()
        );

        // AI 서버에 수정 요청
        AiDiaryResponseDto aiResponse = aiServerService.requestDiaryModification(aiModifyRequest).block();

        if (aiResponse == null || !StringUtils.hasText(aiResponse.getDiary())) {

            throw new RuntimeException("AI 서버로부터 일기 수정 내용을 받지 못했습니다. 응답 내용: " + (aiResponse != null ? aiResponse.getDiary() : "null"));
        }

        // AI 서버로부터 받은 내용으로 일기 업데이트
        diary.setContent(aiResponse.getDiary());
        if (StringUtils.hasText(aiResponse.getEmoji())) {
            diary.setEmotionIcon(aiResponse.getEmoji());
        }

        return DiaryResponse.from(diaryRepository.save(diary));
    }

    private void setExplicitRepresentativePhoto(Diary diary, Long representativePhotoId, Long userId, List<Long> currentDiaryPhotoIds) {
        DiaryPhoto repPhoto = photoRepository.findById(representativePhotoId)
                .orElseThrow(() -> new EntityNotFoundException("대표 사진으로 지정할 사진을 찾을 수 없습니다. ID: " + representativePhotoId));
        if (!repPhoto.getUserId().equals(userId)) {
            throw new SecurityException("대표 사진으로 지정할 사진에 대한 접근 권한이 없습니다.");
        }
        boolean isPhotoInDiary = diary.getDiaryPhotos().stream().anyMatch(dp -> dp.getId().equals(representativePhotoId));
        if (!isPhotoInDiary && (currentDiaryPhotoIds == null || !currentDiaryPhotoIds.contains(representativePhotoId))) {
            throw new IllegalArgumentException("선택된 대표 사진은 현재 일기에 포함된 사진이어야 합니다.");
        }
        diary.setRepresentativePhotoUrl(repPhoto.getPhotoUrl());
    }

    private void setInitialRepresentativePhoto(Diary diary) {
        if (StringUtils.hasText(diary.getRepresentativePhotoUrl())) {
            return;
        }
        if (diary.getDiaryPhotos() != null && !diary.getDiaryPhotos().isEmpty()) {
            diary.getDiaryPhotos().stream()
                    .filter(dp -> dp.getSequence() != null)
                    .min(Comparator.comparingInt(DiaryPhoto::getSequence))
                    .ifPresent(firstPhoto -> diary.setRepresentativePhotoUrl(firstPhoto.getPhotoUrl()));
        } else {
            diary.setRepresentativePhotoUrl(null);
        }
    }

    @Transactional
    public DiaryResponse setRepresentativePhoto(Long userId, Long diaryId, Long photoId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        Diary diary = diaryRepository.findByIdAndUserAndDeletedAtIsNull(diaryId, user)
                .orElseThrow(() -> new EntityNotFoundException("일기를 찾을 수 없습니다. ID: " + diaryId));

        DiaryPhoto newRepresentativePhoto = photoRepository.findById(photoId)
                .orElseThrow(() -> new EntityNotFoundException("지정할 사진을 찾을 수 없습니다. ID: " + photoId));

        if (newRepresentativePhoto.getDiary() == null || !newRepresentativePhoto.getDiary().getId().equals(diaryId) || !newRepresentativePhoto.getUserId().equals(userId)) {
            throw new IllegalArgumentException("해당 사진을 이 일기의 대표 사진으로 지정할 수 없습니다.");
        }
        diary.setRepresentativePhotoUrl(newRepresentativePhoto.getPhotoUrl());
        return DiaryResponse.from(diaryRepository.save(diary));
    }

    @Transactional
    public DiaryResponse updateDiaryPhotos(Long userId, Long diaryId, DiaryPhotosUpdateRequest request) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new EntityNotFoundException("일기를 찾을 수 없습니다. ID: " + diaryId));

        if (!diary.getUser().getId().equals(userId)) {
            throw new SecurityException("해당 일기에 대한 수정 권한이 없습니다.");
        }

        // --- 1. 요청된 사진 정보 준비 ---
        List<PhotoAssignmentDto> requestedAssignments = request.getPhotos() == null ? new ArrayList<>() : request.getPhotos();
        Set<Long> requestedPhotoIds = requestedAssignments.stream()
                .map(PhotoAssignmentDto::getPhotoId)
                .collect(Collectors.toSet());

        // --- 2. 기존 사진 정보 및 삭제 처리 ---
        List<DiaryPhoto> photosToDelete = new ArrayList<>();
        List<DiaryPhoto> currentDiaryPhotos = new ArrayList<>(diary.getDiaryPhotos()); // 복사본 사용

        String currentRepresentativePhotoUrl = diary.getRepresentativePhotoUrl();
        Long currentRepresentativePhotoId = null;

        for (DiaryPhoto existingPhoto : currentDiaryPhotos) {
            if (!requestedPhotoIds.contains(existingPhoto.getId())) {
                photosToDelete.add(existingPhoto);
                if (existingPhoto.getPhotoUrl().equals(currentRepresentativePhotoUrl)) {
                    diary.setRepresentativePhotoUrl(null); // 대표 사진이 삭제되면 null로 설정
                }
            }
            if (existingPhoto.getPhotoUrl().equals(currentRepresentativePhotoUrl)) {
                currentRepresentativePhotoId = existingPhoto.getId();
            }
        }

        // 실제 삭제 처리 (DB + S3)
        if (!photosToDelete.isEmpty()) {
            for (DiaryPhoto photo : photosToDelete) {
                log.info("S3 파일 삭제 시도: {}", photo.getPhotoUrl());
                s3Uploader.deleteFileByUrl(photo.getPhotoUrl());
            }
            diary.getDiaryPhotos().removeAll(photosToDelete);
        }

        // --- 3. 추가 및 순서 변경 처리 ---
        List<DiaryPhoto> newFinalDiaryPhotos = new ArrayList<>();
        Map<Long, DiaryPhoto> existingPhotosMap = diary.getDiaryPhotos().stream()
                .collect(Collectors.toMap(DiaryPhoto::getId, Function.identity()));

        for (PhotoAssignmentDto assignment : requestedAssignments) {
            DiaryPhoto photo = photoRepository.findById(assignment.getPhotoId())
                    .orElseThrow(() -> new EntityNotFoundException("추가하려는 사진을 찾을 수 없습니다. ID: " + assignment.getPhotoId()));

            if (!photo.getUserId().equals(userId)) {
                throw new SecurityException("다른 사용자의 사진(ID: " + photo.getId() + ")을 일기에 추가할 수 없습니다.");
            }

            // 사진이 다른 일기에 이미 속해 있는지 확인
//            if (photo.getDiary() != null && !photo.getDiary().getId().equals(diaryId)) {
//                throw new IllegalArgumentException("사진(ID: " + photo.getId() + ")은 이미 다른 일기에 속해 있습니다.");
//            }

            photo.setDiary(diary);
            photo.setSequence(assignment.getSequence());
            newFinalDiaryPhotos.add(photo);
            existingPhotosMap.remove(assignment.getPhotoId());
        }
        diary.getDiaryPhotos().clear();
        diary.getDiaryPhotos().addAll(newFinalDiaryPhotos);


        // --- 4. 새로운 대표 사진 설정 ---
        if (request.getNewRepresentativePhotoId() != null) {
            Long newRepPhotoId = request.getNewRepresentativePhotoId();
            DiaryPhoto newRepPhoto = newFinalDiaryPhotos.stream()
                    .filter(p -> p.getId().equals(newRepPhotoId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("새로운 대표 사진 ID " + newRepPhotoId + "가 최종 사진 목록에 없습니다."));
            diary.setRepresentativePhotoUrl(newRepPhoto.getPhotoUrl());
        } else if (diary.getRepresentativePhotoUrl() == null && !newFinalDiaryPhotos.isEmpty()) {
            setInitialRepresentativePhoto(diary); // sequence가 가장 낮은 사진을 대표로 설정
        }

        // --- 5. 앨범 정보 업데이트 ---
        if (albumService != null) {
            albumService.processDiaryAlbums(diary, diary.getDiaryPhotos());
        }

        Diary savedDiary = diaryRepository.save(diary);
        return DiaryResponse.from(savedDiary);
    }

    @Transactional(readOnly = true)
    public List<DiaryResponse> getFavoriteDiaries(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        List<Diary> favoriteDiaries = diaryRepository.findByUserAndIsFavoritedTrueAndDeletedAtIsNullOrderByDiaryDateDesc(user);
        return favoriteDiaries.stream().map(DiaryResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public DiaryResponse toggleDiaryFavorite(Long userId, Long diaryId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        Diary diary = diaryRepository.findByIdAndUserAndDeletedAtIsNull(diaryId, user)
                .orElseThrow(() -> new EntityNotFoundException("일기를 찾을 수 없습니다. ID: " + diaryId));
        diary.setIsFavorited(diary.getIsFavorited() == null ? true : !diary.getIsFavorited());
        return DiaryResponse.from(diaryRepository.save(diary));
    }

    @Transactional
    public DiaryResponse setDiaryFavorite(Long userId, Long diaryId, FavoriteToggleRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        Diary diary = diaryRepository.findByIdAndUserAndDeletedAtIsNull(diaryId, user)
                .orElseThrow(() -> new EntityNotFoundException("일기를 찾을 수 없습니다. ID: " + diaryId));
        diary.setIsFavorited(request.getIsFavorited());
        return DiaryResponse.from(diaryRepository.save(diary));
    }

    @Transactional(readOnly = true)
    public Page<DiaryResponse> searchDiariesByContent(Long userId, String keyword, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));

        if (!StringUtils.hasText(keyword) || keyword.trim().length() < 2) {
            log.info("사용자 ID {}의 검색어가 유효하지 않거나 너무 짧아 (두 글자 미만) 빈 결과를 반환합니다. 검색어: '{}'", userId, keyword);
            return Page.empty(pageable);
        }

        String trimmedKeyword = keyword.trim();

        Page<Diary> foundDiaries = diaryRepository.findByUserAndContentContainingAndDeletedAtIsNull(user, trimmedKeyword, pageable);
        log.info("사용자 ID {}가 키워드 '{}'로 검색하여 {}개의 일기를 찾았습니다.", userId, trimmedKeyword, foundDiaries.getTotalElements());

        return foundDiaries.map(DiaryResponse::from);
    }

    // --- 휴지통 기능 관련 메소드들 ---

    @Transactional
    public void deleteDiary(Long userId, Long diaryId) { // 소프트 삭제
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        Diary diary = diaryRepository.findByIdAndUserAndDeletedAtIsNull(diaryId, user)
                .orElseThrow(() -> new EntityNotFoundException("삭제할 일기를 찾을 수 없습니다. ID: " + diaryId));

        diary.setDeletedAt(LocalDateTime.now());
        diaryRepository.save(diary);
        log.info("일기 ID {}를 휴지통으로 이동했습니다.", diaryId);
    }

    @Transactional
    public DiaryResponse restoreDiary(Long userId, Long diaryId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        Diary diary = diaryRepository.findByIdAndUser(diaryId, user)
                .filter(d -> d.getDeletedAt() != null) // 휴지통에 있는 것만 대상으로 함
                .orElseThrow(() -> new EntityNotFoundException("휴지통에서 해당 일기를 찾을 수 없거나 이미 복원된 일기입니다. ID: " + diaryId));

        diary.setDeletedAt(null);
        return DiaryResponse.from(diaryRepository.save(diary));
    }

    @Transactional
    public void permanentlyDeleteDiary(Long userId, Long diaryId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        Diary diary = diaryRepository.findByIdAndUserAndDeletedAtIsNotNull(diaryId, user)
                .orElseThrow(() -> new EntityNotFoundException("휴지통에서 해당 일기를 찾을 수 없거나 영구 삭제할 권한이 없습니다. ID: " + diaryId));

        // 1. S3에서 사진 파일 삭제
        for (DiaryPhoto photo : diary.getDiaryPhotos()) {
            if (StringUtils.hasText(photo.getPhotoUrl())) {
                s3Uploader.deleteFileByUrl(photo.getPhotoUrl());
            }
        }
        log.info("일기 ID {}의 S3 사진 파일 삭제 완료.", diaryId);

        diaryAlbumRepository.deleteByDiary(diary);
        log.info("일기 ID {}의 DiaryAlbum 연결 삭제 완료.", diaryId);
        diaryRepository.delete(diary);
        log.info("일기 ID {} 영구 삭제 완료.", diaryId);
    }

    @Transactional
    public void emptyUserTrash(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));
        List<Diary> trashedDiaries = diaryRepository.findAllByUserAndDeletedAtIsNotNull(user);

        if (trashedDiaries.isEmpty()) {
            log.info("사용자 ID {}의 휴지통이 비어있습니다.", userId);
            return;
        }

        log.info("사용자 ID {}의 휴지통 비우기 시작. 대상 일기 수: {}", userId, trashedDiaries.size());
        for (Diary diary : trashedDiaries) {
            // S3 파일 삭제
            for (DiaryPhoto photo : diary.getDiaryPhotos()) {
                if (StringUtils.hasText(photo.getPhotoUrl())) {
                    s3Uploader.deleteFileByUrl(photo.getPhotoUrl());
                }
            }
            // DiaryAlbum 연결 삭제
            diaryAlbumRepository.deleteByDiary(diary);
        }
        // 모든 대상 Diary DB에서 한 번에 삭제
        diaryRepository.deleteAll(trashedDiaries);
        log.info("사용자 ID {}의 휴지통 비우기 완료.", userId);
    }

    @Transactional(readOnly = true)
    public Page<DiaryResponse> getTrashedDiaries(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));
        Page<Diary> trashedDiariesPage = diaryRepository.findByUserAndDeletedAtIsNotNullOrderByDeletedAtDesc(user, pageable);
        return trashedDiariesPage.map(DiaryResponse::from);
    }

    @Transactional
    public void permanentlyDeleteOldTrashedDiaries() {
        LocalDateTime cutoffDateTime = LocalDateTime.now().minusDays(30);
        List<Diary> oldTrashedDiaries = diaryRepository.findAllByDeletedAtIsNotNullAndDeletedAtBefore(cutoffDateTime);

        if (oldTrashedDiaries.isEmpty()) {
            log.info("30일이 경과하여 자동 영구 삭제할 휴지통 일기가 없습니다.");
            return;
        }
        log.info("30일 경과 휴지통 일기 {}개 자동 영구 삭제 시작...", oldTrashedDiaries.size());
        for (Diary diary : oldTrashedDiaries) {
            // S3 파일 삭제
            for (DiaryPhoto photo : diary.getDiaryPhotos()) {
                if (StringUtils.hasText(photo.getPhotoUrl())) {
                    s3Uploader.deleteFileByUrl(photo.getPhotoUrl());
                }
            }
            // DiaryAlbum 연결 삭제
            diaryAlbumRepository.deleteByDiary(diary);
        }
        // 모든 대상 Diary DB에서 한 번에 삭제
        diaryRepository.deleteAll(oldTrashedDiaries);
        log.info("30일 경과 휴지통 일기 {}개 자동 영구 삭제 완료.", oldTrashedDiaries.size());
    }
}