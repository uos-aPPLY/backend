package com.apply.diarypic.user.service;

import com.apply.diarypic.global.security.jwt.JwtUtils;
import com.apply.diarypic.keyword.service.KeywordService;
import com.apply.diarypic.user.dto.AuthRequest;
import com.apply.diarypic.user.dto.AuthResponse;
import com.apply.diarypic.user.dto.UserInfoResponse;
import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SocialUserInfoService socialUserInfoService;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final KeywordService keywordService;

    @Transactional
    public AuthResponse authenticate(AuthRequest req) {
        Map<String, Object> userInfoMap = switch (req.provider().toLowerCase()) {
            case "kakao" -> socialUserInfoService.getKakaoUserInfo(req.accessToken());
            case "google" -> socialUserInfoService.getGoogleUserInfo(req.accessToken());
            case "naver" -> socialUserInfoService.getNaverUserInfo(req.accessToken());
            case "apple" -> socialUserInfoService.getAppleUserInfo(req.accessToken());
            default -> throw new IllegalArgumentException("지원하지 않는 provider: " + req.provider());
        };

        String snsUserId = null;
        String socialNickname = null;
        String emailFromApple = null;

        switch (req.provider().toLowerCase()) {
            case "kakao":
                snsUserId = String.valueOf(userInfoMap.get("id"));
                Map<String, Object> properties = (Map<String, Object>) userInfoMap.get("properties");
                if (properties != null) {
                    socialNickname = (String) properties.get("nickname");
                }
                break;
            case "google":
                snsUserId = (String) userInfoMap.get("sub");
                socialNickname = (String) userInfoMap.get("name");
                break;
            case "naver":
                if (userInfoMap == null) {
                    throw new IllegalStateException("네이버 사용자 정보 응답이 null입니다 (SocialUserInfoService로부터).");
                }
                snsUserId = (String) userInfoMap.get("id");
                socialNickname = (String) userInfoMap.get("nickname");
                if (socialNickname == null || socialNickname.isBlank()) {
                    socialNickname = (String) userInfoMap.get("name");
                }
                break;
            case "apple":
                snsUserId = (String) userInfoMap.get("sub");
                emailFromApple = (String) userInfoMap.get("email");
                if (emailFromApple != null && !emailFromApple.isBlank()) {
                    socialNickname = emailFromApple.split("@")[0];
                }
                break;
            default:
                throw new IllegalStateException("Provider 처리 오류");
        }

        if (socialNickname == null || socialNickname.isBlank()) {
            socialNickname = req.provider() + "_" + (snsUserId != null ? snsUserId.substring(0, Math.min(snsUserId.length(), 6)) : "user");
        }
        if (snsUserId == null) {
            throw new IllegalStateException("snsUserId를 추출할 수 없습니다.");
        }

        final String finalProvider = req.provider().toLowerCase();
        final String finalSnsUserId = snsUserId;
        final String finalSocialNicknameForNewUser = socialNickname;

        User user = userRepository.findBySnsProviderAndSnsUserId(finalProvider, finalSnsUserId)
                .orElseGet(() -> {
                    log.info("✅ 신규 사용자 등록 시도: provider={}, snsUserId={}, nickname={}",
                            finalProvider, finalSnsUserId, finalSocialNicknameForNewUser);
                    User newUser = User.builder()
                            .snsProvider(finalProvider)
                            .snsUserId(finalSnsUserId)
                            .nickname(null)
                            .writingStylePrompt("기본 말투입니다.")
                            .writingStyleNumber(1)
                            .alarmEnabled(false)
                            .build();
                    User savedNewUser = userRepository.save(newUser);
                    keywordService.createInitialRecommendedKeywordsForUser(savedNewUser);
                    log.info("✅ 사용자 ID {} 에게 초기 추천 키워드 생성 완료.", savedNewUser.getId());
                    return savedNewUser;
                });

        log.info("👤 사용자 인증 성공: userId={}, provider={}, snsUserId={}, nickname={}",
                user.getId(), user.getSnsProvider(), user.getSnsUserId(), user.getNickname());

        String appAccessToken = jwtUtils.generateAccessToken(user.getId());
        String appRefreshToken = jwtUtils.generateRefreshToken(user.getId());

        user.updateRefreshToken(appRefreshToken);
        userRepository.save(user);

        UserInfoResponse userInfoDto = new UserInfoResponse(user.getId(), user.getNickname());
        return new AuthResponse(
                appAccessToken,
                jwtUtils.getAccessTokenExpiresIn(),
                appRefreshToken,
                jwtUtils.getRefreshTokenExpiresIn(),
                userInfoDto
        );
    }

    @Transactional
    public AuthResponse refreshAccessToken(String refreshToken) {
        if (!jwtUtils.validateRefreshToken(refreshToken)) {
            throw new BadCredentialsException("유효하지 않은 리프레시 토큰입니다.");
        }

        Long userId = jwtUtils.getUserIdFromRefreshToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("리프레시 토큰에 해당하는 사용자를 찾을 수 없습니다."));

        if (!refreshToken.equals(user.getRefreshToken())) {
            user.updateRefreshToken(null);
            userRepository.save(user);
            throw new BadCredentialsException("리프레시 토큰이 일치하지 않거나 만료되었습니다. 다시 로그인해주세요.");
        }

        String newAccessToken = jwtUtils.generateAccessToken(userId);

        UserInfoResponse userInfoDto = new UserInfoResponse(user.getId(), user.getNickname());

        return new AuthResponse(
                newAccessToken,
                jwtUtils.getAccessTokenExpiresIn(),
                refreshToken,
                jwtUtils.getRefreshTokenExpiresIn(),
                userInfoDto
        );
    }
}