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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiServerService.class);


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
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AiImageScoringResponseDto.class)
                .doOnSuccess(response -> log.info("Successfully received photo scoring response from AI server. Recommended count: {}",
                        response != null && response.getRecommendedPhotoIds() != null ? response.getRecommendedPhotoIds().size() : "null response"))
                .doOnError(error -> log.error("Error during AI photo recommendation request: {}", error.getMessage(), error))
                .onErrorResume(error -> {
                    log.error("Failed to get photo recommendation from AI server. Returning empty response. Error: {}", error.getMessage());
                    return Mono.just(new AiImageScoringResponseDto(Collections.emptyList()));
                });
    }

    public Mono<AiDiaryResponseDto> requestDiaryGeneration(AiDiaryGenerateRequestDto diaryRequest) {
        log.info("Sending diary generation request to AI server.");
        return this.webClient.post()
                .uri("/generate") // AI 서버의 일기 생성 엔드포인트
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(diaryRequest)
                .retrieve()
                .bodyToMono(AiDiaryResponseDto.class)
                .doOnSuccess(response -> log.info("Successfully received diary generation response from AI server."))
                .doOnError(error -> {
                    log.error("Error during AI diary generation request: {}", error.getMessage(), error);
                })
                .onErrorResume(error -> {
                    log.error("Failed to generate diary from AI server. Error: {}", error.getMessage());
                    return Mono.just(new AiDiaryResponseDto("AI 서버 오류로 일기를 생성할 수 없습니다.", "error"));
                });
    }

    // 새로운 메소드 추가
    public Mono<AiDiaryResponseDto> requestDiaryModification(AiDiaryModifyRequestDto modifyRequest) {
        log.info("Sending diary modification request to AI server.");
        return this.webClient.post()
                .uri("/modify") // AI 서버의 일기 수정 엔드포인트
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(modifyRequest)
                .retrieve()
                .bodyToMono(AiDiaryResponseDto.class) // 응답 DTO는 생성 시와 동일 (AiDiaryResponseDto)
                .doOnSuccess(response -> log.info("Successfully received diary modification response from AI server."))
                .doOnError(error -> {
                    log.error("Error during AI diary modification request: {}", error.getMessage(), error);
                })
                .onErrorResume(error -> {
                    log.error("Failed to modify diary from AI server. Error: {}", error.getMessage());
                    // AI 서버 오류 시, 클라이언트에게 전달할 적절한 오류 응답 또는 예외 처리 필요
                    // 여기서는 생성과 유사하게 오류 메시지를 담은 DTO를 반환하거나,
                    // 혹은 특정 예외를 발생시켜 상위 서비스에서 처리하도록 할 수 있습니다.
                    // 예: return Mono.error(new RuntimeException("AI 일기 수정 중 오류 발생: " + error.getMessage()));
                    return Mono.just(new AiDiaryResponseDto("AI 서버 오류로 일기를 수정할 수 없습니다.", "error"));
                });
    }
}