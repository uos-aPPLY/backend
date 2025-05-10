package com.apply.diarypic.photo.service;

import com.apply.diarypic.ai.dto.AiPhotoScoreRequestDto;
import com.apply.diarypic.ai.service.AiServerService;
import com.apply.diarypic.photo.entity.DiaryPhoto;
import com.apply.diarypic.photo.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils; // StringUtils 임포트
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream; // Stream 임포트

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
                .map(photo -> {
                    // DiaryPhoto의 countryName, adminAreaLevel1, locality를 조합하여 주소 문자열 생성
                    String combinedAddress = Stream.of(photo.getLocality(), photo.getAdminAreaLevel1(), photo.getCountryName())
                            .filter(StringUtils::hasText)
                            .collect(Collectors.joining(", "));
                    if (!StringUtils.hasText(combinedAddress)) {
                        combinedAddress = null; // 모든 정보가 없으면 null
                    }

                    return new AiPhotoScoreRequestDto(
                            photo.getId(),
                            photo.getPhotoUrl(),
                            photo.getShootingDateTime() != null ? photo.getShootingDateTime().format(ISO_LOCAL_DATE_TIME_FORMATTER) : null,
                            combinedAddress, // 조합된 주소 전달
                            validMandatoryPhotoIds.contains(photo.getId())
                    );
                })
                .collect(Collectors.toList());

        if (photosToScore.isEmpty() && !validMandatoryPhotoIds.isEmpty()) {
            return new ArrayList<>(validMandatoryPhotoIds);
        }
        if (photosToScore.isEmpty()){
            return Collections.emptyList();
        }

        Mono<List<Long>> recommendedPhotoIdsMono = aiServerService.requestPhotoRecommendation(photosToScore);
        List<Long> aiRecommendedIds = recommendedPhotoIdsMono.block();

        if (aiRecommendedIds == null) {
            log.warn("AI server returned null for photo recommendation for user {}. Returning mandatory photos only or empty.", userId);
            return validMandatoryPhotoIds.isEmpty() ? Collections.emptyList() : new ArrayList<>(validMandatoryPhotoIds);
        }

        Set<Long> finalSelection = new HashSet<>(validMandatoryPhotoIds);
        finalSelection.addAll(aiRecommendedIds);

        return finalSelection.stream().limit(9).collect(Collectors.toList());
    }
}