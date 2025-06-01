package com.apply.diarypic.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialUserInfoService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${apple.auth.client-id}")
    private String appleAudience;

    @Value("${apple.auth.issuer}")
    private String appleIssuer;

    @Value("${apple.auth.jwk-set-uri}")
    private String appleJwkSetUri;

    private List<Map<String, String>> applePublicKeysCache;
    private long applePublicKeysCacheTime = 0;
    private static final long APPLE_KEYS_CACHE_TTL_MS = 3600000;

    public Map<String, Object> getAppleUserInfo(String identityToken) {
        log.info("Attempting to validate Apple identity token.");
        try {
            List<Map<String, String>> publicKeys = getApplePublicKeys();

            String headerKid = getKidFromTokenHeader(identityToken);

            Map<String, String> matchedKey = publicKeys.stream()
                    .filter(key -> key.get("kid").equals(headerKid))
                    .findFirst()
                    .orElseThrow(() -> new BadCredentialsException("Apple identity token의 kid와 일치하는 공개키를 찾을 수 없습니다."));

            PublicKey publicKey = generatePublicKey(matchedKey);

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(identityToken)
                    .getBody();

            if (!claims.getIssuer().equals(appleIssuer)) {
                throw new BadCredentialsException("Apple identity token issuer 불일치. Expected: " + appleIssuer + ", Actual: " + claims.getIssuer());
            }
            if (!claims.getAudience().contains(appleAudience)) {
                if (!claims.getAudience().equals(appleAudience)) {
                    throw new BadCredentialsException("Apple identity token audience 불일치. Expected: " + appleAudience + ", Actual: " + claims.getAudience());
                }
            }

            String appleUserId = claims.getSubject();
            String email = claims.get("email", String.class);

            log.info("Apple identity token 검증 성공. User ID: {}, Email: {}", appleUserId, email);

            Map<String, Object> userInfoMap = new HashMap<>();
            userInfoMap.put("sub", appleUserId);
            if (email != null) {
                userInfoMap.put("email", email);
            }
            return userInfoMap;

        } catch (BadCredentialsException e) {
            log.error("Apple identity token 검증 실패: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Apple identity token 처리 중 에러 발생: {}", e.getMessage(), e);
            throw new RuntimeException("Apple identity token 처리 중 에러: " + e.getMessage(), e);
        }
    }

    private List<Map<String, String>> getApplePublicKeys() {
        long currentTime = System.currentTimeMillis();
        if (applePublicKeysCache != null && (currentTime - applePublicKeysCacheTime < APPLE_KEYS_CACHE_TTL_MS)) {
            log.debug("Using cached Apple public keys.");
            return applePublicKeysCache;
        }

        log.info("Fetching Apple public keys from {}", appleJwkSetUri);
        String keysJson = webClient.get()
                .uri(appleJwkSetUri)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Apple JWK Set API 에러: status={}, body={}", clientResponse.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Apple JWK Set API 호출 실패: " + clientResponse.statusCode()));
                                })
                )
                .bodyToMono(String.class)
                .block();

        if (keysJson == null) {
            throw new RuntimeException("Apple JWK Set API 응답이 null입니다.");
        }

        try {
            Map<String, List<Map<String, String>>> keysResponse = objectMapper.readValue(keysJson, new TypeReference<>() {});
            this.applePublicKeysCache = keysResponse.get("keys");
            this.applePublicKeysCacheTime = currentTime;
            log.info("Successfully fetched and cached Apple public keys. Number of keys: {}", this.applePublicKeysCache.size());
            return this.applePublicKeysCache;
        } catch (IOException e) {
            log.error("Apple JWK Set JSON 파싱 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Apple JWK Set JSON 파싱 실패: " + e.getMessage(), e);
        }
    }

    private String getKidFromTokenHeader(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new BadCredentialsException("유효하지 않은 JWT 형식입니다.");
        }
        String headerSegment = parts[0];
        try {
            byte[] headerBytes = Base64.getUrlDecoder().decode(headerSegment);
            String headerJson = new String(headerBytes, StandardCharsets.UTF_8);
            Map<String, Object> headerMap = objectMapper.readValue(headerJson, new TypeReference<>() {});
            return (String) headerMap.get("kid");
        } catch (Exception e) {
            log.error("Apple identity token 헤더에서 kid 추출 실패: {}", e.getMessage(), e);
            throw new BadCredentialsException("Apple identity token 헤더 파싱 실패: " + e.getMessage(), e);
        }
    }


    private PublicKey generatePublicKey(Map<String, String> jwk) throws NoSuchAlgorithmException, InvalidKeySpecException {
        String n = jwk.get("n"); // Modulus
        String e = jwk.get("e"); // Exponent

        if (n == null || e == null) {
            throw new InvalidKeySpecException("JWK에서 n 또는 e 값이 누락되었습니다.");
        }

        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));

        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(spec);
    }

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