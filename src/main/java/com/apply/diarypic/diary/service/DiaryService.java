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
        List<Long> currentPhotoIds = new ArrayList<>();

        if (!CollectionUtils.isEmpty(request.getPhotoIds())) {
            for (int i = 0; i < request.getPhotoIds().size(); i++) {
                Long photoId = request.getPhotoIds().get(i);
                photoPayloads.add(new AiDiaryCreateRequest.FinalizedPhotoPayload(photoId, "", i + 1));
                currentPhotoIds.add(photoId);
            }
        }

        // 2. Diary 및 DiaryPhoto 기본 연결 및 저장 (대표사진 URL은 아직 미설정)
        // createAndSaveDiaryAndAlbums 내부에서 content, emotionIcon 등 기본 정보와 사진 연결, Geocoding까지 처리
        Diary diaryInProgress = createAndSaveDiaryAndAlbums(user, request.getContent(), request.getEmotionIcon(), diaryDate, photoPayloads, userId, false, true); // Geocoding 수행하도록 true 전달

        // 3. 대표 사진 설정
        if (request.getRepresentativePhotoId() != null) {
            setExplicitRepresentativePhoto(diaryInProgress, request.getRepresentativePhotoId(), userId, currentPhotoIds);
        } else {
            setInitialRepresentativePhoto(diaryInProgress);
        }

        // 4. 대표 사진 URL까지 포함하여 최종 저장 및 DB와 즉시 동기화
        Diary fullySavedDiary = diaryRepository.saveAndFlush(diaryInProgress);
        log.info("Diary ID {} (수동생성) 최종 저장 완료. 대표사진 URL: {}", fullySavedDiary.getId(), fullySavedDiary.getRepresentativePhotoUrl());

        // 5. 앨범 처리 (모든 정보가 DB에 저장된 후)
        albumService.processDiaryAlbums(fullySavedDiary, new ArrayList<>(fullySavedDiary.getDiaryPhotos()));
        log.info("Diary ID {} (수동생성) 앨범 처리 완료.", fullySavedDiary.getId());

        // 6. 최종적으로 DB에서 다시 조회하여 응답 생성 (사진 목록 포함 보장)
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
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        LocalDate diaryDate = aiDiaryCreateRequest.getDiaryDate();
        if (diaryDate == null) diaryDate = LocalDate.now();

        Optional<Diary> existingDiaryOpt = diaryRepository.findByUserAndDiaryDateAndDeletedAtIsNull(user, diaryDate);
        if (existingDiaryOpt.isPresent()) {
            Diary existingDiary = existingDiaryOpt.get();
            if ("generating".equalsIgnoreCase(existingDiary.getStatus())) {
                throw new IllegalStateException("해당 날짜(" + diaryDate + ")의 일기가 이미 생성 중입니다.");
            }
            throw new IllegalArgumentException("해당 날짜(" + diaryDate + ")에 이미 일기가 존재합니다. ID: " + existingDiary.getId());
        }

        List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads = aiDiaryCreateRequest.getFinalizedPhotos();
        if (finalizedPhotoPayloads == null || finalizedPhotoPayloads.isEmpty() || finalizedPhotoPayloads.size() > 9) {
            throw new IllegalArgumentException("사진 정보가 올바르지 않습니다.");
        }

        // 1. "generating" 상태로 Diary 임시 저장 (Geocoding은 아직 안 함)
        // content, emotionIcon, representativePhotoUrl은 비동기 처리 후 채워짐
        Diary generatingDiaryEntity = Diary.builder()
                .user(user)
                .diaryDate(diaryDate)
                .status("generating")
                .content("") // 임시값
                .emotionIcon("smile") // 초기값 설정 가능
                .isFavorited(false)
                .diaryPhotos(new ArrayList<>())
                .build();
        Diary savedGeneratingDiary = diaryRepository.save(generatingDiaryEntity);
        log.info("일기 ID {} (날짜: {}) 'generating' 상태로 임시 저장됨.", savedGeneratingDiary.getId(), diaryDate);

        // 2. DiaryPhoto들을 임시 저장된 Diary와 연결 (DB에는 아직 미반영, 컬렉션에만 추가 후 비동기에서 처리)
        // 이 단계에서 photoPayloads를 비동기 메소드로 넘겨서 거기서 DiaryPhoto 연결 및 저장을 처리.
        // 바로 아래 processAiDiaryGeneration 호출.

        // 3. 비동기 AI 처리 호출
        processAiDiaryGeneration(user, savedGeneratingDiary.getId(), aiDiaryCreateRequest);

        // 4. 클라이언트에는 "generating" 상태의 DiaryResponse 즉시 반환
        // 이때 photos 리스트는 비어있음 (실제 연결 및 저장은 비동기에서)
        // DiaryResponse.from()이 DB lazy loading을 트리거하지 않도록,
        // savedGeneratingDiary 객체 (photos 컬렉션이 비어있는)를 그대로 사용.
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


    @Async("diaryAiTaskExecutor") // AI 작업용 별도 스레드 풀
    @Transactional
    public void processAiDiaryGeneration(User user, Long diaryId, AiDiaryCreateRequest aiDiaryCreateRequest) {
        List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads = aiDiaryCreateRequest.getFinalizedPhotos();
        log.info("비동기 AI 일기 생성 시작: Diary ID {}", diaryId);
        Diary diaryToUpdate = diaryRepository.findById(diaryId).orElse(null);

        if (diaryToUpdate == null || !"generating".equalsIgnoreCase(diaryToUpdate.getStatus())) {
            log.warn("비동기 AI 일기 생성 중단: Diary ID {} 찾을 수 없거나 상태가 'generating' 아님 (현재: {}).",
                    diaryId, diaryToUpdate != null ? diaryToUpdate.getStatus() : "null");
            return;
        }

        try {
            // 1. DiaryPhoto 연결 및 Geocoding 수행 후 저장
            List<DiaryPhoto> actualDiaryPhotos = new ArrayList<>();
            if (finalizedPhotoPayloads != null) {
                finalizedPhotoPayloads.sort(Comparator.comparingInt(AiDiaryCreateRequest.FinalizedPhotoPayload::getSequence));
                for (AiDiaryCreateRequest.FinalizedPhotoPayload payload : finalizedPhotoPayloads) {
                    DiaryPhoto diaryPhoto = photoRepository.findById(payload.getPhotoId())
                            .orElseThrow(() -> new EntityNotFoundException("AI 생성 중 사진 못찾음 ID: " + payload.getPhotoId()));
                    if (!diaryPhoto.getUserId().equals(user.getId())) { // user 객체 직접 비교
                        throw new SecurityException("Photo access denied: " + payload.getPhotoId());
                    }
                    diaryPhoto.setDiary(diaryToUpdate);
                    diaryPhoto.setSequence(payload.getSequence());

                    // Geocoding 수행
                    if (diaryPhoto.getLocation() != null &&
                            (!StringUtils.hasText(diaryPhoto.getCountryName()) ||
                                    !StringUtils.hasText(diaryPhoto.getAdminAreaLevel1()) ||
                                    !StringUtils.hasText(diaryPhoto.getLocality()))) {
                        try {
                            String[] latLng = diaryPhoto.getLocation().split(",");
                            if (latLng.length == 2) {
                                double latitude = Double.parseDouble(latLng[0]);
                                double longitude = Double.parseDouble(latLng[1]);
                                GeocodingService.ParsedAddress parsedAddress = geocodingService.getParsedAddressFromCoordinates(latitude, longitude);
                                if (parsedAddress != null) {
                                    diaryPhoto.setCountryName(parsedAddress.getCountryName());
                                    diaryPhoto.setAdminAreaLevel1(parsedAddress.getAdminAreaLevel1());
                                    diaryPhoto.setLocality(parsedAddress.getLocality());
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Diary ID {} - Photo ID {}: 비동기 Geocoding 오류: {}", diaryId, diaryPhoto.getId(), e.getMessage());
                        }
                    }
                    actualDiaryPhotos.add(diaryPhoto);
                }
                diaryToUpdate.getDiaryPhotos().clear();
                diaryToUpdate.getDiaryPhotos().addAll(actualDiaryPhotos);
            }
            // 사진 정보 (Geocoding 포함) 및 연결 정보를 먼저 DB에 반영
            diaryRepository.saveAndFlush(diaryToUpdate);
            log.info("Diary ID {}에 사진 {}개 연결 및 Geocoding 후 저장 (비동기).", diaryId, actualDiaryPhotos.size());

            // 2. AI 서버 요청 DTO 생성 (이때 diaryPhoto.getPhotoUrl()은 Public URL)
            String userWritingStyle = user.getWritingStylePrompt();
            if (!StringUtils.hasText(userWritingStyle)) userWritingStyle = "오늘 있었던 일을 바탕으로 일기를 작성해줘.";

            List<ImageInfoDto> imageInfoForAi = diaryToUpdate.getDiaryPhotos().stream() // DB에 저장된 최신 사진 정보 사용
                    .map(dp -> {
                        String publicImageUrl = dp.getPhotoUrl();
                        String combinedAddress = Stream.of(dp.getLocality(), dp.getAdminAreaLevel1(), dp.getCountryName())
                                .filter(StringUtils::hasText).collect(Collectors.joining(", "));
                        AiDiaryCreateRequest.FinalizedPhotoPayload currentPayload = finalizedPhotoPayloads.stream()
                                .filter(p -> p.getPhotoId().equals(dp.getId())).findFirst().orElse(null);
                        String keyword = currentPayload != null ? currentPayload.getKeyword() : "";

                        return new ImageInfoDto(publicImageUrl,
                                dp.getShootingDateTime() != null ? dp.getShootingDateTime().format(ISO_LOCAL_DATE_TIME_FORMATTER) : null,
                                StringUtils.hasText(combinedAddress) ? combinedAddress : null, keyword, dp.getSequence());
                    }).collect(Collectors.toList());
            AiDiaryGenerateRequestDto aiRequest = new AiDiaryGenerateRequestDto(userWritingStyle, imageInfoForAi);

            // 3. AI 서버에 일기 생성 요청
            aiServerService.requestDiaryGeneration(aiRequest)
                    .subscribe(aiResponse -> {
                        Diary diaryToFinalize = diaryRepository.findById(diaryId).orElse(null); // 다시 조회
                        if (diaryToFinalize == null || !"generating".equalsIgnoreCase(diaryToFinalize.getStatus())) {
                            log.warn("AI 응답 후 Diary ID {} 찾을 수 없거나 상태 변경됨.", diaryId); return;
                        }
                        if (aiResponse != null && StringUtils.hasText(aiResponse.getDiary())) {
                            diaryToFinalize.setContent(aiResponse.getDiary());
                            diaryToFinalize.setEmotionIcon(aiResponse.getEmoji());
                            diaryToFinalize.setStatus("unconfirmed");

                            if (aiDiaryCreateRequest.getRepresentativePhotoId() != null) {
                                setExplicitRepresentativePhoto(diaryToFinalize, aiDiaryCreateRequest.getRepresentativePhotoId(), user.getId(),
                                        diaryToFinalize.getDiaryPhotos().stream().map(DiaryPhoto::getId).collect(Collectors.toList()));
                            } else {
                                setInitialRepresentativePhoto(diaryToFinalize);
                            }
                            Diary finalSavedDiary = diaryRepository.saveAndFlush(diaryToFinalize); // 최종 저장 및 플러시
                            log.info("비동기 AI 일기 생성 DB 최종 저장 완료: Diary ID {}", diaryId);

                            albumService.processDiaryAlbums(finalSavedDiary, new ArrayList<>(finalSavedDiary.getDiaryPhotos()));
                            log.info("Diary ID {} (AI생성) 앨범 처리 완료.", diaryId);
                        } else {
                            handleAiProcessingError(diaryToFinalize, "AI 응답 내용 없음");
                        }
                    }, error -> handleAiProcessingError(diaryRepository.findById(diaryId).orElse(null), "AI 구독 오류: " + error.getMessage()));
        } catch (Exception e) {
            log.error("비동기 AI 일기 생성 로직 전체 예외: Diary ID {}. Error: {}", diaryId, e.getMessage(), e);
            handleAiProcessingError(diaryRepository.findById(diaryId).orElse(null), "시스템 오류");
        }
    }

    private void handleAiProcessingError(Diary diary, String errorMessage) {
        if (diary != null && "generating".equalsIgnoreCase(diary.getStatus())) {
            diary.setStatus("failed");
            String originalContent = diary.getContent() == null ? "" : diary.getContent();
            diary.setContent(originalContent + "\n[AI 처리 실패: " + errorMessage + "]");
            diaryRepository.save(diary);
            log.error("Diary ID {} 상태 'failed'로 변경. 원인: {}", diary.getId(), errorMessage);
        } else if (diary != null) {
            log.warn("Diary ID {} 상태가 'generating'이 아니므로(현재: {}), AI 실패 처리를 건너뜁니다.", diary.getId(), diary.getStatus());
        } else {
            log.error("AI 처리 실패 후 Diary를 찾을 수 없어 상태 변경 불가. 원인: {}", errorMessage);
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

    private Diary createAndSaveDiaryAndAlbums(User user, String content, String emotionIcon, LocalDate diaryDate,
                                              List<AiDiaryCreateRequest.FinalizedPhotoPayload> finalizedPhotoPayloads,
                                              Long userId, boolean isAiGenerated, boolean performGeocoding) {
        Diary diary = Diary.builder()
                .user(user)
                .content(content)
                .emotionIcon(emotionIcon) // 초기 아이콘 설정 (AI 생성 시에는 aiResponse.getEmoji()로 덮어쓰여짐)
                .diaryDate(diaryDate)
                .isFavorited(false)
                .status(isAiGenerated ? (content.isEmpty() ? "generating" : "unconfirmed") : "confirmed") // AI 생성이고 아직 content 없으면 generating
                .diaryPhotos(new ArrayList<>())
                .build();
        Diary savedDiary = diaryRepository.save(diary);

        List<DiaryPhoto> diaryPhotosForDiaryEntities = new ArrayList<>();
        if (finalizedPhotoPayloads != null) {
            finalizedPhotoPayloads.sort(Comparator.comparingInt(AiDiaryCreateRequest.FinalizedPhotoPayload::getSequence));
            for (AiDiaryCreateRequest.FinalizedPhotoPayload payload : finalizedPhotoPayloads) {
                DiaryPhoto diaryPhoto = photoRepository.findById(payload.getPhotoId())
                        .orElseThrow(() -> new IllegalArgumentException("저장할 사진 정보를 찾을 수 없습니다. ID: " + payload.getPhotoId()));
                if (!diaryPhoto.getUserId().equals(userId)) {
                    throw new SecurityException("해당 사진에 대한 접근 권한이 없습니다. Photo ID: " + payload.getPhotoId());
                }
                diaryPhoto.setDiary(savedDiary);
                diaryPhoto.setSequence(payload.getSequence());

                if (performGeocoding) { // Geocoding 수행 여부 플래그 확인
                    if (diaryPhoto.getLocation() != null &&
                            (!StringUtils.hasText(diaryPhoto.getCountryName()) ||
                                    !StringUtils.hasText(diaryPhoto.getAdminAreaLevel1()) ||
                                    !StringUtils.hasText(diaryPhoto.getLocality()))) {
                        try {
                            String[] latLng = diaryPhoto.getLocation().split(",");
                            if (latLng.length == 2) {
                                double latitude = Double.parseDouble(latLng[0]);
                                double longitude = Double.parseDouble(latLng[1]);
                                GeocodingService.ParsedAddress parsedAddress = geocodingService.getParsedAddressFromCoordinates(latitude, longitude);
                                if (parsedAddress != null) {
                                    diaryPhoto.setCountryName(parsedAddress.getCountryName());
                                    diaryPhoto.setAdminAreaLevel1(parsedAddress.getAdminAreaLevel1());
                                    diaryPhoto.setLocality(parsedAddress.getLocality());
                                }
                            }
                        } catch (Exception e) {
                            log.warn("일기 저장 중 Photo ID {}: Geocoding 오류: {}", diaryPhoto.getId(), e.getMessage());
                        }
                    }
                }
                diaryPhotosForDiaryEntities.add(diaryPhoto);
                // 키워드 처리 로직 (생략)
            }
        }
        savedDiary.getDiaryPhotos().clear();
        savedDiary.getDiaryPhotos().addAll(diaryPhotosForDiaryEntities);
        // 사진 정보(Geocoding 결과 포함) 및 연결 정보를 먼저 DB에 반영
        Diary fullySavedDiary = diaryRepository.saveAndFlush(savedDiary);
        log.info("Diary ID {}에 사진 {}개 연결 및 Geocoding (필요시) 후 저장.", fullySavedDiary.getId(), fullySavedDiary.getDiaryPhotos().size());

        // 앨범 처리는 이 메소드 호출부에서, 모든 정보 (대표사진 포함)가 Diary에 설정된 후 수행
        // if (!diaryPhotosForDiaryEntities.isEmpty()) {
        //    albumService.processDiaryAlbums(fullySavedDiary, diaryPhotosForDiaryEntities);
        // }
        return fullySavedDiary;
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