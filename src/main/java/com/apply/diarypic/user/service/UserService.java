package com.apply.diarypic.user.service;

import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }

    @Transactional
    public User updateNickname(Long userId, String nickname) {
        User user = getUserById(userId);
        user.setNickname(nickname);
        return user;
    }

    @Transactional
    public User updateWritingStyle(Long userId, String prompt) {
        User user = getUserById(userId);
        user.setWritingStylePrompt(prompt);
        return user;
    }

    @Transactional
    public User updateAlarm(Long userId, Boolean enabled, LocalTime alarmTime) {
        User user = getUserById(userId);
        user.setAlarmEnabled(enabled);
        user.setAlarmTime(alarmTime);
        return user;
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = getUserById(userId);
        userRepository.delete(user);
    }
}
