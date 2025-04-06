package com.apply.diarypic.global.security;

import com.apply.diarypic.global.security.jwt.JwtProvider;
import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        log.info("✅ OAuth2 인증 성공 - provider: {}, oauthId: {}", oAuth2User.getProvider(), oAuth2User.getOauthId());

        User user = userRepository.findBySnsProviderAndSnsUserId(oAuth2User.getProvider(), oAuth2User.getOauthId())
                .orElseThrow(() -> new IllegalArgumentException("⛔ 로그인된 유저를 찾을 수 없습니다."));

        UserPrincipal userPrincipal = new UserPrincipal(user.getId(), user.getSnsProvider());
        String accessToken = jwtProvider.createToken(userPrincipal);

        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"accessToken\": \"" + accessToken + "\"}");
    }
}
