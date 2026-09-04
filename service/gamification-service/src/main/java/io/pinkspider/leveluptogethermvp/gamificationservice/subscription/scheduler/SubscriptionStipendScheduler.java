package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.scheduler;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application.SubscriptionStipendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * LUT-453: 구독자 일일 다이아 스티펜드 스케줄러.
 *
 * <p>지급일 경계는 UTC(저장 시간대와 동일) — 자정 직후 10분에 실행해 만료 경계의 애매함을 줄인다.
 * 멱등키(구독 ID, 지급일) 덕에 재실행·수동 재기동에도 중복 지급이 없다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionStipendScheduler {

    private final SubscriptionStipendService stipendService;

    @Scheduled(cron = "0 10 0 * * *", zone = "UTC")
    @SchedulerLock(
            name = "SubscriptionStipendScheduler_grantDailyStipends",
            lockAtMostFor = "PT30M",
            lockAtLeastFor = "PT1M")
    public void grantDailyStipends() {
        log.info("구독 스티펜드 스케줄러 시작");
        try {
            stipendService.grantDailyStipends();
        } catch (Exception e) {
            log.error("구독 스티펜드 스케줄러 오류", e);
        }
    }
}
