package com.apply.diarypic.photo.service;

import com.apply.diarypic.ai.dto.AiImageScoringRequestDto;
import com.apply.diarypic.ai.dto.AiPhotoInputDto;
import com.apply.diarypic.ai.service.AiServerService;
import com.apply.diarypic.photo.entity.DiaryPhoto;
import com.apply.diarypic.photo.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoRecommendationService {

    private final AiServerService aiServerService;
    private final PhotoRepository photoRepository;

    @Transactional(readOnly = true)
    public Mono<List<Long>> getRecommendedPhotosFromAI(Long userId, List<Long> uploadedPhotoIds, List<Long> mandatoryPhotoIds) {
        // 1. 초기 조건 체크 시 반환 타입 명시
        if (uploadedPhotoIds == null || uploadedPhotoIds.isEmpty()) {
            log.warn("AI 추천 요청: 사용자 ID {}에 대해 업로드된 사진 ID 목록이 비어있습니다.", userId);
            List<Long> resultList = mandatoryPhotoIds != null ? new ArrayList<>(mandatoryPhotoIds) : Collections.<Long>emptyList(); // 타입 명시
            return Mono.just(resultList);
        }

        List<DiaryPhoto> allUserPhotosInRequest = photoRepository.findAllById(uploadedPhotoIds).stream()
                .filter(photo -> photo.getUserId().equals(userId))
                .collect(Collectors.toList());

        // 2. 유효한 사진 없을 시 반환 타입 명시
        if (allUserPhotosInRequest.isEmpty()) {
            log.warn("AI 추천 요청: 사용자 ID {}에 대해 유효한 사진을 찾을 수 없습니다 (업로드 ID: {}).", userId, uploadedPhotoIds);
            List<Long> resultList = mandatoryPhotoIds != null ? new ArrayList<>(mandatoryPhotoIds) : Collections.<Long>emptyList(); // 타입 명시
            return Mono.just(resultList);
        }

        List<AiPhotoInputDto> imagesForAi = allUserPhotosInRequest.stream()
                .map(photo -> new AiPhotoInputDto(String.valueOf(photo.getId()), photo.getPhotoUrl()))
                .collect(Collectors.toList());

        List<AiPhotoInputDto> referenceImagesForAi;
        if (mandatoryPhotoIds != null && !mandatoryPhotoIds.isEmpty()) {
            Set<Long> mandatoryIdSet = new HashSet<>(mandatoryPhotoIds);
            referenceImagesForAi = allUserPhotosInRequest.stream()
                    .filter(photo -> mandatoryIdSet.contains(photo.getId()))
                    .map(photo -> new AiPhotoInputDto(String.valueOf(photo.getId()), photo.getPhotoUrl()))
                    .collect(Collectors.toList());
        } else {
            referenceImagesForAi = Collections.emptyList(); // 이 자체는 괜찮음, 이후 사용 방식에 따라 결정
        }

        AiImageScoringRequestDto scoringRequest = new AiImageScoringRequestDto(imagesForAi, referenceImagesForAi);
        log.info("AI 서버에 사진 추천 요청: 사용자 ID {}, 전체 사진 {}장, 필수 사진 {}장", userId, imagesForAi.size(), referenceImagesForAi.size());

        return aiServerService.requestPhotoRecommendation(scoringRequest)
                .flatMap(aiResponse -> {
                    List<Long> recommendedIdsFromAi = new ArrayList<>();
                    if (aiResponse != null && aiResponse.getRecommendedPhotoIds() != null) {
                        log.info("AI 서버로부터 원본 추천 사진 ID 수신: {}", aiResponse.getRecommendedPhotoIds());
                        recommendedIdsFromAi = aiResponse.getRecommendedPhotoIds().stream()
                                .map(objId -> {
                                    if (objId instanceof Integer) return ((Integer) objId).longValue();
                                    if (objId instanceof String) {
                                        try { return Long.parseLong((String) objId); }
                                        catch (NumberFormatException e) { log.warn("AI 응답 ID 파싱 오류: {}", objId); return null; }
                                    }
                                    if (objId instanceof Long) return (Long) objId;
                                    log.warn("AI 응답 ID 타입 오류: {}", objId != null ? objId.getClass().getName() : "null");
                                    return null;
                                })
                                .filter(id -> id != null)
                                .collect(Collectors.toList());
                    } else {
                        log.warn("AI 서버 사진 추천 응답이 null이거나 recommendedPhotoIds가 null입니다. 사용자 ID: {}.", userId);
                    }

                    Set<Long> combinedIds = new HashSet<>();
                    if (mandatoryPhotoIds != null) {
                        combinedIds.addAll(mandatoryPhotoIds);
                    }
                    combinedIds.addAll(recommendedIdsFromAi);

                    if (combinedIds.isEmpty()){
                        log.info("필수 사진 및 AI 추천 사진이 없어 빈 목록 반환. 사용자 ID: {}", userId);
                        return Mono.just(Collections.<Long>emptyList()); // 타입 명시
                    }

                    List<DiaryPhoto> photosToSort = photoRepository.findAllById(new ArrayList<>(combinedIds));

                    List<Long> sortedFinalIds = photosToSort.stream()
                            .filter(p -> p.getUserId().equals(userId))
                            .sorted(Comparator.comparing(DiaryPhoto::getShootingDateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                                    .thenComparing(DiaryPhoto::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                            .map(DiaryPhoto::getId)
                            .limit(9)
                            .collect(Collectors.toList());

                    log.info("최종 추천 및 정렬된 사진 ID 목록 (최대 9장): {}", sortedFinalIds);
                    return Mono.just(sortedFinalIds);

                })
                .onErrorResume(e -> { // 3. onErrorResume에서 반환 타입 명시
                    log.error("AI 사진 추천 처리 중 오류 발생. 필수 사진 목록(정렬 전) 또는 빈 목록 반환. 오류: {}", e.getMessage(), e);
                    List<Long> resultList = mandatoryPhotoIds != null ? new ArrayList<>(mandatoryPhotoIds) : Collections.<Long>emptyList(); // 타입 명시
                    return Mono.just(resultList);
                });
    }
}