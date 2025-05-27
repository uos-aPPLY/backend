package com.apply.diarypic.diary.service;

import com.apply.diarypic.ai.dto.AiDiaryGenerateRequestDto;
import com.apply.diarypic.ai.dto.AiDiaryModifyRequestDto;
import com.apply.diarypic.ai.dto.AiDiaryResponseDto;
import com.apply.diarypic.ai.dto.ImageInfoDto;
import com.apply.diarypic.ai.service.AiServerService;
import com.apply.diarypic.album.entity.Album;
import com.apply.diarypic.album.entity.DiaryAlbum;
import com.apply.diarypic.album.repository.DiaryAlbumRepository;
import com.apply.diarypic.album.service.AlbumService;
import com.apply.diarypic.diary.dto.*;
import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.global.geocoding.GeocodingService;
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
import org.springframework.scheduling.annotation.Async;
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
    private final GeocodingService geocodingService;

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
    public List<CalendarDiaryInfoResponse> getDiariesForCalendar(Long userId, int year, int month) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));

        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("월(month)은 1에서 12 사이의 값이어야 합니다.");
        }

        List<Diary> diaries = diaryRepository.findAllByUserAndYearAndMonthAndDeletedAtIsNullOrderByDiaryDateAsc(user, year, month);

        return diaries.stream()
                .map(CalendarDiaryInfoResponse::from)
                .collect(Collectors.toList());
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
        List<Long> currentPhotoIds = new ArrayList<>(); // 대표 사진 검증용

        if (!CollectionUtils.isEmpty(request.getPhotoIds())) {
            for (int i = 0; i < request.getPhotoIds().size(); i++) {
                Long photoId = request.getPhotoIds().get(i);
                photoPayloads.add(new AiDiaryCreateRequest.FinalizedPhotoPayload(photoId, "", i + 1));
                currentPhotoIds.add(photoId);
            }
        }

        // 1. Diary 및 DiaryPhoto 기본 연결 및 저장 (대표사진 URL은 아직 미설정)
        Diary diaryInProgress = createAndSaveDiaryAndAlbums(user, request.getContent(), request.getEmotionIcon(), diaryDate, photoPayloads, userId, false);
        // 이 시점의 diaryInProgress.getDiaryPhotos()는 채워져 있어야 함.

        // 2. 대표 사진 설정
        if (request.getRepresentativePhotoId() != null) {
            setExplicitRepresentativePhoto(diaryInProgress, request.getRepresentativePhotoId(), userId, currentPhotoIds);
        } else {
            setInitialRepresentativePhoto(diaryInProgress); // diaryInProgress.getDiaryPhotos() 사용
        }

        // 3. 대표 사진 URL까지 포함하여 최종 저장 및 DB와 즉시 동기화
        Diary fullySavedDiary = diaryRepository.saveAndFlush(diaryInProgress);
        log.debug("Diary ID {} 최종 저장 (대표사진 포함) 및 플러시 완료. 대표사진 URL: {}", fullySavedDiary.getId(), fullySavedDiary.getRepresentativePhotoUrl());

        // 4. 앨범 처리 (모든 정보가 DB에 저장된 후)
        albumService.processDiaryAlbums(fullySavedDiary, new ArrayList<>(fullySavedDiary.getDiaryPhotos()));
        log.debug("Diary ID {} 앨범 처리 완료.", fullySavedDiary.getId());

        // 5. 최종적으로 DB에서 다시 조회하여 응답 생성 (가장 확실한 방법)
        //    또는 fullySavedDiary를 사용해도 되지만, lazy-loaded 컬렉션 등을 고려하면 재조회가 안전.
        Diary finalDiaryForResponse = diaryRepository.findById(fullySavedDiary.getId())
                .orElseThrow(() -> new EntityNotFoundException("저장된 일기를 찾을 수 없습니다. ID: " + fullySavedDiary.getId()));
        return DiaryResponse.from(finalDiaryForResponse);
    }

//    @Transactional
//    public DiaryResponse createDiaryWithAiAssistance(Long userId, AiDiaryCreateRequest aiDiaryCreateRequest) {
//        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
//        String userWritingStyle = user.getWritingStylePrompt();
//        if (!StringUtils.hasText(userWritingStyle)) userWritingStyle = "오늘 있었던 일을 바탕으로 일기를 작성해줘.";
//
//        LocalDate diaryDate = aiDiaryCreateRequest.getDiaryDate();
//        if (diaryDate == null) diaryDate = LocalDate.now();
//
//        List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads = aiDiaryCreateRequest.getFinalizedPhotos();
//        if (finalizedPhotoPayloads == null || finalizedPhotoPayloads.isEmpty() || finalizedPhotoPayloads.size() > 9) {
//            throw new IllegalArgumentException("사진 정보가 올바르지 않습니다.");
//        }
//
//        List<ImageInfoDto> imageInfoForAi = finalizedPhotoPayloads.stream()
//                .map(payload -> {
//                    DiaryPhoto diaryPhoto = photoRepository.findById(payload.getPhotoId()).orElseThrow(() -> new IllegalArgumentException("Photo not found: " + payload.getPhotoId()));
//                    if (!diaryPhoto.getUserId().equals(userId)) throw new SecurityException("Photo access denied: " + payload.getPhotoId());
//                    String combinedAddress = Stream.of(diaryPhoto.getLocality(), diaryPhoto.getAdminAreaLevel1(), diaryPhoto.getCountryName())
//                            .filter(StringUtils::hasText).collect(Collectors.joining(", "));
//                    return new ImageInfoDto(diaryPhoto.getPhotoUrl(), diaryPhoto.getShootingDateTime() != null ? diaryPhoto.getShootingDateTime().format(ISO_LOCAL_DATE_TIME_FORMATTER) : null,
//                            StringUtils.hasText(combinedAddress) ? combinedAddress : null, payload.getKeyword(), payload.getSequence());
//                }).collect(Collectors.toList());
//
//        AiDiaryGenerateRequestDto aiRequest = new AiDiaryGenerateRequestDto(userWritingStyle, imageInfoForAi);
//        AiDiaryResponseDto aiResponse = aiServerService.requestDiaryGeneration(aiRequest).block();
//
//        if (aiResponse == null || !StringUtils.hasText(aiResponse.getDiary())) {
//            throw new RuntimeException("AI 서버로부터 일기 내용을 생성하지 못했습니다.");
//        }
//        String autoContent = aiResponse.getDiary();
//        String autoEmoji = aiResponse.getEmoji();
//
//        Diary diary = createAndSaveDiaryAndAlbums(user, autoContent, autoEmoji, diaryDate, finalizedPhotoPayloads, userId, true);
//
//        if (aiDiaryCreateRequest.getRepresentativePhotoId() != null) {
//            setExplicitRepresentativePhoto(diary, aiDiaryCreateRequest.getRepresentativePhotoId(), userId, finalizedPhotoPayloads.stream().map(AiDiaryCreateRequest.FinalizedPhotoPayload::getPhotoId).collect(Collectors.toList()));
//        } else {
//            setInitialRepresentativePhoto(diary);
//        }
//        return DiaryResponse.from(diaryRepository.save(diary));
//    }

    @Transactional
    public DiaryResponse requestAiDiaryCreation(Long userId, AiDiaryCreateRequest aiDiaryCreateRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        LocalDate diaryDate = aiDiaryCreateRequest.getDiaryDate();
        if (diaryDate == null) {
            diaryDate = LocalDate.now();
        }

        // 1. 해당 날짜에 이미 일기가 있는지 (생성 중 포함) 확인
        Optional<Diary> existingDiaryOpt = diaryRepository.findByUserAndDiaryDateAndDeletedAtIsNull(user, diaryDate);
        if (existingDiaryOpt.isPresent()) {
            Diary existingDiary = existingDiaryOpt.get();
            if ("generating".equalsIgnoreCase(existingDiary.getStatus())) {
                throw new IllegalStateException("해당 날짜(" + diaryDate + ")의 일기가 이미 생성 중입니다.");
            }
            // 이미 생성 완료된 일기가 있다면, 그 일기 정보를 반환하거나 예외 발생
            // 여기서는 예외 발생으로 처리 (클라이언트가 /status API로 먼저 확인하는 것을 권장)
            throw new IllegalArgumentException("해당 날짜(" + diaryDate + ")에 이미 일기가 존재합니다. ID: " + existingDiary.getId());
        }

        // 2. 사진 정보 유효성 검사
        List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads = aiDiaryCreateRequest.getFinalizedPhotos();
        if (finalizedPhotoPayloads == null || finalizedPhotoPayloads.isEmpty() || finalizedPhotoPayloads.size() > 9) {
            throw new IllegalArgumentException("AI 일기 생성을 위한 사진 정보가 올바르지 않습니다 (1~9장 필요).");
        }

        // 3. "generating" 상태로 Diary 임시 저장 (사진 연결은 아직 안 함)
        Diary generatingDiaryEntity = Diary.builder()
                .user(user)
                .diaryDate(diaryDate)
                .status("generating") // 생성 중 상태 (소문자)
                .content("") // NOT NULL 제약 회피용 초기값
                .emotionIcon("smile")
                .isFavorited(false)
                .diaryPhotos(new ArrayList<>()) // 초기 빈 리스트
                .build();
        Diary savedGeneratingDiary = diaryRepository.save(generatingDiaryEntity); // ID 확보
        log.info("일기 ID {} (날짜: {}) 'generating' 상태로 임시 저장됨.", savedGeneratingDiary.getId(), diaryDate);

        // 4. DiaryPhoto 엔티티들과 임시 저장된 Diary 연결 (DB에 반영은 아직 안 함, 컬렉션에만 추가)
        // 이 단계에서 DiaryPhoto들은 이미 PhotoService.uploadPhotosWithMetadata를 통해
        // S3 URL과 (Geocoding된) 주소 정보가 저장되어 있다고 가정.
        List<DiaryPhoto> photosToConnect = new ArrayList<>();
        for (AiDiaryCreateRequest.FinalizedPhotoPayload payload : finalizedPhotoPayloads) {
            DiaryPhoto diaryPhoto = photoRepository.findById(payload.getPhotoId())
                    .orElseThrow(() -> new IllegalArgumentException("요청된 사진을 찾을 수 없습니다. Photo ID: " + payload.getPhotoId()));
            if (!diaryPhoto.getUserId().equals(userId)) {
                throw new SecurityException("Photo access denied: " + payload.getPhotoId());
            }
            // 이 시점에는 DiaryPhoto에 setDiary, setSequence를 하지 않고,
            // 비동기 작업인 processAiDiaryGeneration에서 Diary와 최종 연결 및 저장합니다.
            // generatingDiary 객체를 반환할 때 photo 정보를 포함시키기 위해 임시로 연결된 것처럼 처리할 수도 있지만,
            // 실제 DB 연결은 비동기 작업에서 하는 것이 더 명확합니다.
            // 여기서는 finalizedPhotoPayloads를 그대로 비동기 메소드에 전달합니다.
        }


        // 5. 비동기 AI 처리 호출
        // aiDiaryCreateRequest는 대표사진 ID도 포함할 수 있으므로 그대로 전달
        processAiDiaryGeneration(user, savedGeneratingDiary.getId(), aiDiaryCreateRequest);

        // 6. 클라이언트에는 "generating" 상태의 DiaryResponse 즉시 반환
        // 이때 photos 리스트는 비어있거나, 아직 DB에 연결되지 않은 임시 정보일 수 있습니다.
        // 클라이언트가 /status API로 최종 상태를 확인하도록 유도.
        // DiaryResponse.from()이 DB에서 diaryPhotos를 lazy loading 한다면,
        // savedGeneratingDiary에는 아직 photo 연결이 없을 수 있으므로 photos는 비어있게 나옴.
        return DiaryResponse.from(savedGeneratingDiary);
    }

//    @Transactional
//    public DiaryResponse requestAiDiaryModification(Long userId, Long diaryId, DiaryAiUpdateRequest request) {
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));
//        Diary diary = diaryRepository.findByIdAndUserAndDeletedAtIsNull(diaryId, user)
//                .orElseThrow(() -> new EntityNotFoundException("수정할 일기를 찾을 수 없습니다. ID: " + diaryId));
//
//        if ("generating".equalsIgnoreCase(diary.getStatus())) {
//            throw new IllegalStateException("해당 일기는 현재 AI 처리 중입니다. 잠시 후 다시 시도해주세요.");
//        }
//
//        String originalStatus = diary.getStatus();
//        diary.setStatus("generating");
//        Diary generatingDiary = diaryRepository.save(diary);
//        log.info("일기 ID {} 'generating' (for modification) 상태로 업데이트됨.", diaryId);
//
//        processAiDiaryModification(user, generatingDiary.getId(), request, originalStatus);
//
//        // Presigned URL을 사용하지 않으므로 S3Uploader와 만료시간 전달 불필요
//        return DiaryResponse.from(generatingDiary);
//    }


    @Async("diaryAiTaskExecutor") // AI 작업용 별도 스레드 풀 사용 (AsyncConfig에 정의 필요)
    @Transactional
    public void processAiDiaryGeneration(User user, Long diaryId, AiDiaryCreateRequest aiDiaryCreateRequest) {
        // aiDiaryCreateRequest에서 finalizedPhotoPayloads와 representativePhotoId를 가져옴
        List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads = aiDiaryCreateRequest.getFinalizedPhotos();

        log.info("비동기 AI 일기 생성 시작: Diary ID {}", diaryId);
        // @Transactional이므로, diaryId로 다시 조회하여 영속성 컨텍스트 내 엔티티로 작업
        Diary diaryToUpdate = diaryRepository.findById(diaryId).orElse(null);

        if (diaryToUpdate == null || !"generating".equalsIgnoreCase(diaryToUpdate.getStatus())) {
            log.warn("비동기 AI 일기 생성 중단: Diary ID {}를 찾을 수 없거나 상태가 'generating'이 아님 (현재 상태: {}).",
                    diaryId, diaryToUpdate != null ? diaryToUpdate.getStatus() : "null");
            return;
        }

        try {
            // 1. DiaryPhoto 엔티티들과 Diary 연결 및 저장 (이 시점에서 DB에 DiaryPhoto의 diary_id, sequence 업데이트)
            List<DiaryPhoto> actualDiaryPhotos = new ArrayList<>();
            if (finalizedPhotoPayloads != null) {
                finalizedPhotoPayloads.sort(Comparator.comparingInt(AiDiaryCreateRequest.FinalizedPhotoPayload::getSequence));
                for (AiDiaryCreateRequest.FinalizedPhotoPayload payload : finalizedPhotoPayloads) {
                    DiaryPhoto diaryPhoto = photoRepository.findById(payload.getPhotoId())
                            .orElseThrow(() -> {
                                log.error("비동기 처리 중 사진 못찾음 Photo ID: {}", payload.getPhotoId());
                                return new EntityNotFoundException("AI 일기 생성 중 사진을 찾을 수 없습니다. Photo ID: " + payload.getPhotoId());
                            });
                    // Geocoding은 PhotoService.uploadPhotosWithMetadata에서 이미 수행되었다고 가정
                    diaryPhoto.setDiary(diaryToUpdate);
                    diaryPhoto.setSequence(payload.getSequence());
                    actualDiaryPhotos.add(diaryPhoto);
                }
                diaryToUpdate.getDiaryPhotos().clear();
                diaryToUpdate.getDiaryPhotos().addAll(actualDiaryPhotos);
                // photoRepository.saveAll(actualDiaryPhotos); // 또는 Diary 저장 시 Cascade
            }
            // Diary와 DiaryPhoto 연결 정보를 먼저 저장 (대표 사진 설정 및 AI 요청 전에 사진 정보 확정)
            diaryRepository.saveAndFlush(diaryToUpdate); // diary_id, sequence 업데이트 즉시 반영
            log.info("Diary ID {}에 사진 {}개 연결 완료 (비동기).", diaryId, actualDiaryPhotos.size());


            // 2. AI 서버 요청 DTO 생성 (ImageInfoDto에 Public URL 사용)
            String userWritingStyle = user.getWritingStylePrompt();
            if (!StringUtils.hasText(userWritingStyle)) userWritingStyle = "오늘 있었던 일을 바탕으로 일기를 작성해줘.";

            List<ImageInfoDto> imageInfoForAi = actualDiaryPhotos.stream() // 이제 diaryToUpdate.getDiaryPhotos() 사용 가능
                    .map(diaryPhoto -> {
                        String publicImageUrl = diaryPhoto.getPhotoUrl(); // Public URL
                        String combinedAddress = Stream.of(diaryPhoto.getLocality(), diaryPhoto.getAdminAreaLevel1(), diaryPhoto.getCountryName())
                                .filter(StringUtils::hasText).collect(Collectors.joining(", "));
                        return new ImageInfoDto(publicImageUrl,
                                diaryPhoto.getShootingDateTime() != null ? diaryPhoto.getShootingDateTime().format(ISO_LOCAL_DATE_TIME_FORMATTER) : null,
                                StringUtils.hasText(combinedAddress) ? combinedAddress : null,
                                finalizedPhotoPayloads.stream() // payload에서 keyword 가져오기
                                        .filter(p -> p.getPhotoId().equals(diaryPhoto.getId()))
                                        .findFirst().map(AiDiaryCreateRequest.FinalizedPhotoPayload::getKeyword).orElse(""),
                                diaryPhoto.getSequence());
                    }).collect(Collectors.toList());

            AiDiaryGenerateRequestDto aiRequest = new AiDiaryGenerateRequestDto(userWritingStyle, imageInfoForAi);

            // 3. AI 서버에 일기 생성 요청 및 결과 처리
            aiServerService.requestDiaryGeneration(aiRequest)
                    .subscribe(aiResponse -> {
                        // subscribe 콜백 내에서는 트랜잭션이 다를 수 있으므로, 필요시 diary를 다시 조회하거나 새로운 트랜잭션으로 처리
                        // 현재 processAiDiaryGeneration 메소드가 @Transactional이므로, 여기서 diaryToUpdate를 수정하면 됨.
                        Diary diaryToFinalize = diaryRepository.findById(diaryId).orElse(null);
                        if (diaryToFinalize == null) { // 그 사이에 삭제되었을 가능성 (매우 희박)
                            log.warn("AI 응답 후 Diary ID {}를 찾을 수 없음.", diaryId);
                            return;
                        }

                        if (aiResponse != null && StringUtils.hasText(aiResponse.getDiary())) {
                            diaryToFinalize.setContent(aiResponse.getDiary());
                            diaryToFinalize.setEmotionIcon(aiResponse.getEmoji());
                            diaryToFinalize.setStatus("unconfirmed"); // AI 처리 완료

                            // 4. 대표 사진 설정 (AI 내용 업데이트 후)
                            if (aiDiaryCreateRequest.getRepresentativePhotoId() != null) {
                                setExplicitRepresentativePhoto(diaryToFinalize, aiDiaryCreateRequest.getRepresentativePhotoId(), user.getId(),
                                        actualDiaryPhotos.stream().map(DiaryPhoto::getId).collect(Collectors.toList())
                                );
                            } else {
                                setInitialRepresentativePhoto(diaryToFinalize); // diaryToFinalize.getDiaryPhotos() 사용
                            }

                            Diary finalSavedDiary = diaryRepository.save(diaryToFinalize); // 모든 정보 최종 저장
                            log.info("비동기 AI 일기 생성 DB 최종 저장 완료: Diary ID {}", diaryId);

                            // 5. 앨범 처리 (모든 정보가 DB에 저장된 후)
                            albumService.processDiaryAlbums(finalSavedDiary, new ArrayList<>(finalSavedDiary.getDiaryPhotos()));
                            log.info("Diary ID {} (AI생성) 앨범 처리 완료.", diaryId);

                        } else {
                            log.error("비동기 AI 일기 생성 실패 (AI 응답 없음): Diary ID {}", diaryId);
                            diaryToFinalize.setStatus("failed");
                            diaryToFinalize.setContent((diaryToFinalize.getContent() == null ? "" : diaryToFinalize.getContent()) + "\n[AI 생성 실패: 응답 없음]");
                            diaryRepository.save(diaryToFinalize);
                        }
                    }, error -> { // 구독 오류 처리
                        log.error("비동기 AI 일기 생성 중 구독 오류: Diary ID {}. Error: {}", diaryId, error.getMessage(), error);
                        Diary diaryOnError = diaryRepository.findById(diaryId).orElse(null);
                        if (diaryOnError != null && "generating".equalsIgnoreCase(diaryOnError.getStatus())) {
                            diaryOnError.setStatus("failed");
                            diaryOnError.setContent((diaryOnError.getContent() == null ? "" : diaryOnError.getContent()) + "\n[AI 생성 실패: " + error.getMessage() + "]");
                            diaryRepository.save(diaryOnError);
                        }
                    });

        } catch (Exception e) { // processAiDiaryGeneration 메소드 전체의 예외 처리
            log.error("비동기 AI 일기 생성 로직 실행 중 예외: Diary ID {}. Error: {}", diaryId, e.getMessage(), e);
            // 이 시점에 diaryToUpdate가 null이 아님은 위에서 보장됨
            if ("generating".equalsIgnoreCase(diaryToUpdate.getStatus())) {
                diaryToUpdate.setStatus("failed");
                diaryToUpdate.setContent((diaryToUpdate.getContent() == null ? "" : diaryToUpdate.getContent()) + "\n[AI 생성 준비 중 시스템 오류]");
                diaryRepository.save(diaryToUpdate);
            }
        }
    }


//    @Async
//    @Transactional
//    public void processAiDiaryModification(User user, Long diaryId, DiaryAiUpdateRequest request, String originalStatus) {
//        log.info("비동기 AI 일기 수정 시작: Diary ID {}", diaryId);
//        Diary diaryToUpdate = diaryRepository.findById(diaryId).orElse(null);
//
//        if (diaryToUpdate == null || !"generating".equalsIgnoreCase(diaryToUpdate.getStatus())) {
//            log.warn("비동기 AI 일기 수정 중단: Diary ID {}를 찾을 수 없거나 상태가 'generating'이 아님 (현재 상태: {}).", diaryId, diaryToUpdate != null ? diaryToUpdate.getStatus() : "null");
//            // 원래 상태로 복구 시도 (선택적)
//            if (diaryToUpdate != null && StringUtils.hasText(originalStatus)) {
//                diaryToUpdate.setStatus(originalStatus);
//                diaryRepository.save(diaryToUpdate);
//            }
//            return;
//        }
//
//        try {
//            String userWritingStyle = user.getWritingStylePrompt();
//            if (!StringUtils.hasText(userWritingStyle)) {
//                userWritingStyle = "오늘 있었던 일을 바탕으로 일기를 작성해줘.";
//            }
//
//            AiDiaryModifyRequestDto aiModifyRequest = new AiDiaryModifyRequestDto(
//                    userWritingStyle,
//                    request.getMarkedDiaryContent(),
//                    request.getUserRequest()
//            );
//
//            aiServerService.requestDiaryModification(aiModifyRequest)
//                    .subscribe(aiResponse -> {
//                        if (aiResponse != null && StringUtils.hasText(aiResponse.getDiary())) {
//                            diaryToUpdate.setContent(aiResponse.getDiary());
//                            if (StringUtils.hasText(aiResponse.getEmoji())) {
//                                diaryToUpdate.setEmotionIcon(aiResponse.getEmoji());
//                            }
//                            diaryToUpdate.setStatus("unconfirmed"); // 또는 originalStatus (사용자가 확인했었다면)
//                            diaryRepository.save(diaryToUpdate);
//                            log.info("비동기 AI 일기 수정 완료: Diary ID {}", diaryId);
//                        } else {
//                            log.error("비동기 AI 일기 수정 실패: AI 서버로부터 유효한 응답을 받지 못함. Diary ID {}", diaryId);
//                            diaryToUpdate.setStatus(originalStatus); // 실패 시 원래 상태로 롤백
//                            diaryRepository.save(diaryToUpdate);
//                        }
//                    }, error -> {
//                        log.error("비동기 AI 일기 수정 중 오류 발생: Diary ID {}. Error: {}", diaryId, error.getMessage());
//                        Diary diaryOnError = diaryRepository.findById(diaryId).orElse(null);
//                        if (diaryOnError != null) {
//                            diaryOnError.setStatus(originalStatus); // 실패 시 원래 상태로 롤백
//                            // diaryOnError.setContent(diaryOnError.getContent() + "\n\n[AI 수정 실패: " + error.getMessage() + "]");
//                            diaryRepository.save(diaryOnError);
//                        }
//                    });
//        } catch (Exception e) {
//            log.error("비동기 AI 일기 수정 로직 실행 중 예외 발생: Diary ID {}. Error: {}", diaryId, e.getMessage(), e);
//            diaryToUpdate.setStatus(originalStatus);
//            // diaryToUpdate.setContent(diaryToUpdate.getContent() + "\n\n[AI 수정 준비 중 오류: " + e.getMessage() + "]");
//            diaryRepository.save(diaryToUpdate);
//        }
//    }

    @Transactional(readOnly = true)
    public AiDiaryResponseDto suggestAiModification(Long userId, Long diaryId, DiaryAiUpdateRequest clientRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));
        Diary diary = diaryRepository.findByIdAndUserAndDeletedAtIsNull(diaryId, user)
                .orElseThrow(() -> new EntityNotFoundException("수정 제안을 요청할 일기를 찾을 수 없습니다. ID: " + diaryId));

        // 사용자의 글쓰기 스타일 가져오기
        String userWritingStyle = user.getWritingStylePrompt();

        AiDiaryModifyRequestDto aiModifyRequest = new AiDiaryModifyRequestDto(
                userWritingStyle,
                clientRequest.getMarkedDiaryContent(),
                clientRequest.getUserRequest()
        );

        log.info("AI 서버에 일기 수정 제안 요청: Diary ID {}", diaryId);
        // AI 서버에 수정 요청 (동기적으로 결과 대기)
        AiDiaryResponseDto aiSuggestion = aiServerService.requestDiaryModification(aiModifyRequest).block();

        if (aiSuggestion == null || !StringUtils.hasText(aiSuggestion.getDiary())) {
            log.error("AI 서버로부터 일기 수정 제안을 받지 못했습니다. Diary ID: {}", diaryId);
            return new AiDiaryResponseDto("AI 수정 제안을 생성하는 데 실패했습니다. 원본 내용을 확인해주세요.", diary.getEmotionIcon());
        }

        log.info("AI 서버로부터 일기 수정 제안 수신 완료: Diary ID {}", diaryId);
        return aiSuggestion; // AI가 제안한 내용과 이모티콘을 그대로 반환
    }

    // --- 일기 상태 확인용 서비스 메소드 ---
    @Transactional(readOnly = true)
    public DiaryStatusResponse getDiaryStatusByDate(Long userId, LocalDate date) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));
        Optional<Diary> diaryOpt = diaryRepository.findByUserAndDiaryDateAndDeletedAtIsNull(user, date);

        if (diaryOpt.isEmpty()) {
            return new DiaryStatusResponse(date.toString(), "none", null);
        }
        Diary diary = diaryOpt.get();
        String status = diary.getStatus() != null ? diary.getStatus().toLowerCase() : "unconfirmed"; // 기본값 또는 null 처리
        if ("generating".equals(status)) {
            return new DiaryStatusResponse(date.toString(), "generating", diary.getId());
        }
        return new DiaryStatusResponse(date.toString(), "exists", diary.getId());
    }

    // 일기 "확인" (Confirm) 서비스 메소드
    @Transactional
    public DiaryResponse confirmDiary(Long userId, Long diaryId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));
        Diary diary = diaryRepository.findByIdAndUserAndDeletedAtIsNull(diaryId, user)
                .orElseThrow(() -> new EntityNotFoundException("확인할 일기를 찾을 수 없습니다. ID: " + diaryId));

        if ("generating".equalsIgnoreCase(diary.getStatus())) {
            throw new IllegalStateException("아직 생성 중인 일기입니다. 잠시 후 다시 시도해주세요.");
        }

        diary.setStatus("confirmed");
        Diary confirmedDiary = diaryRepository.save(diary);
        log.info("일기 ID {}의 상태가 'confirmed'로 업데이트되었습니다.", diaryId);
        return DiaryResponse.from(confirmedDiary);
    }

    private Diary createAndSaveDiaryAndAlbums(User user, String content, String emoji, LocalDate diaryDate, List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads, Long userId, boolean isAiGenerated) {
        // 1. Diary 기본 정보로 먼저 저장 (ID 확보)
        Diary diary = Diary.builder()
                .user(user)
                .content(content) // AI 생성 전이라면 content는 "" 또는 임시값
                .emotionIcon(emoji) // AI 생성 전이라면 emoji는 null 또는 임시값
                .diaryDate(diaryDate)
                .isFavorited(false)
                .status(isAiGenerated ? "generating" : "confirmed") // 생성 시점의 상태
                .diaryPhotos(new ArrayList<>())
                .build();
        Diary savedDiaryOnlyBaseInfo = diaryRepository.save(diary); // 1차 저장 (Diary ID 생성)
        log.debug("1차 저장된 Diary ID: {}", savedDiaryOnlyBaseInfo.getId());

        List<DiaryPhoto> finalDiaryPhotos = new ArrayList<>();
        if (finalizedPhotoPayloads != null && !finalizedPhotoPayloads.isEmpty()) {
            finalizedPhotoPayloads.sort(Comparator.comparingInt(AiDiaryCreateRequest.FinalizedPhotoPayload::getSequence));
            for (AiDiaryCreateRequest.FinalizedPhotoPayload payload : finalizedPhotoPayloads) {
                DiaryPhoto diaryPhoto = photoRepository.findById(payload.getPhotoId())
                        .orElseThrow(() -> new IllegalArgumentException("저장할 사진 정보를 찾을 수 없습니다. ID: " + payload.getPhotoId()));

                if (!diaryPhoto.getUserId().equals(userId)) {
                    throw new SecurityException("해당 사진에 대한 접근 권한이 없습니다. Photo ID: " + payload.getPhotoId());
                }

                diaryPhoto.setDiary(savedDiaryOnlyBaseInfo); // 생성된 Diary와 연결
                diaryPhoto.setSequence(payload.getSequence());
                // Geocoding은 PhotoService.upload 시 이미 완료되었다고 가정 (또는 여기서 필요시 수행)

                // photoRepository.save(diaryPhoto); // 여기서 개별 저장 또는 아래서 Diary 통해 Cascade
                finalDiaryPhotos.add(diaryPhoto);

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
                savedDiaryOnlyBaseInfo.getDiaryPhotos().clear();
                savedDiaryOnlyBaseInfo.getDiaryPhotos().addAll(finalDiaryPhotos);
            }
        }

        // 2. DiaryPhoto 연결 정보까지 포함하여 다시 저장 (이때 diary_photos 테이블에 diary_id가 채워짐)
        // CascadeType.ALL 또는 PERSIST, MERGE가 Diary.diaryPhotos에 설정되어 있어야 함
        Diary diaryWithPhotos = diaryRepository.saveAndFlush(savedDiaryOnlyBaseInfo); // saveAndFlush로 즉시 DB 반영 및 동기화
        log.debug("Diary ID {} 에 사진 {}개 연결 후 저장 및 플러시 완료.", diaryWithPhotos.getId(), diaryWithPhotos.getDiaryPhotos().size());

        // 3. 대표 사진 설정 (이때 diaryWithPhotos.getDiaryPhotos()는 DB와 동기화된 상태여야 함)
        //    createDiary 및 processAiDiaryGeneration에서 이 메소드 호출 후에 setExplicit/InitialRepresentativePhoto가 호출됨.
        //    따라서 이 메소드에서는 대표 사진 설정을 하지 않고, 호출부에서 처리하도록 합니다.
        //    또는, 대표사진 ID를 파라미터로 받아 여기서 설정하고 반환할 수도 있습니다.
        //    현재 구조상으로는 호출부에서 처리하는 것이 더 명확해 보입니다.

        // 4. 앨범 처리 (반드시 Diary와 DiaryPhoto 관계가 DB에 완전히 저장된 후)
        //    주의: processAiDiaryGeneration의 경우, 이 메소드가 반환된 후에 AI 응답을 받아 content/emotion/representativePhoto 등을 채우고
        //    다시 diaryRepository.save()를 한 후, 그 다음에 albumService.processDiaryAlbums를 호출해야 함.
        //    createDiary (수동)의 경우, 이 메소드 반환 후 대표사진 설정하고, 최종 save 후 albumService 호출.
        //    따라서 이 메소드 내부에서 albumService.processDiaryAlbums를 호출하는 것은 시점이 너무 이를 수 있음.
        // albumService.processDiaryAlbums(diaryWithPhotos, new ArrayList<>(diaryWithPhotos.getDiaryPhotos()));

        return diaryWithPhotos;
    }

    @Transactional
    public DiaryResponse updateWholeDiary(Long userId, Long diaryId, DiaryRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));
        Diary diary = diaryRepository.findByIdAndUserAndDeletedAtIsNull(diaryId, user)
                .orElseThrow(() -> new EntityNotFoundException("수정할 일기를 찾을 수 없습니다. ID: " + diaryId));

        // 0. 수정 전, 현재 일기가 연결된 앨범들 추적
        Set<Album> previouslyAffectedAlbums = diaryAlbumRepository.findByDiary(diary).stream()
                .map(DiaryAlbum::getAlbum)
                .collect(Collectors.toSet());

        // 1. 기본 정보 업데이트
        if (request.getDiaryDate() != null) {
            diary.setDiaryDate(request.getDiaryDate());
        }
        diary.setContent(request.getContent());
        diary.setEmotionIcon(request.getEmotionIcon());

        List<Long> requestedPhotoIds = request.getPhotoIds() == null ? new ArrayList<>() : request.getPhotoIds();
        Set<Long> requestedPhotoIdsSet = new HashSet<>(requestedPhotoIds);

        List<DiaryPhoto> photosToDelete = new ArrayList<>();
        List<DiaryPhoto> currentDiaryPhotosCopy = new ArrayList<>(diary.getDiaryPhotos());

        String currentRepresentativePhotoS3Key = diary.getRepresentativePhotoUrl();

        for (DiaryPhoto existingPhoto : currentDiaryPhotosCopy) {
            if (!requestedPhotoIdsSet.contains(existingPhoto.getId())) {
                photosToDelete.add(existingPhoto);
                if (existingPhoto.getPhotoUrl() != null && existingPhoto.getPhotoUrl().equals(currentRepresentativePhotoS3Key)) {
                    diary.setRepresentativePhotoUrl(null);
                }
            }
        }

        if (!photosToDelete.isEmpty()) {
            for (DiaryPhoto photo : photosToDelete) {
                if (StringUtils.hasText(photo.getPhotoUrl())) {
                    s3Uploader.deleteFileByUrl(photo.getPhotoUrl());
                }
            }
            diary.getDiaryPhotos().removeAll(photosToDelete);
            log.info("일기 ID {} 업데이트 중 {}개의 사진 삭제 완료.", diaryId, photosToDelete.size());
        }

        List<DiaryPhoto> newFinalDiaryPhotos = new ArrayList<>();
        Map<Long, DiaryPhoto> finalPhotoMap = new HashMap<>();

        for (int i = 0; i < requestedPhotoIds.size(); i++) {
            Long photoIdToAssign = requestedPhotoIds.get(i);
            DiaryPhoto photo = photoRepository.findById(photoIdToAssign)
                    .orElseThrow(() -> new EntityNotFoundException("일기에 추가하려는 사진을 찾을 수 없습니다. ID: " + photoIdToAssign));

            if (!photo.getUserId().equals(userId)) {
                throw new SecurityException("다른 사용자의 사진(ID: " + photo.getId() + ")을 일기에 추가할 수 없습니다.");
            }

            photo.setDiary(diary);
            photo.setSequence(i);
            newFinalDiaryPhotos.add(photo);
        }

        diary.getDiaryPhotos().clear();
        diary.getDiaryPhotos().addAll(newFinalDiaryPhotos);

        if (request.getRepresentativePhotoId() != null) {
            setExplicitRepresentativePhoto(diary, request.getRepresentativePhotoId(), userId, requestedPhotoIds);
        } else if (!newFinalDiaryPhotos.isEmpty()) {
            setInitialRepresentativePhoto(diary);
        } else {
            diary.setRepresentativePhotoUrl(null);
        }

        Diary savedDiary = diaryRepository.save(diary);
        log.info("일기 ID {} 전체 업데이트 완료.", diaryId);

        // 5. 앨범 정보 업데이트 및 빈 앨범 정리
        Set<Album> albumsToCheck = new HashSet<>(previouslyAffectedAlbums);
        if (albumService != null) {
            albumService.processDiaryAlbums(savedDiary, new ArrayList<>(savedDiary.getDiaryPhotos()));
            diaryAlbumRepository.findByDiary(savedDiary).forEach(da -> albumsToCheck.add(da.getAlbum()));
        }
        if (!albumsToCheck.isEmpty()) {
            log.info("일기 ID {} 수정 후 다음 앨범들의 상태를 확인합니다: {}", diaryId, albumsToCheck.stream().map(Album::getName).collect(Collectors.toList()));
            albumsToCheck.forEach(albumService::checkAndRemoveAlbumIfEmpty);
        }

        return DiaryResponse.from(savedDiary);
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

//    @Transactional
//    public DiaryResponse updateDiaryWithAiAssistance(Long userId, Long diaryId, DiaryAiUpdateRequest request) {
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));
//        Diary diary = diaryRepository.findById(diaryId)
//                .orElseThrow(() -> new EntityNotFoundException("수정할 일기를 찾을 수 없습니다. ID: " + diaryId));
//
//        if (!diary.getUser().getId().equals(userId)) {
//            throw new SecurityException("해당 일기에 대한 수정 권한이 없습니다.");
//        }
//
//        String userWritingStyle = user.getWritingStylePrompt();
//        if (!StringUtils.hasText(userWritingStyle)) {
//            userWritingStyle = "오늘 있었던 일을 바탕으로 일기를 작성해줘.";
//        }
//
//        AiDiaryModifyRequestDto aiModifyRequest = new AiDiaryModifyRequestDto(
//                userWritingStyle,
//                request.getMarkedDiaryContent(),
//                request.getUserRequest()
//        );
//
//        // AI 서버에 수정 요청
//        AiDiaryResponseDto aiResponse = aiServerService.requestDiaryModification(aiModifyRequest).block();
//
//        if (aiResponse == null || !StringUtils.hasText(aiResponse.getDiary())) {
//
//            throw new RuntimeException("AI 서버로부터 일기 수정 내용을 받지 못했습니다. 응답 내용: " + (aiResponse != null ? aiResponse.getDiary() : "null"));
//        }
//
//        // AI 서버로부터 받은 내용으로 일기 업데이트
//        diary.setContent(aiResponse.getDiary());
//        if (StringUtils.hasText(aiResponse.getEmoji())) {
//            diary.setEmotionIcon(aiResponse.getEmoji());
//        }
//
//        return DiaryResponse.from(diaryRepository.save(diary));
//    }

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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));
        Diary diary = diaryRepository.findByIdAndUserAndDeletedAtIsNull(diaryId, user) // 활성 일기만 수정 가능
                .orElseThrow(() -> new EntityNotFoundException("수정할 일기를 찾을 수 없습니다. ID: " + diaryId));

        // --- 0. 수정 전, 현재 일기가 연결된 앨범들 추적 ---
        Set<Album> previouslyAffectedAlbums = diaryAlbumRepository.findByDiary(diary).stream()
                .map(DiaryAlbum::getAlbum)
                .collect(Collectors.toSet());

        // --- 1. 요청된 사진 정보 준비 ---
        List<PhotoAssignmentDto> requestedAssignments = request.getPhotos() == null ? new ArrayList<>() : request.getPhotos();
        Set<Long> requestedPhotoIds = requestedAssignments.stream()
                .map(PhotoAssignmentDto::getPhotoId)
                .collect(Collectors.toSet());

        // --- 2. 기존 사진 정보 및 삭제 처리 ---
        List<DiaryPhoto> photosToDelete = new ArrayList<>();
        // diary.getDiaryPhotos()를 직접 수정하면 ConcurrentModificationException 발생 가능하므로 복사본 사용
        List<DiaryPhoto> currentDiaryPhotosCopy = new ArrayList<>(diary.getDiaryPhotos());

        String currentRepresentativePhotoUrl = diary.getRepresentativePhotoUrl();

        for (DiaryPhoto existingPhoto : currentDiaryPhotosCopy) {
            if (!requestedPhotoIds.contains(existingPhoto.getId())) {
                photosToDelete.add(existingPhoto);
                if (existingPhoto.getPhotoUrl() != null && existingPhoto.getPhotoUrl().equals(currentRepresentativePhotoUrl)) {
                    diary.setRepresentativePhotoUrl(null); // 대표 사진이 삭제되면 null로 설정
                }
            }
        }

        // 실제 삭제 처리 (DB + S3)
        if (!photosToDelete.isEmpty()) {
            for (DiaryPhoto photo : photosToDelete) {
                if (StringUtils.hasText(photo.getPhotoUrl())) {
                    log.info("S3 파일 삭제 시도: {}", photo.getPhotoUrl());
                    s3Uploader.deleteFileByUrl(photo.getPhotoUrl());
                }
            }
            diary.getDiaryPhotos().removeAll(photosToDelete);
            log.info("일기 ID {}에서 {}개의 사진 삭제 완료.", diaryId, photosToDelete.size());
        }

        // --- 3. 추가 및 순서 변경 처리 ---
        List<DiaryPhoto> newFinalDiaryPhotos = new ArrayList<>();

        for (PhotoAssignmentDto assignment : requestedAssignments) {
            Long photoIdToAssign = assignment.getPhotoId();
            DiaryPhoto photo = diary.getDiaryPhotos().stream()
                    .filter(dp -> dp.getId().equals(photoIdToAssign))
                    .findFirst()
                    .orElseGet(() -> photoRepository.findById(photoIdToAssign)
                            .orElseThrow(() -> new EntityNotFoundException("추가/수정하려는 사진을 찾을 수 없습니다. ID: " + photoIdToAssign)));

            if (!photo.getUserId().equals(userId)) {
                throw new SecurityException("다른 사용자의 사진(ID: " + photo.getId() + ")을 일기에 추가/수정할 수 없습니다.");
            }


            photo.setDiary(diary); // 현재 일기와 연결
            photo.setSequence(assignment.getSequence());
            newFinalDiaryPhotos.add(photo);
        }

        // Diary 엔티티의 사진 목록을 최종 목록으로 업데이트
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
            // 기존 대표사진이 삭제되었고, 새 대표사진 지정이 없으며, 사진이 남아있다면 첫번째 사진을 대표로
            setInitialRepresentativePhoto(diary);
        }
        // 만약 대표사진이 지정되지 않았고, 기존 대표사진 URL도 null이 아니고, 해당 사진이 여전히 목록에 있다면 유지됨.

        Diary savedDiary = diaryRepository.save(diary); // 일기 및 DiaryPhoto 변경사항 저장
        log.info("일기 ID {}의 사진 목록 업데이트 완료.", diaryId);

        // --- 5. 앨범 정보 업데이트 및 빈 앨범 정리 ---
        // 기존에 연결되었던 앨범과 새롭게 연결될 가능성이 있는 앨범 모두를 대상으로 상태 확인 필요
        Set<Album> albumsToCheck = new HashSet<>(previouslyAffectedAlbums);

        if (albumService != null) {
            albumService.processDiaryAlbums(savedDiary, new ArrayList<>(savedDiary.getDiaryPhotos())); // 사진 목록이 변경되었으므로 앨범 재처리

            // 재처리 후 현재 일기와 연결된 모든 앨범을 다시 가져옴
            diaryAlbumRepository.findByDiary(savedDiary).forEach(da -> albumsToCheck.add(da.getAlbum()));
        }

        // 영향을 받았을 가능성이 있는 모든 앨범에 대해 활성 일기 수 체크 및 자동 삭제
        if (!albumsToCheck.isEmpty()) {
            log.info("일기 ID {} 수정 후 다음 앨범들의 상태를 확인합니다: {}", diaryId, albumsToCheck.stream().map(Album::getName).collect(Collectors.toList()));
            albumsToCheck.forEach(albumService::checkAndRemoveAlbumIfEmpty);
        }

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

        // 일기가 속해있던 앨범 목록 가져옴
        List<Album> affectedAlbums = diaryAlbumRepository.findByDiary(diary).stream()
                .map(DiaryAlbum::getAlbum)
                .collect(Collectors.toList());

        diary.setDeletedAt(LocalDateTime.now());
        diaryRepository.save(diary);
        log.info("일기 ID {}를 휴지통으로 이동했습니다.", diaryId);

        // 영향을 받은 앨범들에 대해 활성 일기 수 체크 및 자동 삭제
        affectedAlbums.forEach(albumService::checkAndRemoveAlbumIfEmpty);
    }

    @Transactional
    public DiaryResponse restoreDiary(Long userId, Long diaryId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));

        // 1. 휴지통에서 복원할 일기 조회 (사용자 소유이며, 삭제된 상태인지 확인)
        Diary diaryToRestore = diaryRepository.findByIdAndUser(diaryId, user) // findByIdAndUser 사용 (deletedAt IS NOT NULL 조건은 아래 filter로)
                .filter(d -> d.getDeletedAt() != null) // 확실히 휴지통에 있는 일기인지 확인
                .orElseThrow(() -> new EntityNotFoundException("휴지통에서 해당 일기를 찾을 수 없거나 이미 활성 상태입니다. ID: " + diaryId));

        // 2. 복원하려는 날짜에 이미 활성 상태의 다른 일기가 있는지 확인
        LocalDate targetDate = diaryToRestore.getDiaryDate();
        Optional<Diary> existingActiveDiaryOnDate = diaryRepository.findByUserAndDiaryDateAndDeletedAtIsNull(user, targetDate);

        if (existingActiveDiaryOnDate.isPresent()) {
            // 복원하려는 일기와 다른 ID를 가진 활성 일기가 해당 날짜에 이미 존재한다면 복원 불가
            if (!existingActiveDiaryOnDate.get().getId().equals(diaryToRestore.getId())) {
                log.warn("일기 복원 실패: 사용자 ID {}, 날짜 {}에 이미 활성 상태의 일기(ID: {})가 존재합니다. 복원 시도 일기 ID: {}",
                        userId, targetDate, existingActiveDiaryOnDate.get().getId(), diaryId);
                throw new IllegalStateException("해당 날짜(" + targetDate + ")에 이미 다른 일기가 존재하여 복원할 수 없습니다.");
            }
        }

        // 3. 일기 복원 (deletedAt을 null로 설정)
        diaryToRestore.setDeletedAt(null);
        if ("trashed".equalsIgnoreCase(diaryToRestore.getStatus())) { // 만약 "trashed" 상태를 사용했다면
            diaryToRestore.setStatus("unconfirmed"); // 또는 원래 상태를 기억하고 있다면 그 상태로
        }

        Diary restoredDiary = diaryRepository.save(diaryToRestore);
        log.info("일기 ID {}를 복원했습니다. 날짜: {}", diaryId, restoredDiary.getDiaryDate());

        albumService.processDiaryAlbums(restoredDiary, new ArrayList<>(restoredDiary.getDiaryPhotos()));

        return DiaryResponse.from(restoredDiary);
    }

    @Transactional
    public void permanentlyDeleteDiary(Long userId, Long diaryId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        Diary diary = diaryRepository.findByIdAndUserAndDeletedAtIsNotNull(diaryId, user) // 휴지통에 있는 일기만 조회
                .orElseThrow(() -> new EntityNotFoundException("휴지통에서 해당 일기를 찾을 수 없거나 영구 삭제할 권한이 없습니다. ID: " + diaryId));

        // S3에서 사진 파일 삭제
        for (DiaryPhoto photo : diary.getDiaryPhotos()) {
            if (StringUtils.hasText(photo.getPhotoUrl())) {
                s3Uploader.deleteFileByUrl(photo.getPhotoUrl());
            }
        }
        log.info("일기 ID {}의 S3 사진 파일 삭제 완료.", diaryId);

        // 일기가 속해있던 앨범 목록을 미리 가져옴
        List<Album> affectedAlbums = diaryAlbumRepository.findByDiary(diary).stream()
                .map(DiaryAlbum::getAlbum)
                .collect(Collectors.toList());

        // DiaryAlbum 연결 삭제
        diaryAlbumRepository.deleteByDiary(diary);
        log.info("일기 ID {}의 DiaryAlbum 연결 삭제 완료.", diaryId);

        // Diary 엔티티 삭제
        diaryRepository.delete(diary);
        log.info("일기 ID {} 영구 삭제 완료.", diaryId);

        affectedAlbums.forEach(albumService::checkAndRemoveAlbumIfEmpty);
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
        Set<Album> allAffectedAlbums = new HashSet<>();

        for (Diary diary : trashedDiaries) {
            // S3 파일 삭제
            for (DiaryPhoto photo : diary.getDiaryPhotos()) {
                if (StringUtils.hasText(photo.getPhotoUrl())) {
                    s3Uploader.deleteFileByUrl(photo.getPhotoUrl());
                }
            }
            // 일기가 속해있던 앨범들을 수집
            diaryAlbumRepository.findByDiary(diary).forEach(da -> allAffectedAlbums.add(da.getAlbum()));
            // DiaryAlbum 연결 삭제
            diaryAlbumRepository.deleteByDiary(diary);
        }
        // 모든 대상 Diary DB에서 한 번에 삭제
        diaryRepository.deleteAll(trashedDiaries);
        log.info("사용자 ID {}의 휴지통 내 모든 일기 영구 삭제 완료.", userId);

        // 영향을 받은 모든 앨범들에 대해 활성 일기 수 체크 및 자동 삭제
        allAffectedAlbums.forEach(albumService::checkAndRemoveAlbumIfEmpty);
        log.info("사용자 ID {}의 휴지통 비우기 후 앨범 정리 완료.", userId);
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
        Set<Album> allAffectedAlbums = new HashSet<>();

        for (Diary diary : oldTrashedDiaries) {
            // S3 파일 삭제
            for (DiaryPhoto photo : diary.getDiaryPhotos()) {
                if (StringUtils.hasText(photo.getPhotoUrl())) {
                    s3Uploader.deleteFileByUrl(photo.getPhotoUrl());
                }
            }
            // 일기가 속해있던 앨범들을 수집
            diaryAlbumRepository.findByDiary(diary).forEach(da -> allAffectedAlbums.add(da.getAlbum()));
            // DiaryAlbum 연결 삭제
            diaryAlbumRepository.deleteByDiary(diary);
        }
        // 모든 대상 Diary DB에서 한 번에 삭제
        diaryRepository.deleteAll(oldTrashedDiaries);
        log.info("30일 경과 휴지통 일기 {}개 자동 영구 삭제 완료.", oldTrashedDiaries.size());

        // 영향을 받은 모든 앨범들에 대해 활성 일기 수 체크 및 자동 삭제
        allAffectedAlbums.forEach(albumService::checkAndRemoveAlbumIfEmpty);
        log.info("30일 경과 휴지통 일기 자동 영구 삭제 후 앨범 정리 완료.");
    }
}