package com.apply.diarypic.user.dto;

public record AuthResponse(
        String accessToken,
        long accessTokenExpiresIn,
        UserInfoResponse userInfo
) {}