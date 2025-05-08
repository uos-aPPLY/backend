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

import java.util.List;

@Service
public class AiServerService {

    private final WebClient webClient;
    private final String aiServerBaseUrl;

    public AiServerService(WebClient.Builder webClientBuilder, @Value("${ai-server.base-url}") String aiServerBaseUrl) {
        this.aiServerBaseUrl = aiServerBaseUrl;
        // AI 서버 전용 WebClient 인스턴스 생성 (필요시 타임아웃 등 추가 설정)
        this.webClient = webClientBuilder.baseUrl(this.aiServerBaseUrl).build();
    }

    /**
     * AI 서버에 사진 추천(스코어링)을 요청합니다.
     *
     * @param photosToScore AI 서버로 보낼 사진 정보 리스트
     * @return AI가 추천한 사진 ID 리스트를 담은 Mono
     */
    public Mono<List<Long>> requestPhotoRecommendation(List<AiPhotoScoreRequestDto> photosToScore) {
        return this.webClient.post()
                .uri("/score") // AI 서버의 사진 추천 엔드포인트
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(photosToScore)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Long>>() {})
                .doOnError(error -> System.err.println("Error during photo recommendation request: " + error.getMessage())) // 간단한 에러 로깅
                .onErrorResume(error -> {
                    // 실제 프로덕션 코드에서는 더 정교한 에러 처리 필요
                    // 예를 들어, 기본 추천 로직을 수행하거나, 사용자에게 에러 메시지를 반환
                    System.err.println("Failed to get photo recommendation from AI server. Returning empty list or default.");
                    return Mono.empty(); // 또는 Mono.just(Collections.emptyList());
                });
    }

    /**
     * AI 서버에 일기 생성을 요청합니다.
     *
     * @param diaryRequest AI 서버로 보낼 일기 생성 요청 정보 (사용자 말투, 사진 정보 등)
     * @return AI가 생성한 일기 내용을 담은 Mono
     */
    public Mono<AiDiaryResponseDto> requestDiaryGeneration(AiDiaryGenerateRequestDto diaryRequest) {
        return this.webClient.post()
                .uri("/generate") // AI 서버의 일기 생성 엔드포인트
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(diaryRequest)
                .retrieve()
                .bodyToMono(AiDiaryResponseDto.class)
                .doOnError(error -> System.err.println("Error during diary generation request: " + error.getMessage()))
                .onErrorResume(error -> {
                    System.err.println("Failed to generate diary from AI server. Returning error response or default.");
                    // 기본 응답 또는 에러를 나타내는 DTO 반환 고려
                    return Mono.just(new AiDiaryResponseDto("AI 서버와의 통신 중 오류가 발생하여 일기를 생성할 수 없습니다."));
                });
    }
}