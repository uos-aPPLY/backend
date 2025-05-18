package com.apply.diarypic.user.dto;

public record AuthResponse(
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken,
        long refreshTokenExpiresIn,
        UserInfoResponse userInfo
) {}