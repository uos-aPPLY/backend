package com.apply.diarypic.user.controller;

import com.apply.diarypic.global.security.UserPrincipal;
import com.apply.diarypic.user.dto.*;
import com.apply.diarypic.user.entity.User;
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
        User user = userService.getUserById(userPrincipal.getUserId());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PatchMapping("/nickname")
    public ResponseEntity<UserResponse> updateNickname(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                       @RequestBody @Valid UpdateNicknameRequest request) {
        User user = userService.updateNickname(userPrincipal.getUserId(), request.getNickname());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PatchMapping("/writing-style")
    public ResponseEntity<UserResponse> updateWritingStyle(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                           @RequestBody @Valid UpdateWritingStyleRequest request) {
        User user = userService.updateWritingStyle(userPrincipal.getUserId(), request.getPrompt());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PatchMapping("/alarm")
    public ResponseEntity<UserResponse> updateAlarm(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                    @RequestBody @Valid UpdateAlarmRequest request) {
        LocalTime alarmTime = LocalTime.of(request.getHour(), request.getMinute());

        User user = userService.updateAlarm(
                userPrincipal.getUserId(),
                request.getEnabled(),
                alarmTime
        );
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        userService.deleteUser(userPrincipal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
