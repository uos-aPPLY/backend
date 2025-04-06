package com.apply.diarypic.global.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Getter
public class CustomOAuth2User implements OAuth2User {

    private final Map<String, Object> attributes;
    private final String provider;
    private final String oauthId;

    public CustomOAuth2User(Map<String, Object> attributes, String provider, String oauthId) {
        this.attributes = attributes;
        this.provider = provider;
        this.oauthId = oauthId;
    }

    @Override
    public String getName() {
        return oauthId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList(); // 권한 필요 시 수정 가능
    }
}
