package com.apply.diarypic.user.service;

import com.apply.diarypic.global.security.jwt.JwtUtils;
import com.apply.diarypic.user.dto.AuthRequest;
import com.apply.diarypic.user.dto.AuthResponse;
import com.apply.diarypic.user.dto.UserInfoResponse;
import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SocialUserInfoService socialUserInfoService;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;

    @Transactional
    public AuthResponse authenticate(AuthRequest req) {
        // 1. 소셜 플랫폼에서 사용자 정보 가져오기
        Map<String, Object> userInfoMap = switch (req.provider().toLowerCase()) {
            case "kakao" -> socialUserInfoService.getKakaoUserInfo(req.accessToken());
            case "google" -> socialUserInfoService.getGoogleUserInfo(req.accessToken());
            default -> throw new IllegalArgumentException("지원하지 않는 provider: " + req.provider());
        };

        // 2. 사용자 고유 ID 및 닉네임(선택적) 추출
        String snsUserId = switch (req.provider().toLowerCase()) {
            case "kakao" -> String.valueOf(userInfoMap.get("id"));
            case "google" -> (String) userInfoMap.get("sub");
            default -> throw new IllegalStateException("Provider 처리 오류"); // 발생하면 안됨
        };

        String nickname = null; // 닉네임 초기화
        if ("kakao".equalsIgnoreCase(req.provider())) {
            Map<String, Object> properties = (Map<String, Object>) userInfoMap.get("properties");
            if (properties != null) {
                nickname = (String) properties.get("nickname");
            }
        } else if ("google".equalsIgnoreCase(req.provider())) {
            nickname = (String) userInfoMap.get("name");
        }
        // 닉네임이 없으면 기본값 생성
        if (nickname == null || nickname.isBlank()) {
            nickname = req.provider() + "_" + snsUserId.substring(0, Math.min(snsUserId.length(), 6));
        }


        // 3. DB에서 사용자 조회 또는 생성
        final String finalNickname = nickname; // 람다에서 사용하기 위해 final 변수로
        User user = userRepository.findBySnsProviderAndSnsUserId(req.provider(), snsUserId)
                .orElseGet(() -> {
                    log.info("✅ 신규 사용자 등록 시도: provider={}, snsUserId={}, nickname={}", req.provider(), snsUserId, finalNickname);
                    User newUser = User.builder()
                            .snsProvider(req.provider())
                            .snsUserId(snsUserId)
                            .nickname(finalNickname) // 소셜 플랫폼 닉네임 또는 기본값
                            .writingStylePrompt("기본 말투입니다.") // 기본값 설정
                            .alarmEnabled(false) // 기본값 설정
                            .build();
                    return userRepository.save(newUser);
                });

        // (선택 사항) 기존 사용자의 경우 닉네임 업데이트 로직 추가 가능
        if (!user.getNickname().equals(finalNickname)) {
            log.info("🔄 사용자 닉네임 업데이트: userId={}, oldNickname={}, newNickname={}", user.getId(), user.getNickname(), finalNickname);
            user.setNickname(finalNickname); // User 엔티티에 @Setter(AccessLevel.PACKAGE) 등 필요
            // userRepository.save(user); // @Transactional에 의해 더티 체킹으로 업데이트됨
        }


        log.info("👤 사용자 인증 성공: userId={}, provider={}, snsUserId={}", user.getId(), req.provider(), snsUserId);

        // 4. 자체 Access Token 생성
        String appAccessToken = jwtUtils.generateAccessToken(user.getId());

        // 5. 응답 DTO 생성 (UserInfoResponse 사용)
        UserInfoResponse userInfoDto = new UserInfoResponse(user.getId(), user.getNickname());
        return new AuthResponse(appAccessToken, jwtUtils.getAccessTokenExpiresIn(), userInfoDto);
    }
}