package com.apply.diarypic.diary.service;

import com.apply.diarypic.ai.dto.AiDiaryGenerateRequestDto;
import com.apply.diarypic.ai.dto.AiDiaryResponseDto;
import com.apply.diarypic.ai.dto.ImageInfoDto;
import com.apply.diarypic.ai.service.AiServerService;
// AlbumRepository는 AlbumService를 통해 간접적으로 사용되므로 직접 주입은 필요 없을 수 있습니다.
// import com.apply.diarypic.album.repository.AlbumRepository;
import com.apply.diarypic.album.repository.DiaryAlbumRepository;
import com.apply.diarypic.album.service.AlbumService;
import com.apply.diarypic.diary.dto.*; // 모든 DTO 임포트
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
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
    private final DiaryAlbumRepository diaryAlbumRepository; // 직접 사용

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
        // diaryRepository.save(diary)는 createAndSaveDiaryAndAlbums 내부 또는 setExplicit/Initial 이후에 호출되어야 함
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
                .status(isAiGenerated ? "미확인" : "확인") // AI 생성 일기 상태
                .diaryPhotos(new ArrayList<>())
                .build();
        Diary savedDiary = diaryRepository.save(diary); // 먼저 diary 저장하여 ID 확보

        List<DiaryPhoto> newDiaryPhotos = new ArrayList<>();
        if (finalizedPhotoPayloads != null) {
            for (AiDiaryCreateRequest.FinalizedPhotoPayload payload : finalizedPhotoPayloads) {
                DiaryPhoto diaryPhoto = photoRepository.findById(payload.getPhotoId())
                        .orElseThrow(() -> new IllegalArgumentException("저장할 사진 정보를 찾을 수 없습니다. ID: " + payload.getPhotoId()));
                if (!diaryPhoto.getUserId().equals(userId)) {
                    throw new SecurityException("해당 사진에 대한 접근 권한이 없습니다. Photo ID: " + payload.getPhotoId());
                }
                diaryPhoto.setDiary(savedDiary);
                diaryPhoto.setSequence(payload.getSequence());
                newDiaryPhotos.add(diaryPhoto); // 이 단계에서는 컬렉션에만 추가

                // 키워드 처리
                String keywordString = payload.getKeyword();
                if (StringUtils.hasText(keywordString)) {
                    Arrays.stream(keywordString.split("\\s*,\\s*"))
                            .map(String::trim).filter(s -> !s.isEmpty()).distinct()
                            .forEach(kwText -> {
                                Keyword keywordEntity = keywordRepository.findByNameAndUser(kwText, user)
                                        .orElseGet(() -> keywordRepository.save(Keyword.builder().name(kwText).user(user).build()));
                                PhotoKeywordId pkId = new PhotoKeywordId(diaryPhoto.getId(), keywordEntity.getId());
                                if (!photoKeywordRepository.existsById(pkId)) {
                                    photoKeywordRepository.save(PhotoKeyword.builder().diaryPhoto(diaryPhoto).keyword(keywordEntity).build());
                                }
                            });
                }
            }
            // photoRepository.saveAll(newDiaryPhotos); // DiaryPhoto의 변경사항(diary_id, sequence) 저장
            // Diary 엔티티의 diaryPhotos 리스트를 업데이트하고 Diary 저장 시 CascadeType.ALL로 처리됨.
            savedDiary.getDiaryPhotos().addAll(newDiaryPhotos);
        }

        // Diary 저장 후 AlbumService 호출 (DiaryPhoto가 Diary와 연결된 후)
        // savedDiary = diaryRepository.save(savedDiary); // 위에서 이미 diaryPhotos를 추가했으므로, 이 save에서 DiaryPhoto도 함께 처리될 수 있음. 또는 아래 saveAll 직후.

        if (!newDiaryPhotos.isEmpty()) {
            albumService.processDiaryAlbums(savedDiary, newDiaryPhotos);
        }
        return savedDiary; // 변경된 diaryPhotos 컬렉션이 포함된 savedDiary 반환
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

        // 검색어 길이 및 null/공백 체크
        if (!StringUtils.hasText(keyword) || keyword.trim().length() < 2) {
            log.info("사용자 ID {}의 검색어가 유효하지 않거나 너무 짧아 (두 글자 미만) 빈 결과를 반환합니다. 검색어: '{}'", userId, keyword);
            return Page.empty(pageable);
        }

        String trimmedKeyword = keyword.trim(); // 앞뒤 공백 제거

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
        // 소프트 삭제 시 앨범 연결은 AlbumService의 조회 로직에서 deletedAt으로 필터링하므로 DiaryAlbum 레코드 삭제 안 함
        diaryRepository.save(diary);
        log.info("일기 ID {}를 휴지통으로 이동했습니다.", diaryId);
    }

    @Transactional
    public DiaryResponse restoreDiary(Long userId, Long diaryId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        // 휴지통에 있는 일기를 ID와 사용자로 조회
        Diary diary = diaryRepository.findByIdAndUser(diaryId, user)
                .filter(d -> d.getDeletedAt() != null) // 휴지통에 있는 것만 대상으로 함
                .orElseThrow(() -> new EntityNotFoundException("휴지통에서 해당 일기를 찾을 수 없거나 이미 복원된 일기입니다. ID: " + diaryId));

        diary.setDeletedAt(null); // deletedAt을 null로 변경하여 복원
        // 복원 시 AlbumService.processDiaryAlbums를 다시 호출하여 앨범 관계를 재정립할 수 있으나,
        // AlbumService의 getDiariesInAlbum이 deletedAt을 필터링하므로, deletedAt을 null로 바꾸는 것만으로도
        // 앨범에 다시 보이게 됩니다. 단, 앨범 자동 생성/매핑 로직이 복원 시에도 동일하게 적용되어야 한다면 호출 고려.
        // 현재 AlbumService.processDiaryAlbums는 사진 정보 기반으로 앨범을 매핑하므로,
        // 복원 시 사진 정보 변경이 없다면 굳이 호출할 필요는 없을 수 있습니다.
        // 만약 앨범 매핑이 해제되었었다면 (소프트 삭제 시 DiaryAlbum 레코드를 지웠었다면) 여기서 재매핑 필요.
        // 현재 설계는 DiaryAlbum 레코드를 소프트 삭제 시 지우지 않으므로, deletedAt = null만으로 충분.
        return DiaryResponse.from(diaryRepository.save(diary));
    }

    @Transactional
    public void permanentlyDeleteDiary(Long userId, Long diaryId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        // 휴지통에 있는 일기인지, 그리고 사용자 소유인지 확인하여 조회
        Diary diary = diaryRepository.findByIdAndUserAndDeletedAtIsNotNull(diaryId, user)
                .orElseThrow(() -> new EntityNotFoundException("휴지통에서 해당 일기를 찾을 수 없거나 영구 삭제할 권한이 없습니다. ID: " + diaryId));

        // 1. S3에서 사진 파일 삭제
        for (DiaryPhoto photo : diary.getDiaryPhotos()) {
            if (StringUtils.hasText(photo.getPhotoUrl())) {
                s3Uploader.deleteFileByUrl(photo.getPhotoUrl());
            }
        }
        log.info("일기 ID {}의 S3 사진 파일 삭제 완료.", diaryId);

        // 2. DiaryAlbum 연결 삭제 (Diary 엔티티 삭제 시 CascadeType.REMOVE 또는 orphanRemoval=true로 자동 처리될 수 있음)
        // 명시적으로 삭제하는 것이 안전할 수 있음.
        diaryAlbumRepository.deleteByDiary(diary);
        log.info("일기 ID {}의 DiaryAlbum 연결 삭제 완료.", diaryId);

        // PhotoKeyword 연결도 DiaryPhoto의 Cascade 설정에 따라 자동으로 처리될 가능성이 높음
        // 만약 명시적으로 삭제해야 한다면 여기서 photoKeywordRepository.deleteByDiaryPhotoIn(diary.getDiaryPhotos()); 등 처리

        // 3. Diary 엔티티 삭제 (DB에서 최종 삭제)
        // Diary 엔티티에 DiaryPhoto가 CascadeType.ALL, orphanRemoval=true로 매핑되어 있다면,
        // diaryRepository.delete(diary) 호출 시 연관된 DiaryPhoto들도 함께 삭제됩니다.
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
            // permanentlyDeleteDiary(userId, diary.getId()); // 재귀적 호출 대신 내부 로직 직접 수행
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