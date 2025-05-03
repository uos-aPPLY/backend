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
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);

        String provider = request.getClientRegistration().getRegistrationId(); // "kakao", "google"
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 설정 파일(yml)에 정의된 user-name-attribute 값을 사용 (Kakao: "id", Google: "sub")
        String userNameAttributeName = request.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();

        String snsUserId = String.valueOf(attributes.get(userNameAttributeName));

        // snsUserId가 null이거나 "null" 문자열인지 확인
        if (snsUserId == null || snsUserId.equalsIgnoreCase("null")) {
            log.error("🚨 소셜 로그인 ID를 가져올 수 없습니다. provider={}, userNameAttributeName={}, attributes={}", provider, userNameAttributeName, attributes);
            throw new OAuth2AuthenticationException("OAuth2 provider (" + provider + ") 로부터 유효한 사용자 ID를 가져오지 못했습니다.");
        }

        log.debug("🔐 소셜 로그인 정보: provider={}, userNameAttribute={}, snsUserId={}", provider, userNameAttributeName, snsUserId);


        // 사용자 존재 여부 확인
        Optional<User> userOpt = userRepository.findBySnsProviderAndSnsUserId(provider, snsUserId);

        User user; // CustomOAuth2User에 전달할 사용자 객체 참조
        if (userOpt.isEmpty()) {
            // 새 사용자 등록
            user = User.builder()
                    .snsProvider(provider)
                    .snsUserId(snsUserId)
                    .writingStylePrompt("기본 말투입니다.")
                    .alarmEnabled(false)
                    .build();

            userRepository.save(user);

            log.info("✅ 신규 사용자 등록 완료: provider={}, snsUserId={}", provider, snsUserId);
        } else {
            user = userOpt.get(); // 기존 사용자
            log.info("👤 기존 사용자 로그인: provider={}, snsUserId={}", provider, snsUserId);
        }
        return new CustomOAuth2User(attributes, provider, snsUserId /*, user.getId() */);
    }
}