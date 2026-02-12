package io.pinkspider.leveluptogethermvp.notificationservice.event.listener;

import static io.pinkspider.global.config.AsyncConfig.EVENT_EXECUTOR;

import io.pinkspider.global.event.AchievementCompletedEvent;
import io.pinkspider.global.event.ContentReportedEvent;
import io.pinkspider.global.event.FeedCommentEvent;
import io.pinkspider.global.event.FriendRequestAcceptedEvent;
import io.pinkspider.global.event.FriendRequestEvent;
import io.pinkspider.global.event.FriendRequestProcessedEvent;
import io.pinkspider.global.event.FriendRequestRejectedEvent;
import io.pinkspider.global.event.GuildBulletinCreatedEvent;
import io.pinkspider.global.event.GuildChatMessageEvent;
import io.pinkspider.global.event.GuildCreationEligibleEvent;
import io.pinkspider.global.event.GuildInvitationEvent;
import io.pinkspider.global.event.GuildMissionArrivedEvent;
import io.pinkspider.global.event.MissionCommentEvent;
import io.pinkspider.global.event.TitleAcquiredEvent;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryFacadeService;
import io.pinkspider.leveluptogethermvp.notificationservice.application.NotificationService;
import io.pinkspider.global.enums.NotificationType;
import java.util.List;
import java.util.function.Consumer;
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
    private final GuildQueryFacadeService guildQueryFacadeService;

    // ==================== 헬퍼 메서드 ====================

    private void safeHandle(String eventName, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("{} 알림 생성 실패: error={}", eventName, e.getMessage(), e);
        }
    }

    private void safeHandleMultiple(String eventName, List<String> memberIds,
                                     String excludeId, Consumer<String> action) {
        try {
            for (String memberId : memberIds) {
                if (excludeId != null && memberId.equals(excludeId)) continue;
                try {
                    action.accept(memberId);
                } catch (Exception e) {
                    log.warn("{} 알림 실패: memberId={}", eventName, memberId);
                }
            }
        } catch (Exception e) {
            log.error("{} 알림 처리 실패: error={}", eventName, e.getMessage(), e);
        }
    }

    // ==================== 칭호/업적 ====================

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTitleAcquired(TitleAcquiredEvent event) {
        safeHandle("칭호 획득", () -> notificationService.sendNotification(
            event.userId(), NotificationType.TITLE_ACQUIRED,
            event.titleId(), "rarity:" + event.rarity(), event.titleName()));
    }

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAchievementCompleted(AchievementCompletedEvent event) {
        safeHandle("업적 달성", () -> notificationService.sendNotification(
            event.userId(), NotificationType.ACHIEVEMENT_COMPLETED,
            event.achievementId(), null, event.achievementName()));
    }

    // ==================== 친구 ====================

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFriendRequest(FriendRequestEvent event) {
        safeHandle("친구 요청", () -> notificationService.sendNotification(
            event.targetUserId(), NotificationType.FRIEND_REQUEST,
            event.friendshipId(), null, event.requesterNickname()));
    }

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFriendRequestAccepted(FriendRequestAcceptedEvent event) {
        safeHandle("친구 수락", () -> notificationService.sendNotification(
            event.requesterId(), NotificationType.FRIEND_ACCEPTED,
            event.friendshipId(), null, event.accepterNickname()));
    }

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFriendRequestRejected(FriendRequestRejectedEvent event) {
        safeHandle("친구 거절", () -> notificationService.sendNotification(
            event.requesterId(), NotificationType.FRIEND_REJECTED,
            event.friendshipId(), null, event.rejecterNickname()));
    }

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFriendRequestProcessed(FriendRequestProcessedEvent event) {
        safeHandle("친구 요청 알림 삭제", () ->
            notificationService.deleteByReference("FRIEND_REQUEST", event.friendshipId()));
    }

    // ==================== 길드 ====================

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGuildMissionArrived(GuildMissionArrivedEvent event) {
        safeHandleMultiple("길드 미션", event.memberIds(), null, memberId ->
            notificationService.sendNotification(
                memberId, NotificationType.GUILD_MISSION_ARRIVED,
                event.missionId(), null, event.missionTitle()));
    }

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGuildBulletinCreated(GuildBulletinCreatedEvent event) {
        safeHandleMultiple("길드 공지사항", event.memberIds(), null, memberId ->
            notificationService.sendNotification(
                memberId, NotificationType.GUILD_BULLETIN,
                event.postId(), null,
                event.guildName(), event.postTitle(), event.guildId().toString()));
    }

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGuildChatMessage(GuildChatMessageEvent event) {
        safeHandleMultiple("길드 채팅", event.memberIds(), event.userId(), memberId ->
            notificationService.sendNotification(
                memberId, NotificationType.GUILD_CHAT,
                event.messageId(), null,
                event.guildName(), event.senderNickname(),
                event.getPreviewContent(), event.guildId().toString()));
    }

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGuildCreationEligible(GuildCreationEligibleEvent event) {
        safeHandle("길드 창설 가능", () -> notificationService.sendNotification(
            event.userId(), NotificationType.GUILD_CREATION_ELIGIBLE,
            null, null));
    }

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGuildInvitation(GuildInvitationEvent event) {
        safeHandle("길드 초대", () -> notificationService.sendNotification(
            event.inviteeId(), NotificationType.GUILD_INVITE,
            event.invitationId(), null,
            event.inviterNickname(), event.guildName()));
    }

    // ==================== 소셜 (댓글) ====================

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFeedComment(FeedCommentEvent event) {
        if (event.userId().equals(event.feedOwnerId())) return;
        safeHandle("피드 댓글", () -> notificationService.sendNotification(
            event.feedOwnerId(), NotificationType.COMMENT_ON_MY_FEED,
            event.feedId(), null, event.commenterNickname()));
    }

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMissionComment(MissionCommentEvent event) {
        safeHandle("미션 댓글", () -> notificationService.sendNotification(
            event.missionCreatorId(), NotificationType.COMMENT_ON_MY_MISSION,
            event.missionId(), null,
            event.commenterNickname(), event.missionTitle()));
    }

    // ==================== 신고 ====================

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleContentReported(ContentReportedEvent event) {
        try {
            String targetUserId = event.targetUserId();
            String targetTypeDescription = event.targetTypeDescription();

            if (targetUserId != null && !targetUserId.isBlank()) {
                try {
                    notificationService.notifyContentReported(targetUserId, targetTypeDescription);
                } catch (Exception e) {
                    log.warn("신고 대상 유저 알림 생성 실패: targetUserId={}, error={}", targetUserId, e.getMessage());
                }
            }

            notifyGuildMasterIfApplicable(event, targetUserId);
        } catch (Exception e) {
            log.error("콘텐츠 신고 알림 처리 실패: error={}", e.getMessage(), e);
        }
    }

    private void notifyGuildMasterIfApplicable(ContentReportedEvent event, String targetUserId) {
        String targetType = event.targetType();
        String guildMasterId = null;
        Long guildId = null;

        try {
            if ("GUILD".equals(targetType)) {
                guildId = Long.parseLong(event.targetId());
                guildMasterId = guildQueryFacadeService.getGuildMasterId(guildId);
            } else if ("GUILD_NOTICE".equals(targetType)) {
                Long postId = Long.parseLong(event.targetId());
                var postInfo = guildQueryFacadeService.getGuildInfoByPostId(postId);
                if (postInfo != null) {
                    guildMasterId = postInfo.guildMasterId();
                    guildId = postInfo.guildId();
                }
            }

            if (guildMasterId != null && !guildMasterId.equals(targetUserId)) {
                notificationService.notifyGuildContentReported(
                    guildMasterId, event.targetTypeDescription(), guildId);
            }
        } catch (NumberFormatException e) {
            log.warn("길드 관련 신고 처리 중 ID 파싱 실패: targetType={}, targetId={}", targetType, event.targetId());
        } catch (Exception e) {
            log.warn("길드 마스터 알림 생성 실패: targetType={}, error={}", targetType, e.getMessage());
        }
    }
}
