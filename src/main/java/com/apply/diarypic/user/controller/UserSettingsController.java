package com.apply.diarypic.user.controller;

import com.apply.diarypic.global.security.UserPrincipal;
import com.apply.diarypic.user.dto.*;
// import com.apply.diarypic.user.entity.User; // User 반환 안 할 경우 필요 없을 수 있음
import com.apply.diarypic.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyInfo(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        UserResponse response = userService.getUserInfoWithDiaryCounts(userPrincipal.getUserId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/nickname")
    public ResponseEntity<Void> updateNickname(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                               @RequestBody @Valid UpdateNicknameRequest request) {
        userService.updateNickname(userPrincipal.getUserId(), request.getNickname());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/writing-style")
    public ResponseEntity<Void> updateWritingStyle(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                   @RequestBody @Valid UpdateWritingStyleRequest request) {
        userService.updateWritingStyle(userPrincipal.getUserId(), request.getPrompt());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/alarm")
    public ResponseEntity<Void> updateAlarm(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                            @RequestBody @Valid UpdateAlarmRequest request) {
        LocalTime alarmTime = LocalTime.of(request.getHour(), request.getMinute());
        userService.updateAlarm(
                userPrincipal.getUserId(),
                request.getEnabled(),
                alarmTime
        );
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        userService.deleteUser(userPrincipal.getUserId());
        return ResponseEntity.noContent().build();
    }
}