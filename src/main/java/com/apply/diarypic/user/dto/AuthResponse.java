package com.apply.diarypic.user.dto;

public record AuthResponse(
        String accessToken, // 앱이 사용할 자체 Access Token
        long accessTokenExpiresIn, // Access Token 만료 시간 (밀리초 단위)
        UserInfoResponse userInfo // 사용자 정보 DTO
) {}