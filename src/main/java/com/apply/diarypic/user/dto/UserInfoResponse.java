package com.apply.diarypic.user.dto;

public record UserInfoResponse(
        Long id,
        String nickname
        // 필요하다면 다른 필드 추가 (예: email, profileImageUrl 등)
) {}