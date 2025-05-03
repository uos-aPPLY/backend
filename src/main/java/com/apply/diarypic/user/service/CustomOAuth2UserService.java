package com.apply.diarypic.user.service;

import com.apply.diarypic.global.security.CustomOAuth2User;
import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);

        String provider = request.getClientRegistration().getRegistrationId(); // ex. "kakao"
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 카카오는 "id" 필드가 고유 식별자
        String snsUserId = String.valueOf(attributes.get("id"));

        log.debug("🔐 소셜 로그인 정보: provider={}, snsUserId={}", provider, snsUserId);

        // 사용자 존재 여부 확인
        Optional<User> userOpt = userRepository.findBySnsProviderAndSnsUserId(provider, snsUserId);

        if (userOpt.isEmpty()) {
            // 새 사용자 등록 (닉네임, 프로필 등은 앱 내에서 따로 입력받음)
            User newUser = User.builder()
                    .snsProvider(provider)
                    .snsUserId(snsUserId)
                    .writingStylePrompt("기본 말투입니다.")
                    .alarmEnabled(false)
                    .build();

            userRepository.save(newUser);

            log.info("✅ 신규 사용자 등록 완료: provider={}, snsUserId={}", provider, snsUserId);
        }

        return new CustomOAuth2User(attributes, provider, snsUserId);
    }
}
