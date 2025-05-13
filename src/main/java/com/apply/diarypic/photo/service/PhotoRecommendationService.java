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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoRecommendationService {

    private final AiServerService aiServerService;
    private final PhotoRepository photoRepository;

    @Transactional(readOnly = true)
    public Mono<List<Long>> getRecommendedPhotosFromAI(Long userId, List<Long> uploadedPhotoIds, List<Long> mandatoryPhotoIds) {
        if (uploadedPhotoIds == null || uploadedPhotoIds.isEmpty()) {
            log.warn("AI 추천 요청 (비동기): 사용자 ID {}에 대해 업로드된 사진 ID 목록이 비어있습니다.", userId);
            return Mono.just(Collections.emptyList());
        }


        List<DiaryPhoto> allUserPhotosInRequest = photoRepository.findAllById(uploadedPhotoIds).stream()
                .filter(photo -> photo.getUserId().equals(userId))
                .collect(Collectors.toList());

        if (allUserPhotosInRequest.isEmpty()) {
            log.warn("AI 추천 요청 (비동기): 사용자 ID {}에 대해 유효한 사진을 찾을 수 없습니다 (업로드 ID: {}).", userId, uploadedPhotoIds);
            return Mono.just(Collections.emptyList());
        }

        List<AiPhotoInputDto> imagesForAi = allUserPhotosInRequest.stream()
                .map(photo -> new AiPhotoInputDto(String.valueOf(photo.getId()), photo.getPhotoUrl()))
                .collect(Collectors.toList());

        List<AiPhotoInputDto> referenceImagesForAi;
        if (mandatoryPhotoIds != null && !mandatoryPhotoIds.isEmpty()) {
            referenceImagesForAi = allUserPhotosInRequest.stream()
                    .filter(photo -> mandatoryPhotoIds.contains(photo.getId()))
                    .map(photo -> new AiPhotoInputDto(String.valueOf(photo.getId()), photo.getPhotoUrl()))
                    .collect(Collectors.toList());
        } else {
            referenceImagesForAi = Collections.emptyList();
        }

        AiImageScoringRequestDto scoringRequest = new AiImageScoringRequestDto(imagesForAi, referenceImagesForAi);

        log.info("AI 서버에 사진 추천 요청 (비동기): 사용자 ID {}, 전체 사진 {}장, 필수 사진 {}장", userId, imagesForAi.size(), referenceImagesForAi.size());

        return aiServerService.requestPhotoRecommendation(scoringRequest)
                .map(aiResponse -> {
                    if (aiResponse == null || aiResponse.getRecommendedPhotoIds() == null) {
                        log.warn("AI 서버 사진 추천 응답(비동기)이 null이거나 recommendedPhotoIds가 null입니다. 사용자 ID: {}.", userId);
                        return mandatoryPhotoIds != null ? new ArrayList<>(mandatoryPhotoIds) : Collections.<Long>emptyList();
                    }

                    log.info("AI 서버로부터 추천 사진 ID {}개 수신 (비동기): {}", aiResponse.getRecommendedPhotoIds().size(), aiResponse.getRecommendedPhotoIds());
                    List<Long> recommendedIdsFromAi = aiResponse.getRecommendedPhotoIds().stream()
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

                    Set<Long> finalSelection = new HashSet<>();
                    if (mandatoryPhotoIds != null) {
                        finalSelection.addAll(mandatoryPhotoIds);
                    }
                    finalSelection.addAll(recommendedIdsFromAi);

                    List<Long> result = finalSelection.stream().limit(9).collect(Collectors.toList());
                    log.info("최종 추천 사진 ID 목록 (비동기, 최대 9장): {}", result);
                    return result;
                })
                .defaultIfEmpty(mandatoryPhotoIds != null ? new ArrayList<>(mandatoryPhotoIds) : Collections.emptyList()) // AI 응답이 완전히 비었을 경우
                .onErrorResume(e -> {
                    log.error("AI 사진 추천 처리 중 오류 발생 (비동기). 필수 사진 또는 빈 목록 반환. 오류: {}", e.getMessage());
                    return Mono.just(mandatoryPhotoIds != null ? new ArrayList<>(mandatoryPhotoIds) : Collections.emptyList());
                });
    }
}