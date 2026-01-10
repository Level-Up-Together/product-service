package io.pinkspider.global.event.listener;

import io.pinkspider.global.event.GuildJoinedEvent;
import io.pinkspider.global.event.GuildMasterAssignedEvent;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.AchievementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 업적 관련 이벤트 리스너
 * - 길드 관련 이벤트 수신하여 업적 체크 처리
 * - 트랜잭션 커밋 후 비동기로 처리하여 주 트랜잭션에 영향 없음
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AchievementEventListener {

    private static final String EVENT_EXECUTOR = "eventExecutor";

    private final AchievementService achievementService;

    /**
     * 길드 가입 이벤트 처리
     * - 길드 가입 업적 체크
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGuildJoined(GuildJoinedEvent event) {
        log.debug("길드 가입 이벤트 수신: userId={}, guildId={}", event.userId(), event.guildId());
        try {
            achievementService.checkGuildJoinAchievement(event.userId());
        } catch (Exception e) {
            log.warn("길드 가입 업적 체크 실패: userId={}, guildId={}, error={}",
                event.userId(), event.guildId(), e.getMessage());
        }
    }

    /**
     * 길드 마스터 할당 이벤트 처리
     * - 길드 마스터 업적 체크
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGuildMasterAssigned(GuildMasterAssignedEvent event) {
        log.debug("길드 마스터 할당 이벤트 수신: userId={}, guildId={}", event.userId(), event.guildId());
        try {
            achievementService.checkGuildMasterAchievement(event.userId());
        } catch (Exception e) {
            log.warn("길드 마스터 업적 체크 실패: userId={}, guildId={}, error={}",
                event.userId(), event.guildId(), e.getMessage());
        }
    }
}
