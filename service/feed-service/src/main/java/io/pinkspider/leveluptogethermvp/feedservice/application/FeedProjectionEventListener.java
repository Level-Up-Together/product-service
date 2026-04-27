package io.pinkspider.leveluptogethermvp.feedservice.application;

import io.pinkspider.global.event.AchievementCompletedEvent;
import io.pinkspider.global.event.AttendanceStreakEvent;
import io.pinkspider.global.event.FriendRequestAcceptedEvent;
import io.pinkspider.global.event.GuildCreatedEvent;
import io.pinkspider.global.event.GuildJoinedEvent;
import io.pinkspider.global.event.GuildLevelUpEvent;
import io.pinkspider.global.event.TitleAcquiredEvent;
import io.pinkspider.global.event.TitleEquippedEvent;
import io.pinkspider.global.event.UserLevelUpEvent;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * CQRS Read Model - 피드 프로젝션 이벤트 리스너
 * 도메인 이벤트를 수신하여 활동 피드(Read Model)를 자동 생성한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeedProjectionEventListener {

    private final FeedCommandService feedCommandService;
    private final UserQueryFacade userQueryFacadeService;

    /**
     * 칭호 획득 피드 생성 비활성화 (QA-35: 자동 피드 과다 생성 축소)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleTitleAcquired(TitleAcquiredEvent event) {
        log.debug("칭호 획득 피드 생성 스킵 (비활성화): userId={}, titleId={}", event.userId(), event.titleId());
    }

    /**
     * 업적 달성 피드 생성 비활성화 (QA-35: 자동 피드 과다 생성 축소)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleAchievementCompleted(AchievementCompletedEvent event) {
        log.debug("업적 달성 피드 생성 스킵 (비활성화): userId={}, achievementId={}", event.userId(), event.achievementId());
    }

    /**
     * 길드 가입 피드 생성 비활성화 (QA-35: 자동 피드 과다 생성 축소)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleGuildJoined(GuildJoinedEvent event) {
        log.debug("길드 가입 피드 생성 스킵 (비활성화): userId={}, guildId={}", event.userId(), event.guildId());
    }

    /**
     * 친구 추가 피드 생성 비활성화 (QA-35: 자동 피드 과다 생성 축소)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleFriendRequestAccepted(FriendRequestAcceptedEvent event) {
        log.debug("친구 추가 피드 생성 스킵 (비활성화): accepterId={}, requesterId={}", event.userId(), event.requesterId());
    }

    private static final int LEVEL_FEED_MILESTONE_INTERVAL = 10;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleUserLevelUp(UserLevelUpEvent event) {
        // QA-35: 10단위 레벨 달성 시에만 피드 생성
        if (event.newLevel() <= 0 || event.newLevel() % LEVEL_FEED_MILESTONE_INTERVAL != 0) {
            log.debug("레벨업 피드 생성 스킵 (10단위 마일스톤 아님): userId={}, newLevel={}", event.userId(), event.newLevel());
            return;
        }

        try {
            UserProfileInfo profile = userQueryFacadeService.getUserProfile(event.userId());
            feedCommandService.createActivityFeed(
                event.userId(),
                profile.nickname(),
                profile.picture(),
                event.newLevel(),
                profile.titleName(),
                profile.titleRarity(),
                profile.titleColorCode(),
                ActivityType.LEVEL_UP,
                "레벨 " + event.newLevel() + " 달성!",
                "레벨 " + event.newLevel() + "에 도달했습니다!",
                null,
                null,
                null,
                FeedVisibility.PUBLIC,
                null,
                null,
                null
            );
            log.info("피드 프로젝션 생성 - 레벨업: userId={}, newLevel={}", event.userId(), event.newLevel());
        } catch (Exception e) {
            log.error("피드 프로젝션 실패 - 레벨업: userId={}, error={}", event.userId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleGuildCreated(GuildCreatedEvent event) {
        try {
            UserProfileInfo profile = userQueryFacadeService.getUserProfile(event.userId());
            feedCommandService.createActivityFeed(
                event.userId(),
                profile.nickname(),
                profile.picture(),
                profile.level(),
                profile.titleName(),
                profile.titleRarity(),
                profile.titleColorCode(),
                ActivityType.GUILD_CREATED,
                "길드 창설: " + event.guildName(),
                "새로운 길드를 만들었습니다!",
                "GUILD",
                event.guildId(),
                event.guildName(),
                FeedVisibility.PUBLIC,
                event.guildId(),
                null,
                null
            );
            log.info("피드 프로젝션 생성 - 길드 창설: userId={}, guildName={}", event.userId(), event.guildName());
        } catch (Exception e) {
            log.error("피드 프로젝션 실패 - 길드 창설: userId={}, error={}", event.userId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleGuildLevelUp(GuildLevelUpEvent event) {
        // QA-35: 10단위 레벨 달성 시에만 피드 생성
        if (event.newGuildLevel() <= 0 || event.newGuildLevel() % LEVEL_FEED_MILESTONE_INTERVAL != 0) {
            log.debug("길드 레벨업 피드 생성 스킵 (10단위 마일스톤 아님): guildId={}, newLevel={}",
                event.guildId(), event.newGuildLevel());
            return;
        }

        try {
            UserProfileInfo profile = userQueryFacadeService.getUserProfile(event.userId());
            feedCommandService.createActivityFeed(
                event.userId(),
                profile.nickname(),
                profile.picture(),
                profile.level(),
                profile.titleName(),
                profile.titleRarity(),
                profile.titleColorCode(),
                ActivityType.GUILD_LEVEL_UP,
                "길드 레벨업!",
                event.guildName() + " 길드가 레벨 " + event.newGuildLevel() + "에 도달했습니다!",
                "GUILD",
                event.guildId(),
                event.guildName(),
                FeedVisibility.PUBLIC,
                event.guildId(),
                null,
                null
            );
            log.info("피드 프로젝션 생성 - 길드 레벨업: userId={}, guildName={}, newLevel={}",
                event.userId(), event.guildName(), event.newGuildLevel());
        } catch (Exception e) {
            log.error("피드 프로젝션 실패 - 길드 레벨업: userId={}, error={}", event.userId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTitleEquipped(TitleEquippedEvent event) {
        try {
            int updatedCount = feedCommandService.updateFeedTitles(
                event.userId(), event.titleName(), event.titleRarity(), event.titleColorCode());
            log.info("피드 칭호 업데이트: userId={}, title={}, count={}", event.userId(), event.titleName(), updatedCount);
        } catch (Exception e) {
            log.error("피드 칭호 업데이트 실패: userId={}, error={}", event.userId(), e.getMessage(), e);
        }
    }

    /**
     * 연속 출석 피드 생성 비활성화 (QA-35: 자동 피드 과다 생성 축소)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleAttendanceStreak(AttendanceStreakEvent event) {
        log.debug("연속 출석 피드 생성 스킵 (비활성화): userId={}, streakDays={}", event.userId(), event.streakDays());
    }
}
