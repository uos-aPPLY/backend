package com.apply.diarypic.photo.service;

import com.apply.diarypic.ai.dto.AiPhotoScoreRequestDto;
import com.apply.diarypic.ai.service.AiServerService;
import com.apply.diarypic.photo.entity.DiaryPhoto;
import com.apply.diarypic.photo.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Transactional(readOnly = true)
    public List<Long> getRecommendedPhotosFromAI(Long userId, List<Long> uploadedPhotoIds, List<Long> mandatoryPhotoIds) {
        if (uploadedPhotoIds == null || uploadedPhotoIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<DiaryPhoto> userPhotos = photoRepository.findAllById(uploadedPhotoIds).stream()
                .filter(photo -> photo.getUserId().equals(userId))
                .collect(Collectors.toList());

        if (userPhotos.isEmpty()) {
            log.warn("No valid photos found for user {} from uploadedPhotoIds.", userId);
            return Collections.emptyList();
        }

        List<Long> validMandatoryPhotoIds = (mandatoryPhotoIds == null) ? Collections.emptyList() :
                mandatoryPhotoIds.stream()
                        .filter(id -> userPhotos.stream().anyMatch(p -> p.getId().equals(id)))
                        .collect(Collectors.toList());

        List<AiPhotoScoreRequestDto> photosToScore = userPhotos.stream()
                .map(photo -> new AiPhotoScoreRequestDto(
                        photo.getId(),
                        photo.getPhotoUrl(),
                        photo.getShootingDateTime() != null ? photo.getShootingDateTime().format(ISO_LOCAL_DATE_TIME_FORMATTER) : null,
                        photo.getDetailedAddress(),
                        validMandatoryPhotoIds.contains(photo.getId())
                ))
                .collect(Collectors.toList());

        if (photosToScore.isEmpty() && !validMandatoryPhotoIds.isEmpty()) {
            // AI에 보낼 사진은 없지만 필수 사진은 있는 경우, 필수 사진만 반환
            return new ArrayList<>(validMandatoryPhotoIds);
        }
        if (photosToScore.isEmpty()){
            return Collections.emptyList();
        }


        // AI 서버에 추천 요청
        Mono<List<Long>> recommendedPhotoIdsMono = aiServerService.requestPhotoRecommendation(photosToScore);
        List<Long> aiRecommendedIds = recommendedPhotoIdsMono.block(); // 테스트를 위해 block, 실제로는 비동기 처리

        if (aiRecommendedIds == null) {
            log.warn("AI server returned null for photo recommendation for user {}. Returning mandatory photos only or empty.", userId);
            return validMandatoryPhotoIds.isEmpty() ? Collections.emptyList() : new ArrayList<>(validMandatoryPhotoIds);
        }

        // 최종 결과 조합: 필수 사진 + AI 추천 사진 (중복 제거, 최대 9장)
        Set<Long> finalSelection = new HashSet<>(validMandatoryPhotoIds);
        finalSelection.addAll(aiRecommendedIds);

        return finalSelection.stream().limit(9).collect(Collectors.toList());
    }
}