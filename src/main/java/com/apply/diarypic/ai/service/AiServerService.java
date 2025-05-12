package com.apply.diarypic.ai.service;

import com.apply.diarypic.ai.dto.AiDiaryGenerateRequestDto;
import com.apply.diarypic.ai.dto.AiDiaryResponseDto;
import com.apply.diarypic.ai.dto.AiPhotoScoreRequestDto;
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

    public Mono<List<Long>> requestPhotoRecommendation(List<AiPhotoScoreRequestDto> photosToScore) {
        return this.webClient.post()
                .uri("/score")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(photosToScore)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Long>>() {})
                .doOnError(error -> log.error("Error during AI photo recommendation request: {}", error.getMessage()))
                .onErrorResume(error -> {
                    log.error("Failed to get photo recommendation from AI server. Error: {}", error.getMessage());
                    return Mono.just(Collections.emptyList()); // 오류 시 빈 리스트 반환
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
                    return Mono.just(new AiDiaryResponseDto("AI 서버 오류로 일기를 생성할 수 없습니다.", null)); // 오류 메시지 포함 DTO
                });
    }
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiServerService.class);
}