package com.apply.diarypic.user.service;

import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.diary.entity.DiaryPhoto;
import com.apply.diarypic.diary.repository.DiaryRepository;
import com.apply.diarypic.global.s3.S3Uploader;
import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;
    private final S3Uploader s3Uploader;

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

    /**
     * 사용자 삭제:
     * 1) 해당 사용자의 모든 Diary와 DiaryPhoto를 S3에서 삭제
     * 2) DB에서 User 엔티티를 삭제 (Cascade.ALL 으로 Diary+Photo도 함께 삭제)
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 1) S3에 저장된 사진 전부 삭제
        List<Diary> diaries = diaryRepository.findByUserId(userId);
        for (Diary diary : diaries) {
            for (DiaryPhoto photo : diary.getDiaryPhotos()) {
                try {
                    s3Uploader.delete(photo.getPhotoUrl());
                } catch (Exception e) {
                    // 로그만 남기고 계속
                    log.error("S3 사진 삭제 실패: {}", photo.getPhotoUrl(), e);
                }
            }
        }

        // 2) DB에서 User 삭제 (cascade 옵션에 따라 Diary/Photo가 함께 삭제)
        diaryRepository.deleteAll(diaries);
        userRepository.delete(user);
    }
}
