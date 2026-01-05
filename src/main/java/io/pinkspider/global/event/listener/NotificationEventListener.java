package io.pinkspider.global.event.listener;

import static io.pinkspider.global.config.AsyncConfig.EVENT_EXECUTOR;

import io.pinkspider.global.event.AchievementCompletedEvent;
import io.pinkspider.global.event.FeedCommentEvent;
import io.pinkspider.global.event.FriendRequestAcceptedEvent;
import io.pinkspider.global.event.FriendRequestEvent;
import io.pinkspider.global.event.FriendRequestProcessedEvent;
import io.pinkspider.global.event.FriendRequestRejectedEvent;
import io.pinkspider.global.event.GuildMissionArrivedEvent;
import io.pinkspider.global.event.TitleAcquiredEvent;
import io.pinkspider.leveluptogethermvp.userservice.notification.application.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 관련 이벤트 리스너
 * 트랜잭션 커밋 후 비동기로 알림을 생성합니다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    /**
     * 칭호 획득 이벤트 처리
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTitleAcquired(TitleAcquiredEvent event) {
        try {
            log.debug("칭호 획득 이벤트 처리: userId={}, titleName={}", event.userId(), event.titleName());
            notificationService.notifyTitleAcquired(
                event.userId(),
                event.titleId(),
                event.titleName(),
                event.rarity()
            );
        } catch (Exception e) {
            log.error("칭호 획득 알림 생성 실패: userId={}, error={}", event.userId(), e.getMessage(), e);
        }
    }

    /**
     * 업적 달성 이벤트 처리
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAchievementCompleted(AchievementCompletedEvent event) {
        try {
            log.debug("업적 달성 이벤트 처리: userId={}, achievementName={}", event.userId(), event.achievementName());
            notificationService.notifyAchievementCompleted(
                event.userId(),
                event.achievementId(),
                event.achievementName()
            );
        } catch (Exception e) {
            log.error("업적 달성 알림 생성 실패: userId={}, error={}", event.userId(), e.getMessage(), e);
        }
    }

    /**
     * 친구 요청 이벤트 처리
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFriendRequest(FriendRequestEvent event) {
        try {
            log.debug("친구 요청 이벤트 처리: from={}, to={}", event.userId(), event.targetUserId());
            notificationService.notifyFriendRequest(
                event.targetUserId(),
                event.requesterNickname(),
                event.friendshipId()
            );
        } catch (Exception e) {
            log.error("친구 요청 알림 생성 실패: error={}", e.getMessage(), e);
        }
    }

    /**
     * 친구 요청 수락 이벤트 처리
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFriendRequestAccepted(FriendRequestAcceptedEvent event) {
        try {
            log.debug("친구 요청 수락 이벤트 처리: accepter={}, requester={}", event.userId(), event.requesterId());
            notificationService.notifyFriendAccepted(
                event.requesterId(),
                event.accepterNickname(),
                event.friendshipId()
            );
        } catch (Exception e) {
            log.error("친구 수락 알림 생성 실패: error={}", e.getMessage(), e);
        }
    }

    /**
     * 친구 요청 거절 이벤트 처리
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFriendRequestRejected(FriendRequestRejectedEvent event) {
        try {
            log.debug("친구 요청 거절 이벤트 처리: rejecter={}, requester={}", event.userId(), event.requesterId());
            notificationService.notifyFriendRejected(
                event.requesterId(),
                event.rejecterNickname(),
                event.friendshipId()
            );
        } catch (Exception e) {
            log.error("친구 거절 알림 생성 실패: error={}", e.getMessage(), e);
        }
    }

    /**
     * 친구 요청 처리 완료 이벤트 (알림 삭제)
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFriendRequestProcessed(FriendRequestProcessedEvent event) {
        try {
            log.debug("친구 요청 알림 삭제: friendshipId={}", event.friendshipId());
            notificationService.deleteByReference("FRIEND_REQUEST", event.friendshipId());
        } catch (Exception e) {
            log.error("친구 요청 알림 삭제 실패: error={}", e.getMessage(), e);
        }
    }

    /**
     * 길드 미션 도착 이벤트 처리
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGuildMissionArrived(GuildMissionArrivedEvent event) {
        try {
            log.debug("길드 미션 도착 이벤트 처리: missionId={}, memberCount={}",
                event.missionId(), event.memberIds().size());
            for (String memberId : event.memberIds()) {
                try {
                    notificationService.notifyGuildMissionArrived(
                        memberId,
                        event.missionTitle(),
                        event.missionId()
                    );
                } catch (Exception e) {
                    log.warn("길드 미션 알림 생성 실패: memberId={}, error={}", memberId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("길드 미션 알림 처리 실패: error={}", e.getMessage(), e);
        }
    }

    /**
     * 피드 댓글 이벤트 처리
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFeedComment(FeedCommentEvent event) {
        try {
            // 자신의 글에 자신이 댓글 단 경우 알림 제외
            if (event.userId().equals(event.feedOwnerId())) {
                return;
            }
            log.debug("피드 댓글 이벤트 처리: commenter={}, feedOwner={}", event.userId(), event.feedOwnerId());
            notificationService.notifyCommentOnMyFeed(
                event.feedOwnerId(),
                event.commenterNickname(),
                event.feedId()
            );
        } catch (Exception e) {
            log.error("피드 댓글 알림 생성 실패: error={}", e.getMessage(), e);
        }
    }
}
