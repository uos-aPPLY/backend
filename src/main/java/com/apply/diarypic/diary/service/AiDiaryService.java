package com.apply.diarypic.diary.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;

@Slf4j
@Service
public class AiDiaryService {

    private final WebClient webClient;

    public AiDiaryService(WebClient.Builder webClientBuilder) {
        // AI 서버의 베이스 URL을 설정합니다.
        this.webClient = webClientBuilder.baseUrl("http://ai-server.example.com/api").build();
    }

    /**
     * 최종 사진 URL 리스트를 기반으로 AI 서버에 일기 내용을 생성 요청하고, 생성된 내용을 반환합니다.
     *
     * @param photoUrls 최종 사진 URL 리스트
     * @return 생성된 일기 내용
     */
    public String generateDiaryContent(List<String> photoUrls) {
        try {
            // 예시: AI 서버의 /generate 엔드포인트에 POST 요청
            String generatedContent = webClient.post()
                    .uri("/generate")
                    .bodyValue(new PhotoUrlRequest(photoUrls))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return generatedContent;
        } catch (Exception e) {
            log.error("AI 서버로부터 일기 내용 생성 실패", e);
            throw new RuntimeException("AI 서버로부터 일기 내용 생성에 실패했습니다.");
        }
    }

    // AI 서버 요청용 내부 DTO
    public static class PhotoUrlRequest {
        private List<String> photoUrls;

        public PhotoUrlRequest(List<String> photoUrls) {
            this.photoUrls = photoUrls;
        }

        public List<String> getPhotoUrls() {
            return photoUrls;
        }

        public void setPhotoUrls(List<String> photoUrls) {
            this.photoUrls = photoUrls;
        }
    }
}
