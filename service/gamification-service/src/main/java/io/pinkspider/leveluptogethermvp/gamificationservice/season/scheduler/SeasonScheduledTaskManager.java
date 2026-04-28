package io.pinkspider.leveluptogethermvp.gamificationservice.season.scheduler;

import io.pinkspider.leveluptogethermvp.gamificationservice.season.application.SeasonRewardProcessorService;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRewardHistoryRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 시즌 종료 시각에 단발성으로 보상 처리 작업을 등록/취소하는 매니저.
 *
 * - 시즌 생성 시 종료 시각에 단발 작업 등록
 * - 시즌 수정 시 기존 작업 취소 + 재등록
 * - 시즌 삭제/비활성화 시 작업 취소
 * - 서버 시작 시 DB에서 미처리 시즌을 읽어 작업 복원
 *
 * 안전망으로 SeasonRewardScheduler(매일 새벽 3시 KST)는 그대로 유지됨.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeasonScheduledTaskManager {

    // DB의 start_at/end_at은 UTC로 저장됨 (JVM/Hibernate UTC 설정).
    // KST 기반으로 atZone() 하면 9시간 어긋나므로 반드시 UTC로 해석해야 함.
    private static final ZoneOffset DB_ZONE = ZoneOffset.UTC;

    private final SeasonRepository seasonRepository;
    private final SeasonRewardHistoryRepository rewardHistoryRepository;
    private final SeasonRewardProcessorService rewardProcessorService;

    private final TaskScheduler taskScheduler = createTaskScheduler();
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    private static TaskScheduler createTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("season-reward-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 서버 시작 시 등록되어야 할 모든 시즌 작업을 복원
     */
    @EventListener(ApplicationReadyEvent.class)
    public void restorePendingSeasonTasks() {
        log.info("시즌 보상 작업 복원 시작");
        LocalDateTime now = LocalDateTime.now(DB_ZONE);

        // 활성 시즌 중 보상 미처리 + 미래 종료 또는 이미 종료된 시즌
        List<Season> seasons = seasonRepository.findEndedSeasonsWithoutRewards(now);
        for (Season season : seasons) {
            // 이미 종료된 시즌 → 즉시 처리
            triggerImmediately(season.getId(), "복원: 서버 다운 동안 종료됨");
        }

        // 미래 종료 예정 활성 시즌 → 종료 시각에 단발 등록
        List<Season> futureSeasons = seasonRepository.findFutureActiveSeasons(now);
        for (Season season : futureSeasons) {
            schedule(season);
        }
        log.info("시즌 보상 작업 복원 완료: ended={}, upcoming={}", seasons.size(), futureSeasons.size());
    }

    /**
     * 시즌 종료 시각에 보상 처리 작업 등록 (이미 등록된 작업이 있으면 취소 후 재등록)
     */
    public void schedule(Season season) {
        if (season == null || !Boolean.TRUE.equals(season.getIsActive()) || season.getEndAt() == null) {
            return;
        }

        Long seasonId = season.getId();
        cancel(seasonId);

        // end_at은 UTC LocalDateTime이므로 UTC offset으로 Instant 변환
        Instant endInstant = season.getEndAt().toInstant(DB_ZONE);
        Instant now = Instant.now();

        if (endInstant.isBefore(now)) {
            // 이미 종료된 시즌 → 보상 미처리면 즉시 처리
            if (!rewardHistoryRepository.existsBySeasonId(seasonId)) {
                triggerImmediately(seasonId, "이미 종료된 시즌");
            }
            return;
        }

        ScheduledFuture<?> future = taskScheduler.schedule(
            () -> safeProcessRewards(seasonId, "종료 시각 도달"),
            endInstant
        );
        scheduledTasks.put(seasonId, future);
        log.info("시즌 보상 작업 등록: seasonId={}, endAt={} (UTC)", seasonId, season.getEndAt());
    }

    /**
     * 등록된 시즌 작업 취소
     */
    public void cancel(Long seasonId) {
        ScheduledFuture<?> existing = scheduledTasks.remove(seasonId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
            log.info("시즌 보상 작업 취소: seasonId={}", seasonId);
        }
    }

    /**
     * 즉시 보상 처리 (어드민 트리거 또는 복원 시 사용)
     */
    public void triggerImmediately(Long seasonId, String reason) {
        log.info("시즌 보상 즉시 처리 트리거: seasonId={}, reason={}", seasonId, reason);
        taskScheduler.schedule(
            () -> safeProcessRewards(seasonId, reason),
            Instant.now()
        );
    }

    private void safeProcessRewards(Long seasonId, String reason) {
        try {
            // 멱등성: 이미 처리된 시즌이면 스킵 (processSeasonRewards 내부에서도 체크되지만 빠른 종료 위해)
            if (rewardHistoryRepository.existsBySeasonId(seasonId)) {
                log.info("시즌 보상 이미 처리됨, 스킵: seasonId={}, reason={}", seasonId, reason);
                scheduledTasks.remove(seasonId);
                return;
            }
            rewardProcessorService.processSeasonRewards(seasonId);
            log.info("시즌 보상 처리 완료: seasonId={}, reason={}", seasonId, reason);
        } catch (Exception e) {
            log.error("시즌 보상 처리 실패: seasonId={}, reason={}", seasonId, reason, e);
        } finally {
            scheduledTasks.remove(seasonId);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (taskScheduler instanceof ThreadPoolTaskScheduler tps) {
            tps.shutdown();
        }
    }
}
