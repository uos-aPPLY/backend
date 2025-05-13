package com.apply.diarypic.global.scheduler;

import com.apply.diarypic.diary.service.DiaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {

    private final DiaryService diaryService;

    // 매일 새벽 3시에 실행
    // 초 분 시 일 월 요일 (년도 생략 가능)
    // "0 0 3 * * ?" -> 매일 새벽 3시 0분 0초
    @Scheduled(cron = "0 0 3 * * ?")
    public void autoPermanentlyDeleteOldTrashedDiaries() {
        log.info("자동 영구 삭제 스케줄러: 30일 지난 휴지통 일기 삭제 시작...");
        try {
            diaryService.permanentlyDeleteOldTrashedDiaries();
            log.info("자동 영구 삭제 스케줄러: 작업 완료.");
        } catch (Exception e) {
            log.error("자동 영구 삭제 스케줄러 실행 중 오류 발생", e);
        }
    }
}