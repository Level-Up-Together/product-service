package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.scheduler;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application.SubscriptionLedgerReconcileService;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.DailyLedgerReconcileSummary;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * LUT-677: Android 구독 원장 일일 정합 — RTDN 이 끊겨도(2026-10-17 dev Pub/Sub 구독 만료 사례) 다음 날이면 원장 연번이 채워진다.
 *
 * <p>활성·최근 만료(7일) Android 구독마다 Play 를 재조회해 원장의 마지막 연번 다음부터 최신까지 Orders API 로 채우고(증분), 초회 주문의 금액을
 * STORE 로 정정한다. 한 유저씩 트랜잭션이라 한 명의 실패가 나머지를 막지 않는다. 백필 행 수는 {@code
 * lut_subscription_ledger_rows_written_total{source="daily"}} 에 쌓인다 — <b>0 이 정상</b>이고, 양수면 RTDN 경로를
 * 점검한다(Grafana 알람). 00:30 UTC = 스티펜드(00:10 UTC) 뒤, KPI 스냅샷(00:40 KST) 과는 날짜 경계가 달라 무관.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionLedgerReconcileScheduler {

    private final SubscriptionLedgerReconcileService reconcileService;

    @Scheduled(cron = "0 30 0 * * *", zone = "UTC")
    @SchedulerLock(
            name = "SubscriptionLedgerReconcileScheduler_reconcileAndroidLedgers",
            lockAtMostFor = "PT30M",
            lockAtLeastFor = "PT1M")
    public void reconcileAndroidLedgers() {
        try {
            run(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Android 원장 일일 정합 스케줄러 오류", e);
        }
    }

    /** 대상을 한 명씩 대조한다 — 한 명의 Play 재조회 실패는 건너뛰고(다음 날 재시도) 나머지는 계속 */
    DailyLedgerReconcileSummary run(LocalDateTime now) {
        List<String> targets = reconcileService.findAndroidDailyTargets(now);
        int synced = 0;
        int skipped = 0;
        int rowsWritten = 0;
        for (String userId : targets) {
            try {
                rowsWritten += reconcileService.reconcileGoogleIncremental(userId);
                synced++;
            } catch (Exception e) {
                skipped++;
                log.warn("Android 원장 일일 정합 건너뜀: userId={}, error={}", userId, e.getMessage());
            }
        }
        DailyLedgerReconcileSummary summary =
                new DailyLedgerReconcileSummary(targets.size(), synced, skipped, rowsWritten);
        if (rowsWritten > 0) {
            log.warn("Android 원장 일일 정합 — 백필 발생(RTDN 전달 경로 점검): {}", summary);
        } else {
            log.info("Android 원장 일일 정합 완료: {}", summary);
        }
        return summary;
    }
}
