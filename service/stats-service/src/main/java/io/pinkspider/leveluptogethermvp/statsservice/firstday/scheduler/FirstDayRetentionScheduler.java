package io.pinkspider.leveluptogethermvp.statsservice.firstday.scheduler;

import io.pinkspider.leveluptogethermvp.statsservice.firstday.application.FirstDayRetentionService;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto.FirstDayRetentionRunResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.KpiDates;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * LUT-779: 매일 00:50 KST — 활동 flush(00:20)·KPI 스냅샷(00:40) 뒤에 첫날 행동 분류와 D1·D7·D30 을 채운다. 멱등이라 재실행해도
 * 결과가 같다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FirstDayRetentionScheduler {

    private final FirstDayRetentionService retentionService;

    @Scheduled(cron = "0 50 0 * * *", zone = "Asia/Seoul")
    @SchedulerLock(
            name = "FirstDayRetentionScheduler_run",
            lockAtMostFor = "PT30M",
            lockAtLeastFor = "PT1M")
    public void run() {
        run(KpiDates.today(), LocalDateTime.now(ZoneOffset.UTC));
    }

    void run(LocalDate today, LocalDateTime nowUtc) {
        try {
            FirstDayRetentionRunResponse result = retentionService.runDaily(today, nowUtc);
            log.info("첫날 리텐션 집계 완료: {}", result);
        } catch (Exception e) {
            log.error("첫날 리텐션 집계 실패: today={}", today, e);
        }
    }
}
