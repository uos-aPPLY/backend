package com.apply.diarypic.diary.service;

import com.apply.diarypic.ai.dto.AiDiaryGenerateRequestDto;
import com.apply.diarypic.ai.dto.AiDiaryModifyRequestDto;
import com.apply.diarypic.ai.dto.AiDiaryResponseDto;
import com.apply.diarypic.ai.dto.ImageInfoDto;
import com.apply.diarypic.ai.service.AiServerService;
import com.apply.diarypic.album.repository.AlbumRepository;
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
import org.springframework.data.domain.Page; // Page 임포트
import org.springframework.data.domain.Pageable; // Pageable 임포트
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;


import java.time.LocalDate;
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

    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final AlbumRepository albumRepository;
    private final DiaryAlbumRepository diaryAlbumRepository;
    // private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

    /**
     * 특정 ID의 일기를 조회합니다.
     * @param userId 현재 사용자 ID
     * @param diaryId 조회할 일기의 ID
     * @return DiaryResponse DTO
     */
    @Transactional(readOnly = true)
    public DiaryResponse getDiaryById(Long userId, Long diaryId) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new EntityNotFoundException("일기를 찾을 수 없습니다. ID: " + diaryId));

        // 해당 일기가 현재 사용자의 것인지 확인
        if (!diary.getUser().getId().equals(userId)) {
            throw new SecurityException("해당 일기에 대한 접근 권한이 없습니다.");
        }
        return DiaryResponse.from(diary);
    }

    /**
     * 특정 사용자의 모든 일기 목록을 조회합니다. (페이징 적용)
     * @param userId 현재 사용자 ID
     * @param pageable 페이징 정보 (예: 최신순 정렬 - diaryDate DESC, createdAt DESC)
     * @return Page<DiaryResponse> DTO
     */
    @Transactional(readOnly = true)
    public Page<DiaryResponse> getDiariesByUser(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));

        // DiaryRepository에 findByUserOrderByDiaryDateDescCreatedAtDesc(User user, Pageable pageable) 메소드 추가 필요
        Page<Diary> diariesPage = diaryRepository.findByUserOrderByDiaryDateDescCreatedAtDesc(user, pageable);
        return diariesPage.map(DiaryResponse::from); // Page<Diary> -> Page<DiaryResponse>
    }


    // ... (createDiaryWithAiAssistance, createAndSaveDiaryAndAlbums, createDiary, deleteDiary,
    //      toggleDiaryFavorite, setDiaryFavorite, getFavoriteDiaries, setInitialRepresentativePhoto 등은 이전과 동일) ...

    @Transactional
    public DiaryResponse createDiaryWithAiAssistance(Long userId, AiDiaryCreateRequest aiDiaryCreateRequest) { // 파라미터 변경
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        String userWritingStyle = user.getWritingStylePrompt();
        if (!StringUtils.hasText(userWritingStyle)) userWritingStyle = "오늘 있었던 일을 바탕으로 일기를 작성해줘.";

        LocalDate diaryDate = aiDiaryCreateRequest.getDiaryDate();
        if (diaryDate == null) diaryDate = LocalDate.now();

        List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads = aiDiaryCreateRequest.getFinalizedPhotos();
        if (finalizedPhotoPayloads == null || finalizedPhotoPayloads.isEmpty() || finalizedPhotoPayloads.size() > 9) {
            throw new IllegalArgumentException("사진 정보가 올바르지 않습니다.");
        }

        // AI 요청 위한 ImageInfoDto 생성 (기존 로직)
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

        // 일기 및 앨범 생성 (대표 사진 ID는 이 단계에서 직접 사용하지 않음)
        Diary diary = createAndSaveDiaryAndAlbums(user, autoContent, autoEmoji, diaryDate, finalizedPhotoPayloads, userId, true);

        // 대표 사진 설정 로직
        if (aiDiaryCreateRequest.getRepresentativePhotoId() != null) {
            setExplicitRepresentativePhoto(diary, aiDiaryCreateRequest.getRepresentativePhotoId(), userId, finalizedPhotoPayloads.stream().map(AiDiaryCreateRequest.FinalizedPhotoPayload::getPhotoId).collect(Collectors.toList()));
        } else {
            setInitialRepresentativePhoto(diary); // 명시적 ID 없으면 기존 로직
        }

        return DiaryResponse.from(diaryRepository.save(diary)); // diary 저장 (대표사진 URL 업데이트 포함)
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

        if (!diaryPhotosForDiaryEntities.isEmpty()) {
            albumService.processDiaryAlbums(savedDiary, diaryPhotosForDiaryEntities);
        }

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

    // 명시적으로 대표 사진을 설정하는 헬퍼 메소드
    private void setExplicitRepresentativePhoto(Diary diary, Long representativePhotoId, Long userId, List<Long> currentDiaryPhotoIds) {
        DiaryPhoto repPhoto = photoRepository.findById(representativePhotoId)
                .orElseThrow(() -> new EntityNotFoundException("대표 사진으로 지정할 사진을 찾을 수 없습니다. ID: " + representativePhotoId));

        // 1. 해당 사진이 사용자의 사진인지 확인
        if (!repPhoto.getUserId().equals(userId)) {
            throw new SecurityException("대표 사진으로 지정할 사진에 대한 접근 권한이 없습니다.");
        }
        // 2. 해당 사진이 현재 생성되는 일기에 포함된 사진들 중 하나인지 확인
        if (!currentDiaryPhotoIds.contains(representativePhotoId)) {
            throw new IllegalArgumentException("선택된 대표 사진은 현재 일기에 포함된 사진이어야 합니다.");
        }
        // 3. DiaryPhoto 엔티티가 Diary와 연결되어 있는지 확인 (createAndSaveDiaryAndAlbums 이후 호출되므로 repPhoto.getDiary()는 이 시점에 null일 수 있음. currentDiaryPhotoIds로 체크하는 것이 더 적합)
        //    만약 repPhoto.getDiary() != null && !repPhoto.getDiary().getId().equals(diary.getId()) 로 체크하려면, DiaryPhoto가 먼저 저장되고 Diary와 연결된 후여야 함.

        diary.setRepresentativePhotoUrl(repPhoto.getPhotoUrl());
    }

    // 초기 대표 사진 설정 로직 (수정: 이미 설정된 경우 건너뛰도록)
    private void setInitialRepresentativePhoto(Diary diary) {
        // 이미 대표 사진 URL이 설정되어 있다면, 이 메소드에서는 아무 작업도 하지 않음
        if (StringUtils.hasText(diary.getRepresentativePhotoUrl())) {
            return;
        }

        if (diary.getDiaryPhotos() != null && !diary.getDiaryPhotos().isEmpty()) {
            diary.getDiaryPhotos().stream()
                    .filter(dp -> dp.getSequence() != null)
                    .min(Comparator.comparingInt(DiaryPhoto::getSequence))
                    .ifPresent(firstPhoto -> diary.setRepresentativePhotoUrl(firstPhoto.getPhotoUrl()));
        } else {
            diary.setRepresentativePhotoUrl(null); // 사진이 없을 경우 null로 설정
        }
    }

    @Transactional
    public DiaryResponse setRepresentativePhoto(Long userId, Long diaryId, Long photoId) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new EntityNotFoundException("일기를 찾을 수 없습니다. ID: " + diaryId));
        if (!diary.getUser().getId().equals(userId)) {
            throw new SecurityException("해당 일기에 대한 수정 권한이 없습니다.");
        }
        DiaryPhoto newRepresentativePhoto = photoRepository.findById(photoId)
                .orElseThrow(() -> new EntityNotFoundException("지정할 사진을 찾을 수 없습니다. ID: " + photoId));
        if (newRepresentativePhoto.getDiary() == null || !newRepresentativePhoto.getDiary().getId().equals(diaryId) || !newRepresentativePhoto.getUserId().equals(userId)) {
            throw new IllegalArgumentException("해당 사진을 이 일기의 대표 사진으로 지정할 수 없습니다.");
        }
        diary.setRepresentativePhotoUrl(newRepresentativePhoto.getPhotoUrl());
        Diary updatedDiary = diaryRepository.save(diary);
        return DiaryResponse.from(updatedDiary);
    }

    @Transactional(readOnly = true)
    public List<DiaryResponse> getFavoriteDiaries(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        List<Diary> favoriteDiaries = diaryRepository.findByUserAndIsFavoritedTrueOrderByDiaryDateDesc(user);
        if (favoriteDiaries.isEmpty()) {
            return Collections.emptyList();
        }
        return favoriteDiaries.stream()
                .map(DiaryResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public DiaryResponse toggleDiaryFavorite(Long userId, Long diaryId) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new EntityNotFoundException("일기를 찾을 수 없습니다. ID: " + diaryId));
        if (!diary.getUser().getId().equals(userId)) {
            throw new SecurityException("해당 일기에 대한 수정 권한이 없습니다.");
        }
        diary.setIsFavorited(diary.getIsFavorited() == null ? true : !diary.getIsFavorited());
        Diary updatedDiary = diaryRepository.save(diary);
        return DiaryResponse.from(updatedDiary);
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
        return DiaryResponse.from(updatedDiary);
    }

    @Transactional
    public DiaryResponse createDiary(DiaryRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        LocalDate diaryDate = request.getDiaryDate() != null ? request.getDiaryDate() : LocalDate.now();

        List<AiDiaryCreateRequest.FinalizedPhotoPayload> photoPayloadsForManualDiary = new ArrayList<>();
        List<Long> currentPhotoIdsForManualDiary = new ArrayList<>(); // 대표 사진 검증용

        if (!CollectionUtils.isEmpty(request.getPhotoIds())) {
            for (int i = 0; i < request.getPhotoIds().size(); i++) {
                Long photoId = request.getPhotoIds().get(i);
                photoPayloadsForManualDiary.add(
                        new AiDiaryCreateRequest.FinalizedPhotoPayload(photoId, "", i + 1) // 키워드는 빈 문자열, 순서는 인덱스 기반
                );
                currentPhotoIdsForManualDiary.add(photoId);
            }
        }

        Diary diary = createAndSaveDiaryAndAlbums(user, request.getContent(), request.getEmotionIcon(), diaryDate, photoPayloadsForManualDiary, userId, false);

        // 대표 사진 설정 로직
        if (request.getRepresentativePhotoId() != null) {
            setExplicitRepresentativePhoto(diary, request.getRepresentativePhotoId(), userId, currentPhotoIdsForManualDiary);
        } else {
            setInitialRepresentativePhoto(diary);
        }

        return DiaryResponse.from(diaryRepository.save(diary)); // diary 저장 (대표사진 URL 업데이트 포함)
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
            // 엔티티의 @PreUpdate가 호출되어 updatedAt이 자동으로 갱신됩니다.
            return DiaryResponse.from(diaryRepository.save(diary));
        }
        // 변경 사항이 없으면 기존 일기 정보를 그대로 반환 (혹은 다른 방식으로 처리 가능)
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
            // 기본 글쓰기 스타일 설정 (일기 생성 시와 동일하게)
            userWritingStyle = "오늘 있었던 일을 바탕으로 일기를 작성해줘.";
        }

        AiDiaryModifyRequestDto aiModifyRequest = new AiDiaryModifyRequestDto(
                userWritingStyle,
                request.getMarkedDiaryContent(),
                request.getUserRequest()
        );

        // AI 서버에 수정 요청
        AiDiaryResponseDto aiResponse = aiServerService.requestDiaryModification(aiModifyRequest).block(); // WebClient는 기본적으로 비동기. 필요에 따라 block() 또는 subscribe() 사용

        if (aiResponse == null || !StringUtils.hasText(aiResponse.getDiary())) {
            // AI 서버에서 응답이 없거나, diary 내용이 비어있는 경우 예외 처리
            // AiServerService의 onErrorResume에서 이미 기본 메시지를 포함한 DTO를 반환하도록 설정했으므로,
            // 여기서는 해당 메시지를 그대로 사용하거나, 좀 더 구체적인 예외를 발생시킬 수 있습니다.
            throw new RuntimeException("AI 서버로부터 일기 수정 내용을 받지 못했습니다. 응답 내용: " + (aiResponse != null ? aiResponse.getDiary() : "null"));
        }

        // AI 서버로부터 받은 내용으로 일기 업데이트
        diary.setContent(aiResponse.getDiary());
        if (StringUtils.hasText(aiResponse.getEmoji())) { // emoji는 선택적으로 올 수 있으므로 null 체크
            diary.setEmotionIcon(aiResponse.getEmoji());
        }
        // 사진 관련 정보는 수정하지 않음

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

        // 현재 대표 사진 ID 찾기 (URL 기반 비교는 정확하지 않을 수 있으므로, ID를 찾도록 개선 가능하면 좋음)
        // 여기서는 URL로 비교하지만, DiaryPhoto에 대표 사진 여부 플래그가 있거나,
        // Diary 엔티티가 DiaryPhoto 객체를 직접 참조하면 더 명확합니다.
        // 현재는 Diary.representativePhotoUrl 필드만 사용합니다.

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
                // S3에서 실제 파일 삭제 (S3Uploader에 해당 기능 구현 필요)
                // 예: s3Uploader.deleteFile(photo.getPhotoUrl()); // URL에서 파일 키를 추출하는 로직 필요
                log.info("S3 파일 삭제 시도: {}", photo.getPhotoUrl()); // 실제 삭제 로직은 S3Uploader에 구현
//                s3Uploader.deleteFileByUrl(photo.getPhotoUrl());


                // PhotoKeyword는 DiaryPhoto의 cascade에 의해 자동 삭제될 수 있지만, 명시적 처리도 고려
                // photoKeywordRepository.deleteAll(photo.getPhotoKeywords());
                // photo.getPhotoKeywords().clear(); // 컬렉션 비우기
            }
            diary.getDiaryPhotos().removeAll(photosToDelete); // orphanRemoval=true에 의해 DB에서 삭제
            // photoRepository.deleteAll(photosToDelete); // 직접 삭제도 가능
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

            // 사진이 다른 일기에 이미 속해 있는지 확인 (선택적 로직)
            if (photo.getDiary() != null && !photo.getDiary().getId().equals(diaryId)) {
                throw new IllegalArgumentException("사진(ID: " + photo.getId() + ")은 이미 다른 일기에 속해 있습니다.");
            }

            photo.setDiary(diary);
            photo.setSequence(assignment.getSequence());
            newFinalDiaryPhotos.add(photo);
            existingPhotosMap.remove(assignment.getPhotoId()); // 처리된 사진은 맵에서 제거
        }

        // 요청에는 없지만 기존 diary.getDiaryPhotos()에 남아있던 사진들은 삭제되었어야 함 (위 photosToDelete 로직에서 처리)
        // 따라서 newFinalDiaryPhotos가 최종 목록이 됨
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
            // 기존 대표사진이 삭제되었고, 새 대표사진 지정이 없으며, 사진이 남아있다면 첫번째 사진을 대표로 (정책에 따라 다름)
            // 또는 setInitialRepresentativePhoto(diary) 호출 (현재는 이 메소드가 sequence 기반으로 설정)
            setInitialRepresentativePhoto(diary); // sequence가 가장 낮은 사진을 대표로 설정
        }


        // --- 5. 앨범 정보 업데이트 ---
        // 사진 목록이 변경되었으므로, 연관된 앨범 정보도 업데이트 필요 (AlbumService에 위임)
        if (albumService != null) { // albumService가 주입되었는지 확인
            albumService.processDiaryAlbums(diary, diary.getDiaryPhotos());
        }


        Diary savedDiary = diaryRepository.save(diary);
        return DiaryResponse.from(savedDiary);
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

        diaryAlbumRepository.deleteByDiary(diary);
        diaryRepository.delete(diary);
        log.info("일기 ID {} 삭제 완료.", diaryId);
    }
}