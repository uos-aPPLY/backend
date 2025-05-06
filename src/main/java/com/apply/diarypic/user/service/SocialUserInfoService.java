package com.apply.diarypic.user.service;

import lombok.RequiredArgsConstructor; // 이 어노테이션이 final 필드에 대한 생성자를 만들어줍니다.
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
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성
public class SocialUserInfoService {

    private final WebClient webClient; // final로 선언하고 생성자 주입 (Lombok이 처리)

    public Map getKakaoUserInfo(String accessToken) {
        // 여기에 기존 getKakaoUserInfo 로직 그대로 사용 (webClient는 주입받은 것 사용)
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
        // 여기에 기존 getGoogleUserInfo 로직 그대로 사용 (webClient는 주입받은 것 사용)
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
}