package io.pinkspider.leveluptogethermvp.notificationservice.event.listener;

import static io.pinkspider.global.config.AsyncConfig.EVENT_EXECUTOR;

import io.pinkspider.global.event.AchievementCompletedEvent;
import io.pinkspider.global.event.ContentReportedEvent;
import io.pinkspider.global.event.FeedCommentEvent;
import io.pinkspider.global.event.FeedCommentLikedEvent;
import io.pinkspider.global.event.FeedCommentReplyEvent;
import io.pinkspider.global.event.FriendRequestAcceptedEvent;
import io.pinkspider.global.event.FriendRequestEvent;
import io.pinkspider.global.event.FriendRequestProcessedEvent;
import io.pinkspider.global.event.FriendRequestRejectedEvent;
import io.pinkspider.global.event.GuildBulletinCreatedEvent;
import io.pinkspider.global.event.GuildChatMessageEvent;
import io.pinkspider.global.event.GuildCreationEligibleEvent;
import io.pinkspider.global.event.GuildDirectMessageEvent;
import io.pinkspider.global.event.GuildInvitationEvent;
import io.pinkspider.global.event.GuildJoinApprovedEvent;
import io.pinkspider.global.event.GuildJoinRejectedEvent;
import io.pinkspider.global.event.GuildJoinRequestedEvent;
import io.pinkspider.global.event.GuildMissionArrivedEvent;
import io.pinkspider.global.event.MissionAutoEndMilestone;
import io.pinkspider.global.event.MissionAutoEndWarningEvent;
import io.pinkspider.global.event.MissionReminderEvent;
import io.pinkspider.global.event.MissionCommentEvent;
import io.pinkspider.global.event.SeasonRewardItemGrantedEvent;
import io.pinkspider.global.event.ShopItemPurchasedEvent;
import io.pinkspider.global.event.TitleAcquiredEvent;
import io.pinkspider.global.event.UserLevelUpEvent;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.translation.LocaleUtils;
import io.pinkspider.leveluptogethermvp.notificationservice.application.NotificationService;
import io.pinkspider.global.enums.NotificationType;
import java.util.List;
import java.util.Locale;
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
    private final GuildQueryFacade guildQueryFacadeService;
    private final io.pinkspider.global.facade.UserQueryFacade userQueryFacadeService;

    // ==================== 헬퍼 메서드 ====================

        /**
     * LUT-367: 차단 관계 상호작용 알림 스킵 — 어느 한쪽이라도 상대를 차단했으면 보내지 않는다.
     * 차단 조회 실패가 알림 전체를 막지 않도록 예외는 미차단으로 처리한다.
     */
    private boolean isBlockedInteraction(String recipientId, String actorId) {
        if (recipientId == null || actorId == null) {
            return false;
        }
        try {
            return userQueryFacadeService.isBlockedBetween(recipientId, actorId);
        } catch (Exception e) {
            return false;
        }
    }

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

    // ==================== 아이템 획득 (LUT-410) ====================

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleShopItemPurchased(ShopItemPurchasedEvent event) {
        safeHandle("아이템 구매", () -> notificationService.sendLocalizedNotification(
            event.userId(), NotificationType.ITEM_PURCHASED,
            event.shopItemId(), null,
            locale -> new Object[] {
                localizedItemName(event.itemName(), event.itemNameEn(),
                    event.itemNameAr(), event.itemNameJa(), locale)
            }));
    }

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSeasonRewardItemGranted(SeasonRewardItemGrantedEvent event) {
        safeHandle("시즌 보상 아이템", () -> notificationService.sendLocalizedNotification(
            event.userId(), NotificationType.SEASON_REWARD_ITEM,
            event.shopItemId(), null,
            locale -> new Object[] {
                localizedItemName(event.itemName(), event.itemNameEn(),
                    event.itemNameAr(), event.itemNameJa(), locale)
            }));
    }

    /** 수신자 locale 에 맞는 아이템명 선택 — 미등록 언어는 기본값(ko) 폴백 */
    private String localizedItemName(
            String name, String nameEn, String nameAr, String nameJa, Locale locale) {
        return LocaleUtils.getLocalizedText(name, nameEn, nameAr, nameJa, locale.toLanguageTag());
    }

    // ==================== 레벨 ====================

    /**
     * 레벨업 알림 (LUT-301). referenceId=도달 레벨 — 동일 레벨 재발행에 대한 dedup 키.
     * 여러 레벨을 한 번에 오르면 최종 레벨 하나로 발행된다 (UserExperienceService).
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserLevelUp(UserLevelUpEvent event) {
        safeHandle("레벨업", () -> notificationService.sendNotification(
            event.userId(), NotificationType.LEVEL_UP,
            (long) event.newLevel(), null, event.newLevel()));
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
        safeHandleMultiple("길드 채팅", event.memberIds(), event.userId(), memberId -> {
            // LUT-373: 차단 관계면 단체 채팅 알림/푸시도 스킵
            if (isBlockedInteraction(memberId, event.userId())) return;
            notificationService.sendNotification(
                memberId, NotificationType.GUILD_CHAT,
                event.messageId(), null,
                event.guildName(), event.senderNickname(),
                event.getPreviewContent(), event.guildId().toString());
        });
    }

    /**
     * 길드 1:1 DM 알림 (LUT-224).
     * 기존에는 DM이 FCM 푸시만 직접 발송해 알림 레코드가 없었고,
     * 종 레드닷·알림 목록·클릭 이동이 모두 누락되었다.
     * sendNotification 경로로 통일해 레코드 생성 + 실시간 채널 + 푸시를 일괄 처리한다.
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGuildDirectMessage(GuildDirectMessageEvent event) {
        safeHandle("길드 DM", () -> {
            // LUT-383: 차단 관계 DM 알림 스킵 — 전송단 shadow drop과 이중 방어
            // (배포 어긋남·이벤트 재발행 등으로 이벤트가 새어 들어와도 여기서 걸러진다)
            if (isBlockedInteraction(event.recipientId(), event.userId())) {
                return;
            }
            notificationService.sendNotification(
                event.recipientId(), NotificationType.GUILD_DM,
                event.messageId(), null,
                event.senderNickname(), event.getPreviewContent(),
                event.guildId().toString(), event.userId());
        });
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
        if (isBlockedInteraction(event.inviteeId(), event.userId())) return;
        safeHandle("길드 초대", () -> notificationService.sendNotification(
            event.inviteeId(), NotificationType.GUILD_INVITE,
            event.invitationId(), null,
            event.inviterNickname(), event.guildName()));
    }

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGuildJoinRequested(GuildJoinRequestedEvent event) {
        safeHandleMultiple("길드 가입 신청", event.officerIds(), null, officerId ->
            notificationService.sendNotification(
                officerId, NotificationType.GUILD_JOIN_REQUEST,
                event.guildId(), null,
                event.requesterNickname()));
    }

    /** 길드 가입 승인 알림 (LUT-300). 신청자에게 발송, actionUrl은 가입된 길드 홈. */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGuildJoinApproved(GuildJoinApprovedEvent event) {
        safeHandle("길드 가입 승인", () -> notificationService.sendNotification(
            event.requesterId(), NotificationType.GUILD_JOIN_APPROVED,
            event.guildId(), null, event.guildName()));
    }

    /** 길드 가입 거절 알림 (LUT-300). 신청자에게 발송, actionUrl은 길드 목록. */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGuildJoinRejected(GuildJoinRejectedEvent event) {
        safeHandle("길드 가입 거절", () -> notificationService.sendNotification(
            event.requesterId(), NotificationType.GUILD_JOIN_REJECTED,
            event.guildId(), null, event.guildName()));
    }

    // ==================== 소셜 (댓글) ====================

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFeedComment(FeedCommentEvent event) {
        if (event.userId().equals(event.feedOwnerId())) return;
        if (isBlockedInteraction(event.feedOwnerId(), event.userId())) return;
        safeHandle("피드 댓글", () -> notificationService.sendNotification(
            event.feedOwnerId(), NotificationType.COMMENT_ON_MY_FEED,
            event.feedId(), null, event.commenterNickname()));
    }

    /**
     * 피드 댓글 대댓글 알림 (QA-73).
     * 발행 측에서 자기 자신은 이미 제외했지만 방어적으로 한 번 더 필터링.
     * 부모 작성자 + 같은 부모에 대댓글 단 다른 유저들 모두에게 알림.
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFeedCommentReply(FeedCommentReplyEvent event) {
        // 부모 작성자 알림
        if (event.parentCommentAuthorId() != null
            && !event.parentCommentAuthorId().equals(event.userId())
            && !isBlockedInteraction(event.parentCommentAuthorId(), event.userId())) {
            safeHandle("피드 대댓글(부모작성자)", () -> notificationService.sendNotification(
                event.parentCommentAuthorId(), NotificationType.COMMENT_REPLY,
                event.feedId(), null, event.replierNickname()));
        }
        // 같은 부모에 대댓글 단 다른 유저들 (replier/부모작성자 제외, 중복 제거)
        if (event.threadParticipants() != null && !event.threadParticipants().isEmpty()) {
            safeHandleMultiple("피드 대댓글(스레드참여자)", event.threadParticipants(), event.userId(),
                participantId -> {
                    if (isBlockedInteraction(participantId, event.userId())) return;
                    notificationService.sendNotification(
                        participantId, NotificationType.COMMENT_REPLY,
                        event.feedId(), null, event.replierNickname());
                });
        }
    }

    /**
     * 피드 댓글 좋아요 알림 (QA-73). 자기 자신 댓글 좋아요는 발행 측에서 제외됨.
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFeedCommentLiked(FeedCommentLikedEvent event) {
        if (event.userId().equals(event.commentAuthorId())) return;
        if (isBlockedInteraction(event.commentAuthorId(), event.userId())) return;
        safeHandle("피드 댓글 좋아요", () -> notificationService.sendNotification(
            event.commentAuthorId(), NotificationType.COMMENT_LIKED,
            event.feedId(), null, event.likerNickname()));
    }

    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMissionAutoEndWarning(MissionAutoEndWarningEvent event) {
        MissionAutoEndMilestone milestone =
            event.milestone() != null ? event.milestone() : MissionAutoEndMilestone.FINAL;
        NotificationType type = switch (milestone) {
            case FIRST -> NotificationType.MISSION_AUTO_END_WARNING_FIRST;
            case FINAL -> NotificationType.MISSION_AUTO_END_WARNING_FINAL;
        };
        safeHandle("미션 자동종료 임박(" + milestone + ")", () -> notificationService.sendNotification(
            event.userId(), type, event.missionId(), null, event.missionTitle()));
    }

    /** LUT-282: 미션 푸시 리마인더 — 유저가 설정한 요일·시각에 MissionReminderScheduler 가 발행 */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMissionReminder(MissionReminderEvent event) {
        safeHandle("미션 리마인더", () -> notificationService.sendNotification(
            event.userId(), NotificationType.MISSION_REMINDER,
            event.missionId(), null, event.missionTitle()));
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
