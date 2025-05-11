package com.apply.diarypic.user.service;

import com.apply.diarypic.global.security.jwt.JwtUtils;
import com.apply.diarypic.keyword.service.KeywordService;
import com.apply.diarypic.terms.service.TermsService;
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
        // 1. 소셜 플랫폼에서 사용자 정보 가져오기
        Map<String, Object> userInfoMap = switch (req.provider().toLowerCase()) {
            case "kakao" -> socialUserInfoService.getKakaoUserInfo(req.accessToken());
            case "google" -> socialUserInfoService.getGoogleUserInfo(req.accessToken());
            case "naver" -> socialUserInfoService.getNaverUserInfo(req.accessToken());
            default -> throw new IllegalArgumentException("지원하지 않는 provider: " + req.provider());
        };

        // 2. 사용자 고유 ID 및 소셜 프로필 닉네임 추출
        String snsUserId = null;
        String socialNickname = null;

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
            case "naver": // 네이버 케이스 추가
                snsUserId = (String) userInfoMap.get("id"); // 네이버는 'id' 필드를 사용
                socialNickname = (String) userInfoMap.get("nickname"); // 네이버는 'nickname' 또는 'name' 필드를 사용 (앱 설정에 따라 확인)
                if (socialNickname == null || socialNickname.isBlank()) {
                    socialNickname = (String) userInfoMap.get("name"); // 'nickname'이 없으면 'name'을 시도
                }
                break;
            default:
                throw new IllegalStateException("Provider 처리 오류");
        }

        // 소셜 닉네임이 없거나 비어있으면 기본값 생성
        if (socialNickname == null || socialNickname.isBlank()) {
            socialNickname = req.provider() + "_" + (snsUserId != null ? snsUserId.substring(0, Math.min(snsUserId.length(), 6)) : "user");
        }
        if (snsUserId == null) {
            throw new IllegalStateException("snsUserId를 추출할 수 없습니다.");
        }


        // 3. DB에서 사용자 조회 또는 생성
        final String finalProvider = req.provider().toLowerCase(); // 일관성을 위해 소문자로 처리
        final String finalSnsUserId = snsUserId;
        final String finalSocialNicknameForNewUser = socialNickname;

        User user = userRepository.findBySnsProviderAndSnsUserId(finalProvider, finalSnsUserId)
                .orElseGet(() -> {
                    log.info("✅ 신규 사용자 등록 시도: provider={}, snsUserId={}, nickname={}",
                            finalProvider, finalSnsUserId, finalSocialNicknameForNewUser);
                    User newUser = User.builder()
                            .snsProvider(finalProvider)
                            .snsUserId(finalSnsUserId)
                            .nickname(finalSocialNicknameForNewUser)
                            .writingStylePrompt("다정하고 친절한 말투로 일기를 작성해줘.") // 기본 말투 프롬프트 (기존 "기본 말투입니다." 에서 변경 가능)
                            .alarmEnabled(false)
                            .build();
                    User savedNewUser = userRepository.save(newUser);
                    // 신규 사용자에게 초기 추천 키워드 생성
                    keywordService.createInitialRecommendedKeywordsForUser(savedNewUser);
                    log.info("✅ 사용자 ID {} 에게 초기 추천 키워드 생성 완료.", savedNewUser.getId());
                    return savedNewUser;
                });

        log.info("👤 사용자 인증 성공: userId={}, provider={}, snsUserId={}, nickname={}",
                user.getId(), user.getSnsProvider(), user.getSnsUserId(), user.getNickname());

        // 4. 자체 Access Token 생성
        String appAccessToken = jwtUtils.generateAccessToken(user.getId());

        // 5. 응답 DTO 생성
        UserInfoResponse userInfoDto = new UserInfoResponse(user.getId(), user.getNickname());
        return new AuthResponse(appAccessToken, jwtUtils.getAccessTokenExpiresIn(), userInfoDto);
    }
}