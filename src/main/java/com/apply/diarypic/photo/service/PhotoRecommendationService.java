package com.apply.diarypic.photo.service;

import com.apply.diarypic.ai.dto.AiPhotoScoreRequestDto;
import com.apply.diarypic.ai.service.AiServerService;
import com.apply.diarypic.diary.entity.DiaryPhoto; // 기존 엔티티
import com.apply.diarypic.photo.repository.PhotoRepository; // Photo 엔티티를 다루는 레포지토리
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PhotoRecommendationService {

    private final AiServerService aiServerService;
    private final PhotoRepository photoRepository; // 임시 저장된 사진 정보를 가져오기 위함

    // ISO 8601 날짜/시간 포맷터 (자바 LocalDateTime -> String 변환 시 사용)
    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;


    /**
     * 사용자 ID와 필수 선택 사진 ID 목록을 받아 AI에게 사진 추천을 요청하고,
     * 추천된 사진 ID 목록을 반환합니다.
     *
     * @param userId 사용자 ID
     * @param uploadedPhotoIds 사용자가 업로드한 모든 사진의 ID 목록 (DB에 임시 저장된 상태)
     * @param mandatoryPhotoIds 사용자가 필수로 선택한 사진의 ID 목록
     * @return AI가 추천한 사진 ID 목록 (Mono)
     */
    public List<Long> getRecommendedPhotosFromAI(Long userId, List<Long> uploadedPhotoIds, List<Long> mandatoryPhotoIds) {
        // 1. 업로드된 사진 정보들을 DB에서 조회
        // 주의: 실제로는 Photo 엔티티에 isMandatory 필드를 추가하거나,
        //       요청 시점에 mandatoryPhotoIds와 비교하여 DTO를 구성해야 합니다.
        //       여기서는 설명을 위해 DiaryPhoto 엔티티를 사용한다고 가정합니다.
        //       DiaryPhoto 엔티티가 임시 사진 상태를 나타내고 userId를 가지고 있다고 가정.
        List<DiaryPhoto> userPhotos = photoRepository.findAllById(uploadedPhotoIds); // 실제로는 userId로 필터링 필요

        // 2. AiPhotoScoreRequestDto 리스트 생성
        List<AiPhotoScoreRequestDto> photosToScore = userPhotos.stream()
                .map(photo -> new AiPhotoScoreRequestDto(
                        photo.getId(),
                        photo.getPhotoUrl(),
                        photo.getShootingDateTime() != null ? photo.getShootingDateTime().format(ISO_LOCAL_DATE_TIME_FORMATTER) : null,
                        photo.getDetailedAddress(),
                        mandatoryPhotoIds.contains(photo.getId()) // 이 사진이 필수 선택 목록에 포함되어 있는지 여부
                ))
                .collect(Collectors.toList());

        // 3. AiServerService를 통해 AI 서버에 요청 (블로킹 방식으로 결과 기다림 - 실제 서비스에서는 비동기 처리 고려)
        // WebClient는 기본적으로 비동기이지만, 서비스 레이어에서 결과를 받아 처리해야 하므로 .block() 등을 사용할 수 있습니다.
        // 더 나은 방법은 Controller까지 Mono를 전달하는 것입니다.
        List<Long> recommendedPhotoIds = aiServerService.requestPhotoRecommendation(photosToScore).block(); // .block()은 데모용, 실제론 비동기 처리

        if (recommendedPhotoIds == null) {
            // AI 서버 통신 실패 또는 빈 응답 처리
            // 필수 사진만이라도 반환하거나, 다른 폴백 로직 수행
            return mandatoryPhotoIds; // 예시: 최소한 필수 사진은 반환
        }

        return recommendedPhotoIds;
    }
}