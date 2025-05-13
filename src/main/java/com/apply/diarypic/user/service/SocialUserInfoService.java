package com.apply.diarypic.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient; // WebClient 임포트
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialUserInfoService {

    private final WebClient webClient;

    public Map getKakaoUserInfo(String accessToken) {
        return webClient.get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=utf-8")
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Kakao API 에러: status={}, body={}", clientResponse.statusCode(), errorBody);
                                    if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED) {
                                        return Mono.error(new BadCredentialsException("유효하지 않은 카카오 토큰입니다."));
                                    }
                                    return Mono.error(new RuntimeException("카카오 API 호출 실패: " + clientResponse.statusCode()));
                                }))
                .bodyToMono(Map.class)
                .block();
    }

    public Map getGoogleUserInfo(String accessToken) {
        return webClient.get()
                .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Google API 에러: status={}, body={}", clientResponse.statusCode(), errorBody);
                                    if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED || clientResponse.statusCode() == HttpStatus.FORBIDDEN) {
                                        return Mono.error(new BadCredentialsException("유효하지 않은 구글 토큰입니다."));
                                    }
                                    return Mono.error(new RuntimeException("구글 API 호출 실패: " + clientResponse.statusCode()));
                                }))
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> getNaverUserInfo(String accessToken) {
        Map<String, Object> responseMap = webClient.get()
                .uri("https://openapi.naver.com/v1/nid/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Naver API 에러: status={}, body={}", clientResponse.statusCode(), errorBody);
                                    if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED || clientResponse.statusCode() == HttpStatus.FORBIDDEN) {
                                        return Mono.error(new BadCredentialsException("유효하지 않은 네이버 토큰입니다."));
                                    }
                                    return Mono.error(new RuntimeException("네이버 API 호출 실패: " + clientResponse.statusCode()));
                                }))
                .bodyToMono(Map.class)
                .block();

        if (responseMap != null && responseMap.containsKey("response")) {
            return (Map<String, Object>) responseMap.get("response");
        }
        throw new RuntimeException("네이버 사용자 정보를 가져오는데 실패했습니다. 응답 형식 불일치.");
    }
}