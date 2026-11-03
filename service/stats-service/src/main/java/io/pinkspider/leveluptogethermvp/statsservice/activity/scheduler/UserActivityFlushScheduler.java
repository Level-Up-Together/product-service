package io.pinkspider.leveluptogethermvp.statsservice.activity.scheduler;

import io.pinkspider.leveluptogethermvp.statsservice.activity.application.UserActivityFlushService;
import io.pinkspider.leveluptogethermvp.statsservice.activity.domain.UserActivityKeys;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * LUT-598: 매일 00:20 KST 에 전날(그리고 그 전날) 활동 집합을 {@code user_daily_activity} 로 옮긴다.
 *
 * <p>그제도 다시 flush 하는 이유 — 배포·장애로 전날 00:20 실행이 빠졌어도 하루는 자동 보정된다. flush 는 멱등이라 중복 비용은 조회 1회뿐이다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserActivityFlushScheduler {

    static final int DAYS_BACK = 2;

    private final UserActivityFlushService flushService;

    @Scheduled(cron = "0 20 0 * * *", zone = "Asia/Seoul")
    @SchedulerLock(
            name = "UserActivityFlushScheduler_flushRecentDays",
            lockAtMostFor = "PT20M",
            lockAtLeastFor = "PT1M")
    public void flushRecentDays() {
        flushRecentDays(LocalDate.now(UserActivityKeys.KST));
    }

    void flushRecentDays(LocalDate today) {
        for (int back = DAYS_BACK; back >= 1; back--) {
            LocalDate date = today.minusDays(back);
            try {
                flushService.flush(date);
            } catch (Exception e) {
                log.error("활동 flush 실패: date={}", date, e);
            }
        }
    }
}
