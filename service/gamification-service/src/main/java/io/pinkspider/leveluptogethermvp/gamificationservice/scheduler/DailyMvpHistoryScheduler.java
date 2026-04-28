package io.pinkspider.leveluptogethermvp.gamificationservice.scheduler;

import io.pinkspider.leveluptogethermvp.gamificationservice.application.DailyMvpHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class DailyMvpHistoryScheduler {

    private final DailyMvpHistoryService dailyMvpHistoryService;

    /**
     * 매일 새벽 00:00 KST에 전일 MVP 데이터 저장 (Asia/Seoul 타임존)
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "DailyMvpHistoryScheduler_kst", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void saveDailyMvpHistoryKst() {
        saveDailyMvpHistoryForTimezone("Asia/Seoul");
    }

    /**
     * 매일 새벽 00:00 AST에 전일 MVP 데이터 저장 (Asia/Riyadh 타임존)
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Riyadh")
    @SchedulerLock(name = "DailyMvpHistoryScheduler_ast", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void saveDailyMvpHistoryAst() {
        saveDailyMvpHistoryForTimezone("Asia/Riyadh");
    }

    /**
     * 매일 새벽 00:00 UTC에 전일 MVP 데이터 저장 (UTC 타임존)
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    @SchedulerLock(name = "DailyMvpHistoryScheduler_utc", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void saveDailyMvpHistoryUtc() {
        saveDailyMvpHistoryForTimezone("UTC");
    }

    private void saveDailyMvpHistoryForTimezone(String timezone) {
        log.info("일간 MVP 히스토리 저장 스케줄러 시작: timezone={}", timezone);

        try {
            ZoneId zone = ZoneId.of(timezone);
            LocalDate yesterday = LocalDate.now(zone).minusDays(1);
            dailyMvpHistoryService.captureAndSaveDailyMvp(yesterday, timezone);
            log.info("일간 MVP 히스토리 저장 완료: date={}, timezone={}", yesterday, timezone);
        } catch (Exception e) {
            log.error("일간 MVP 히스토리 저장 실패: timezone={}", timezone, e);
        }

        log.info("일간 MVP 히스토리 저장 스케줄러 종료: timezone={}", timezone);
    }
}
