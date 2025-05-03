package com.apply.diarypic.user.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
public class UserResponse {
    private Long id;
    private String snsProvider;
    private String snsUserId;
    private String nickname;
    private String writingStylePrompt;
    private Boolean alarmEnabled;
    private LocalTime alarmTime;

    public static UserResponse from(com.apply.diarypic.user.entity.User user) {
        return UserResponse.builder()
                .id(user.getId())
                .snsProvider(user.getSnsProvider())
                .snsUserId(user.getSnsUserId())
                .nickname(user.getNickname())
                .writingStylePrompt(user.getWritingStylePrompt())
                .alarmEnabled(user.getAlarmEnabled())
                .alarmTime(user.getAlarmTime())
                .build();
    }
}
