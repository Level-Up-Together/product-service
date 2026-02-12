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
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
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
    private final UserProfileCacheService userProfileCacheService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleTitleAcquired(TitleAcquiredEvent event) {
        try {
            UserProfileCache profile = userProfileCacheService.getUserProfile(event.userId());
            feedCommandService.createActivityFeed(
                event.userId(),
                profile.nickname(),
                profile.picture(),
                profile.level(),
                profile.titleName(),
                profile.titleRarity(),
                profile.titleColorCode(),
                ActivityType.TITLE_ACQUIRED,
                "칭호 획득: " + event.titleName(),
                event.rarity() + " 등급 칭호를 획득했습니다!",
                "TITLE",
                event.titleId(),
                event.titleName(),
                FeedVisibility.PUBLIC,
                null,
                null,
                null
            );
            log.info("피드 프로젝션 생성 - 칭호 획득: userId={}, titleName={}", event.userId(), event.titleName());
        } catch (Exception e) {
            log.error("피드 프로젝션 실패 - 칭호 획득: userId={}, error={}", event.userId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleAchievementCompleted(AchievementCompletedEvent event) {
        try {
            UserProfileCache profile = userProfileCacheService.getUserProfile(event.userId());
            feedCommandService.createActivityFeed(
                event.userId(),
                profile.nickname(),
                profile.picture(),
                profile.level(),
                profile.titleName(),
                profile.titleRarity(),
                profile.titleColorCode(),
                ActivityType.ACHIEVEMENT_UNLOCKED,
                "업적 달성: " + event.achievementName(),
                "업적을 달성했습니다!",
                "ACHIEVEMENT",
                event.achievementId(),
                event.achievementName(),
                FeedVisibility.PUBLIC,
                null,
                null,
                null
            );
            log.info("피드 프로젝션 생성 - 업적 달성: userId={}, achievementName={}", event.userId(), event.achievementName());
        } catch (Exception e) {
            log.error("피드 프로젝션 실패 - 업적 달성: userId={}, error={}", event.userId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleGuildJoined(GuildJoinedEvent event) {
        try {
            UserProfileCache profile = userProfileCacheService.getUserProfile(event.userId());
            feedCommandService.createActivityFeed(
                event.userId(),
                profile.nickname(),
                profile.picture(),
                profile.level(),
                profile.titleName(),
                profile.titleRarity(),
                profile.titleColorCode(),
                ActivityType.GUILD_JOINED,
                "길드 가입: " + event.guildName(),
                "길드에 가입했습니다!",
                "GUILD",
                event.guildId(),
                event.guildName(),
                FeedVisibility.PUBLIC,
                event.guildId(),
                null,
                null
            );
            log.info("피드 프로젝션 생성 - 길드 가입: userId={}, guildName={}", event.userId(), event.guildName());
        } catch (Exception e) {
            log.error("피드 프로젝션 실패 - 길드 가입: userId={}, error={}", event.userId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleFriendRequestAccepted(FriendRequestAcceptedEvent event) {
        try {
            // 수락한 사용자의 피드 생성
            UserProfileCache accepterProfile = userProfileCacheService.getUserProfile(event.userId());
            feedCommandService.createActivityFeed(
                event.userId(),
                accepterProfile.nickname(),
                accepterProfile.picture(),
                accepterProfile.level(),
                accepterProfile.titleName(),
                accepterProfile.titleRarity(),
                accepterProfile.titleColorCode(),
                ActivityType.FRIEND_ADDED,
                "새로운 친구!",
                "새로운 친구가 되었습니다!",
                null,
                event.friendshipId(),
                null,
                FeedVisibility.PUBLIC,
                null,
                null,
                null
            );

            // 요청을 보낸 사용자의 피드 생성
            UserProfileCache requesterProfile = userProfileCacheService.getUserProfile(event.requesterId());
            feedCommandService.createActivityFeed(
                event.requesterId(),
                requesterProfile.nickname(),
                requesterProfile.picture(),
                requesterProfile.level(),
                requesterProfile.titleName(),
                requesterProfile.titleRarity(),
                requesterProfile.titleColorCode(),
                ActivityType.FRIEND_ADDED,
                "새로운 친구!",
                "새로운 친구가 되었습니다!",
                null,
                event.friendshipId(),
                null,
                FeedVisibility.PUBLIC,
                null,
                null,
                null
            );
            log.info("피드 프로젝션 생성 - 친구 추가: accepterId={}, requesterId={}", event.userId(), event.requesterId());
        } catch (Exception e) {
            log.error("피드 프로젝션 실패 - 친구 추가: userId={}, error={}", event.userId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleUserLevelUp(UserLevelUpEvent event) {
        try {
            UserProfileCache profile = userProfileCacheService.getUserProfile(event.userId());
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
            UserProfileCache profile = userProfileCacheService.getUserProfile(event.userId());
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
        try {
            UserProfileCache profile = userProfileCacheService.getUserProfile(event.userId());
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleAttendanceStreak(AttendanceStreakEvent event) {
        try {
            UserProfileCache profile = userProfileCacheService.getUserProfile(event.userId());
            feedCommandService.createActivityFeed(
                event.userId(),
                profile.nickname(),
                profile.picture(),
                profile.level(),
                profile.titleName(),
                profile.titleRarity(),
                profile.titleColorCode(),
                ActivityType.ATTENDANCE_STREAK,
                event.streakDays() + "일 연속 출석!",
                event.streakDays() + "일 연속 출석을 달성했습니다!",
                null,
                null,
                null,
                FeedVisibility.PUBLIC,
                null,
                null,
                null
            );
            log.info("피드 프로젝션 생성 - 연속 출석: userId={}, streakDays={}", event.userId(), event.streakDays());
        } catch (Exception e) {
            log.error("피드 프로젝션 실패 - 연속 출석: userId={}, error={}", event.userId(), e.getMessage(), e);
        }
    }
}
