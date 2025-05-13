package com.apply.diarypic.user.service;

import com.apply.diarypic.album.repository.AlbumRepository;
import com.apply.diarypic.diary.entity.Diary;
import com.apply.diarypic.diary.repository.DiaryRepository;
import com.apply.diarypic.global.s3.S3Uploader;
import com.apply.diarypic.keyword.repository.KeywordRepository;
import com.apply.diarypic.photo.entity.DiaryPhoto;
import com.apply.diarypic.terms.repository.UserTermsAgreementRepository;
import com.apply.diarypic.user.dto.UserResponse;
import com.apply.diarypic.user.entity.User;
import com.apply.diarypic.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;
    private final KeywordRepository keywordRepository;
    private final UserTermsAgreementRepository userTermsAgreementRepository;
    private final S3Uploader s3Uploader;
    private final AlbumRepository albumRepository;

    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserInfoWithDiaryCounts(Long userId) {
        User user = getUserById(userId);
        LocalDate now = LocalDate.now();

        long totalDiariesCount = diaryRepository.countByUserAndDeletedAtIsNull(user);
        long yearDiariesCount = diaryRepository.countByUserAndYearAndDeletedAtIsNull(user, now.getYear());
        long monthDiariesCount = diaryRepository.countByUserAndYearAndMonthAndDeletedAtIsNull(user, now.getYear(), now.getMonthValue());

        return UserResponse.from(user, totalDiariesCount, yearDiariesCount, monthDiariesCount);
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("삭제할 사용자를 찾을 수 없습니다. ID: " + userId));

        log.info("사용자 삭제 절차 시작: userId={}", userId);

        // 1. 사용자의 모든 일기 조회
        List<Diary> diaries = diaryRepository.findByUserId(userId);
        log.info("사용자 ID {}의 일기 {}개 S3 파일 삭제 시작", userId, diaries.size());

        // 2. 각 일기에 포함된 사진들의 S3 파일 삭제
        for (Diary diary : diaries) {
            for (DiaryPhoto photo : diary.getDiaryPhotos()) {
                if (photo.getPhotoUrl() != null && !photo.getPhotoUrl().isEmpty()) {
                    try {
                        s3Uploader.deleteFileByUrl(photo.getPhotoUrl());
                        log.info("S3 파일 삭제 성공: {}", photo.getPhotoUrl());
                    } catch (Exception e) {
                        log.error("S3 파일 삭제 실패: {}. 원인: {}", photo.getPhotoUrl(), e.getMessage(), e);
                    }
                }
            }
        }
        log.info("사용자 ID {}의 S3 파일 삭제 완료 또는 시도 완료", userId);

        log.info("사용자 ID {}의 앨범 정보 삭제 시작", userId);
        albumRepository.deleteAllByUser(user);

        log.info("사용자 ID {}의 약관 동의 정보 삭제 시작", userId);
        userTermsAgreementRepository.deleteAllByUser(user);

        log.info("사용자 ID {}의 키워드 정보 삭제 시작", userId);
        keywordRepository.deleteAllByUser(user);

        log.info("사용자 ID {}의 일기 정보 삭제 시작 (DB)", userId);
        diaryRepository.deleteAll(diaries);

        log.info("사용자 ID {}의 User 엔티티 삭제 시작", userId);
        userRepository.delete(user);

        log.info("사용자 ID {} 삭제 완료.", userId);
    }
}