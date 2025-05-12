package com.apply.diarypic.ai.service;

import com.apply.diarypic.ai.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Service
public class AiServerService {

    private final WebClient webClient;

    public AiServerService(WebClient.Builder webClientBuilder, @Value("${ai-server.base-url}") String aiServerBaseUrl) {
        this.webClient = webClientBuilder.baseUrl(aiServerBaseUrl).build();
    }

    public Mono<AiImageScoringResponseDto> requestPhotoRecommendation(AiImageScoringRequestDto request) {
        log.info("Sending photo scoring request to AI server. Image count: {}, Reference image count: {}",
                request.getImages() != null ? request.getImages().size() : 0,
                request.getReferenceImages() != null ? request.getReferenceImages().size() : 0);

        return this.webClient.post()
                .uri("/score") // AI 서버의 사진 추천(스코어링) 엔드포인트
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request) // 새로운 요청 DTO 사용
                .retrieve()
                .bodyToMono(AiImageScoringResponseDto.class) // 새로운 응답 DTO 사용
                .doOnSuccess(response -> log.info("Successfully received photo scoring response from AI server. Recommended count: {}",
                        response != null && response.getRecommendedPhotoIds() != null ? response.getRecommendedPhotoIds().size() : "null response"))
                .doOnError(error -> log.error("Error during AI photo recommendation request: {}", error.getMessage(), error))
                .onErrorResume(error -> {
                    log.error("Failed to get photo recommendation from AI server. Returning empty response. Error: {}", error.getMessage());
                    // 오류 발생 시 빈 추천 ID 목록을 가진 응답 객체 반환
                    return Mono.just(new AiImageScoringResponseDto(Collections.emptyList()));
                });
    }

    public Mono<AiDiaryResponseDto> requestDiaryGeneration(AiDiaryGenerateRequestDto diaryRequest) {
        return this.webClient.post()
                .uri("/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(diaryRequest)
                .retrieve()
                .bodyToMono(AiDiaryResponseDto.class)
                .doOnError(error -> log.error("Error during AI diary generation request: {}", error.getMessage()))
                .onErrorResume(error -> {
                    log.error("Failed to generate diary from AI server. Error: {}", error.getMessage());
                    return Mono.just(new AiDiaryResponseDto("AI 서버 오류로 일기를 생성할 수 없습니다.", "error")); // 오류 메시지 포함 DTO
                });
    }
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiServerService.class);
}