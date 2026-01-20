package io.pinkspider.leveluptogethermvp.gamificationservice.scheduler;

import io.pinkspider.leveluptogethermvp.gamificationservice.application.DailyMvpHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class DailyMvpHistoryScheduler {

    private final DailyMvpHistoryService dailyMvpHistoryService;

    /**
     * 매일 새벽 00:05에 전일 MVP 데이터 저장
     * 자정 직후 실행하여 전일(어제) 데이터를 완전히 캡처
     */
    @Scheduled(cron = "0 5 0 * * *")
    public void saveDailyMvpHistory() {
        log.info("일간 MVP 히스토리 저장 스케줄러 시작");

        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            dailyMvpHistoryService.captureAndSaveDailyMvp(yesterday);
            log.info("일간 MVP 히스토리 저장 완료: date={}", yesterday);
        } catch (Exception e) {
            log.error("일간 MVP 히스토리 저장 실패", e);
            // TODO: Slack 알림 또는 재시도 로직 추가 고려
        }

        log.info("일간 MVP 히스토리 저장 스케줄러 종료");
    }
}
