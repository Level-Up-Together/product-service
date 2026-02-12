package io.pinkspider.leveluptogethermvp.gamificationservice.stats.event.listener;

import io.pinkspider.global.event.FeedLikedEvent;
import io.pinkspider.global.event.FeedUnlikedEvent;
import io.pinkspider.global.event.FriendRemovedEvent;
import io.pinkspider.global.event.FriendRequestAcceptedEvent;
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
}
