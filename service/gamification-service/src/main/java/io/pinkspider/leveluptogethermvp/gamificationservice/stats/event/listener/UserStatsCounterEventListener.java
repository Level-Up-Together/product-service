package io.pinkspider.leveluptogethermvp.gamificationservice.stats.event.listener;

import io.pinkspider.global.event.FeedCommentDeletedEvent;
import io.pinkspider.global.event.FeedCommentEvent;
import io.pinkspider.global.event.FeedLikedEvent;
import io.pinkspider.global.event.FeedUnlikedEvent;
import io.pinkspider.global.event.FriendRemovedEvent;
import io.pinkspider.global.event.FriendRequestAcceptedEvent;
import io.pinkspider.global.event.GuildJoinedEvent;
import io.pinkspider.global.event.MissionCommentDeletedEvent;
import io.pinkspider.global.event.MissionCommentEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.gamificationservice.stats.application.UserStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 소셜 카운터 이벤트 리스너
 * - 좋아요/친구 변경 이벤트를 수신하여 UserStats 카운터 업데이트
 * - 트랜잭션 커밋 후 비동기로 처리
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserStatsCounterEventListener {

    private static final String EVENT_EXECUTOR = "eventExecutor";

    private final UserStatsService userStatsService;
    private final AchievementService achievementService;

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFeedLiked(FeedLikedEvent event) {
        try {
            userStatsService.incrementLikesReceived(event.feedOwnerId());
            achievementService.checkAchievementsByDataSource(event.feedOwnerId(), "FEED_SERVICE");
            log.debug("좋아요 카운터 업데이트: feedOwnerId={}, feedId={}", event.feedOwnerId(), event.feedId());
        } catch (Exception e) {
            log.warn("좋아요 카운터 업데이트 실패: feedOwnerId={}, error={}", event.feedOwnerId(), e.getMessage());
        }
    }

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFeedUnliked(FeedUnlikedEvent event) {
        try {
            userStatsService.decrementLikesReceived(event.feedOwnerId());
            log.debug("좋아요 카운터 감소: feedOwnerId={}, feedId={}", event.feedOwnerId(), event.feedId());
        } catch (Exception e) {
            log.warn("좋아요 카운터 감소 실패: feedOwnerId={}, error={}", event.feedOwnerId(), e.getMessage());
        }
    }

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFriendAccepted(FriendRequestAcceptedEvent event) {
        try {
            userStatsService.incrementFriendCount(event.userId());
            userStatsService.incrementFriendCount(event.requesterId());
            achievementService.checkAchievementsByDataSource(event.userId(), "FRIEND_SERVICE");
            achievementService.checkAchievementsByDataSource(event.requesterId(), "FRIEND_SERVICE");
            log.debug("친구 카운터 증가: userId={}, requesterId={}", event.userId(), event.requesterId());
        } catch (Exception e) {
            log.warn("친구 카운터 업데이트 실패: userId={}, error={}", event.userId(), e.getMessage());
        }
    }

    /**
     * LUT-418: 이벤트마다 +1 하면 같은 길드 탈퇴→재가입 반복으로 무한 누적된다(어뷰징).
     * 증가 대신 "가입해 본 distinct 길드 수"(guild_member 전 status 행 수)로 덮어써 멱등 처리한다.
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGuildJoined(GuildJoinedEvent event) {
        try {
            userStatsService.syncGuildJoinCount(event.userId());
            achievementService.checkAchievementsByDataSource(event.userId(), "USER_STATS");
            log.debug("길드 가입 카운터 동기화: userId={}, guildId={}", event.userId(), event.guildId());
        } catch (Exception e) {
            log.warn("길드 가입 카운터 업데이트 실패: userId={}, error={}", event.userId(), e.getMessage());
        }
    }

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFriendRemoved(FriendRemovedEvent event) {
        try {
            userStatsService.decrementFriendCount(event.userId());
            userStatsService.decrementFriendCount(event.removedFriendId());
            log.debug("친구 카운터 감소: userId={}, removedFriendId={}", event.userId(), event.removedFriendId());
        } catch (Exception e) {
            log.warn("친구 카운터 감소 실패: userId={}, error={}", event.userId(), e.getMessage());
        }
    }

    /**
     * QA-113: 피드 댓글 수신 시 user_stats 카운터 갱신 + USER_STATS 업적 체크.
     * 자기 글에 자기가 댓글 단 경우는 FeedCommentEvent가 발행되지 않으므로 별도 가드 불필요.
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFeedComment(FeedCommentEvent event) {
        try {
            userStatsService.incrementCommentsReceived(event.feedOwnerId());
            achievementService.checkAchievementsByDataSource(event.feedOwnerId(), "USER_STATS");
            log.debug("피드 댓글 카운터 증가: feedOwnerId={}, feedId={}", event.feedOwnerId(), event.feedId());
        } catch (Exception e) {
            log.warn("피드 댓글 카운터 갱신 실패: feedOwnerId={}, error={}", event.feedOwnerId(), e.getMessage());
        }
    }

    /**
     * QA-113: 미션 댓글 수신 시 user_stats 카운터 갱신 + USER_STATS 업적 체크.
     * 자기 미션에 자기가 댓글 단 경우는 MissionCommentEvent가 발행되지 않으므로 별도 가드 불필요.
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMissionComment(MissionCommentEvent event) {
        try {
            userStatsService.incrementCommentsReceived(event.missionCreatorId());
            achievementService.checkAchievementsByDataSource(event.missionCreatorId(), "USER_STATS");
            log.debug("미션 댓글 카운터 증가: missionCreatorId={}, missionId={}",
                event.missionCreatorId(), event.missionId());
        } catch (Exception e) {
            log.warn("미션 댓글 카운터 갱신 실패: missionCreatorId={}, error={}",
                event.missionCreatorId(), e.getMessage());
        }
    }

    /**
     * LUT-418: 피드 댓글 삭제 시 받은 댓글 카운터 감소.
     * 발행 측이 작성 이벤트와 같은 가드(최상위·타인 댓글만)를 적용하므로 여기서는 대칭 감소만 한다.
     * 삭제 후 재작성 루프로 카운터를 무한 부풀리는 어뷰징 차단.
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFeedCommentDeleted(FeedCommentDeletedEvent event) {
        try {
            userStatsService.decrementCommentsReceived(event.feedOwnerId());
            log.debug("피드 댓글 카운터 감소: feedOwnerId={}, feedId={}", event.feedOwnerId(), event.feedId());
        } catch (Exception e) {
            log.warn("피드 댓글 카운터 감소 실패: feedOwnerId={}, error={}", event.feedOwnerId(), e.getMessage());
        }
    }

    /**
     * LUT-418: 미션 댓글 삭제 시 받은 댓글 카운터 감소 (피드 댓글 삭제와 동일 취지).
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMissionCommentDeleted(MissionCommentDeletedEvent event) {
        try {
            userStatsService.decrementCommentsReceived(event.missionCreatorId());
            log.debug("미션 댓글 카운터 감소: missionCreatorId={}, missionId={}",
                event.missionCreatorId(), event.missionId());
        } catch (Exception e) {
            log.warn("미션 댓글 카운터 감소 실패: missionCreatorId={}, error={}",
                event.missionCreatorId(), e.getMessage());
        }
    }
}
