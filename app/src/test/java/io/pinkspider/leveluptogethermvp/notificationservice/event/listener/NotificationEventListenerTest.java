package io.pinkspider.leveluptogethermvp.notificationservice.event.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventListener 테스트")
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private GuildQueryFacadeService guildQueryFacadeService;

    @InjectMocks
    private NotificationEventListener eventListener;

    private static final String REPORTER_ID = "reporter-123";
    private static final String TARGET_USER_ID = "target-user-456";
    private static final String GUILD_MASTER_ID = "guild-master-789";
    private static final String SENDER_ID = "sender-user-100";
    private static final String MEMBER_ID_1 = "member-user-101";
    private static final String MEMBER_ID_2 = "member-user-102";
    private static final String INVITEE_ID = "invitee-user-200";
    private static final String MISSION_CREATOR_ID = "mission-creator-300";

    // ==================== 칭호/업적 ====================

    @Nested
    @DisplayName("칭호 획득 이벤트 처리")
    class HandleTitleAcquiredTest {

        @Test
        @DisplayName("칭호 획득 이벤트 발생 시 알림 서비스 호출")
        void shouldCallNotificationServiceOnTitleAcquired() {
            TitleAcquiredEvent event = new TitleAcquiredEvent("user-123", 1L, "전설적인 모험가", "LEGENDARY");
            eventListener.handleTitleAcquired(event);
            verify(notificationService).sendNotification(
                eq("user-123"), eq(NotificationType.TITLE_ACQUIRED),
                eq(1L), eq("rarity:LEGENDARY"), eq("전설적인 모험가"));
        }

        @Test
        @DisplayName("알림 서비스 실패해도 예외를 던지지 않음")
        void shouldNotThrowExceptionOnNotificationFailure() {
            TitleAcquiredEvent event = new TitleAcquiredEvent("user-123", 1L, "전설적인 모험가", "LEGENDARY");
            doThrow(new RuntimeException("알림 전송 실패"))
                .when(notificationService).sendNotification(
                    anyString(), eq(NotificationType.TITLE_ACQUIRED),
                    anyLong(), anyString(), anyString());
            eventListener.handleTitleAcquired(event);
        }
    }

    @Nested
    @DisplayName("업적 달성 이벤트 처리")
    class HandleAchievementCompletedTest {

        @Test
        @DisplayName("업적 달성 이벤트 발생 시 알림 서비스 호출")
        void shouldCallNotificationServiceOnAchievementCompleted() {
            AchievementCompletedEvent event = new AchievementCompletedEvent("user-123", 1L, "첫 미션 완료");
            eventListener.handleAchievementCompleted(event);
            verify(notificationService).sendNotification(
                eq("user-123"), eq(NotificationType.ACHIEVEMENT_COMPLETED),
                eq(1L), isNull(), eq("첫 미션 완료"));
        }
    }

    // ==================== 친구 ====================

    @Nested
    @DisplayName("친구 요청 이벤트 처리")
    class HandleFriendRequestTest {

        @Test
        @DisplayName("친구 요청 이벤트 발생 시 알림 서비스 호출")
        void shouldCallNotificationServiceOnFriendRequest() {
            FriendRequestEvent event = new FriendRequestEvent("requester-123", "target-456", "테스트유저", 1L);
            eventListener.handleFriendRequest(event);
            verify(notificationService).sendNotification(
                eq("target-456"), eq(NotificationType.FRIEND_REQUEST),
                eq(1L), isNull(), eq("테스트유저"));
        }
    }

    @Nested
    @DisplayName("친구 요청 수락 이벤트 처리")
    class HandleFriendRequestAcceptedTest {

        @Test
        @DisplayName("친구 요청 수락 이벤트 발생 시 알림 서비스 호출")
        void shouldCallNotificationServiceOnFriendRequestAccepted() {
            FriendRequestAcceptedEvent event = new FriendRequestAcceptedEvent("accepter-123", "requester-456", "수락자닉네임", 1L);
            eventListener.handleFriendRequestAccepted(event);
            verify(notificationService).sendNotification(
                eq("requester-456"), eq(NotificationType.FRIEND_ACCEPTED),
                eq(1L), isNull(), eq("수락자닉네임"));
        }
    }

    @Nested
    @DisplayName("친구 요청 거절 이벤트 처리")
    class HandleFriendRequestRejectedTest {

        @Test
        @DisplayName("친구 요청 거절 이벤트 발생 시 알림 서비스 호출")
        void shouldCallNotificationServiceOnFriendRequestRejected() {
            FriendRequestRejectedEvent event = new FriendRequestRejectedEvent("rejecter-123", "requester-456", "거절자닉네임", 1L);
            eventListener.handleFriendRequestRejected(event);
            verify(notificationService).sendNotification(
                eq("requester-456"), eq(NotificationType.FRIEND_REJECTED),
                eq(1L), isNull(), eq("거절자닉네임"));
        }
    }

    @Nested
    @DisplayName("친구 요청 처리 완료 이벤트")
    class HandleFriendRequestProcessedTest {

        @Test
        @DisplayName("친구 요청 처리 완료 시 알림 삭제 호출")
        void shouldDeleteNotificationOnFriendRequestProcessed() {
            FriendRequestProcessedEvent event = new FriendRequestProcessedEvent("user-123", 1L);
            eventListener.handleFriendRequestProcessed(event);
            verify(notificationService).deleteByReference(eq("FRIEND_REQUEST"), eq(1L));
        }
    }

    // ==================== 길드 ====================

    @Nested
    @DisplayName("길드 미션 도착 이벤트 처리")
    class HandleGuildMissionArrivedTest {

        @Test
        @DisplayName("길드 미션 도착 이벤트 발생 시 각 멤버에게 알림 전송")
        void shouldNotifyAllMembersOnGuildMissionArrived() {
            List<String> memberIds = List.of("member-1", "member-2", "member-3");
            GuildMissionArrivedEvent event = new GuildMissionArrivedEvent("creator-123", memberIds, 1L, "테스트 미션");
            eventListener.handleGuildMissionArrived(event);
            verify(notificationService, times(3)).sendNotification(
                anyString(), eq(NotificationType.GUILD_MISSION_ARRIVED),
                eq(1L), isNull(), eq("테스트 미션"));
        }

        @Test
        @DisplayName("일부 멤버 알림 실패해도 나머지는 계속 처리")
        void shouldContinueOnPartialFailure() {
            List<String> memberIds = List.of("member-1", "member-2", "member-3");
            GuildMissionArrivedEvent event = new GuildMissionArrivedEvent("creator-123", memberIds, 1L, "테스트 미션");
            doThrow(new RuntimeException("알림 실패"))
                .when(notificationService).sendNotification(
                    eq("member-2"), eq(NotificationType.GUILD_MISSION_ARRIVED),
                    anyLong(), any(), any());
            eventListener.handleGuildMissionArrived(event);
            verify(notificationService, times(3)).sendNotification(
                anyString(), eq(NotificationType.GUILD_MISSION_ARRIVED),
                eq(1L), isNull(), eq("테스트 미션"));
        }
    }

    @Nested
    @DisplayName("길드 공지사항 등록 이벤트 처리")
    class HandleGuildBulletinCreatedTest {

        @Test
        @DisplayName("길드 공지사항 등록 이벤트 발생 시 각 멤버에게 알림 전송")
        void shouldNotifyAllMembersOnGuildBulletinCreated() {
            List<String> memberIds = List.of("member-1", "member-2", "member-3");
            GuildBulletinCreatedEvent event = new GuildBulletinCreatedEvent(
                "author-123", memberIds, 1L, "테스트 길드", 10L, "중요 공지사항");
            eventListener.handleGuildBulletinCreated(event);
            verify(notificationService, times(3)).sendNotification(
                anyString(), eq(NotificationType.GUILD_BULLETIN),
                eq(10L), isNull(),
                eq("테스트 길드"), eq("중요 공지사항"), eq("1"));
        }
    }

    @Nested
    @DisplayName("길드 채팅 메시지 이벤트 처리")
    class HandleGuildChatMessageTest {

        @Test
        @DisplayName("발송자 본인 제외하고 멤버들에게 알림을 생성한다")
        void handleGuildChatMessage_success_notifiesAllMembersExceptSender() {
            Long guildId = 100L;
            Long messageId = 1L;
            List<String> memberIds = Arrays.asList(SENDER_ID, MEMBER_ID_1, MEMBER_ID_2);
            GuildChatMessageEvent event = new GuildChatMessageEvent(
                SENDER_ID, "발송자닉네임", guildId, "테스트 길드", messageId, "안녕하세요", memberIds, LocalDateTime.now());
            eventListener.handleGuildChatMessage(event);
            verify(notificationService).sendNotification(
                eq(MEMBER_ID_1), eq(NotificationType.GUILD_CHAT),
                eq(messageId), isNull(),
                eq("테스트 길드"), eq("발송자닉네임"), eq("안녕하세요"), eq(guildId.toString()));
            verify(notificationService).sendNotification(
                eq(MEMBER_ID_2), eq(NotificationType.GUILD_CHAT),
                eq(messageId), isNull(),
                eq("테스트 길드"), eq("발송자닉네임"), eq("안녕하세요"), eq(guildId.toString()));
            verify(notificationService, never()).sendNotification(
                eq(SENDER_ID), eq(NotificationType.GUILD_CHAT),
                anyLong(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("길드 창설 가능 이벤트 처리")
    class HandleGuildCreationEligibleTest {

        @Test
        @DisplayName("길드 창설 가능 레벨 도달 이벤트 처리 시 유저에게 알림을 생성한다")
        void handleGuildCreationEligible_success_createsNotification() {
            GuildCreationEligibleEvent event = new GuildCreationEligibleEvent(TARGET_USER_ID, 20, LocalDateTime.now());
            eventListener.handleGuildCreationEligible(event);
            verify(notificationService).sendNotification(
                eq(TARGET_USER_ID), eq(NotificationType.GUILD_CREATION_ELIGIBLE),
                isNull(), isNull());
        }
    }

    @Nested
    @DisplayName("길드 초대 이벤트 처리")
    class HandleGuildInvitationTest {

        @Test
        @DisplayName("길드 초대 이벤트 처리 시 초대받은 유저에게 알림을 생성한다")
        void handleGuildInvitation_success_createsNotification() {
            GuildInvitationEvent event = new GuildInvitationEvent(
                GUILD_MASTER_ID, INVITEE_ID, "마스터닉네임", 100L, "테스트 길드", 1L, LocalDateTime.now());
            eventListener.handleGuildInvitation(event);
            verify(notificationService).sendNotification(
                eq(INVITEE_ID), eq(NotificationType.GUILD_INVITE),
                eq(1L), isNull(),
                eq("마스터닉네임"), eq("테스트 길드"));
        }
    }

    // ==================== 소셜 (댓글) ====================

    @Nested
    @DisplayName("피드 댓글 이벤트 처리")
    class HandleFeedCommentTest {

        @Test
        @DisplayName("피드 댓글 이벤트 발생 시 피드 주인에게 알림 전송")
        void shouldNotifyFeedOwnerOnComment() {
            FeedCommentEvent event = new FeedCommentEvent("commenter-123", "owner-456", "댓글러닉네임", 1L);
            eventListener.handleFeedComment(event);
            verify(notificationService).sendNotification(
                eq("owner-456"), eq(NotificationType.COMMENT_ON_MY_FEED),
                eq(1L), isNull(), eq("댓글러닉네임"));
        }

        @Test
        @DisplayName("자기 글에 자신이 댓글 달면 알림 전송하지 않음")
        void shouldNotNotifyWhenCommentingOnOwnFeed() {
            FeedCommentEvent event = new FeedCommentEvent("user-123", "user-123", "유저닉네임", 1L);
            eventListener.handleFeedComment(event);
            verify(notificationService, never()).sendNotification(
                anyString(), any(NotificationType.class), anyLong(), any(), any());
        }
    }

    @Nested
    @DisplayName("미션 댓글 이벤트 처리")
    class HandleMissionCommentTest {

        @Test
        @DisplayName("미션 댓글 이벤트 처리 시 미션 생성자에게 알림을 생성한다")
        void handleMissionComment_success_createsNotification() {
            MissionCommentEvent event = new MissionCommentEvent(
                TARGET_USER_ID, MISSION_CREATOR_ID, "댓글작성자닉네임", 100L, "테스트 미션 제목", LocalDateTime.now());
            eventListener.handleMissionComment(event);
            verify(notificationService).sendNotification(
                eq(MISSION_CREATOR_ID), eq(NotificationType.COMMENT_ON_MY_MISSION),
                eq(100L), isNull(),
                eq("댓글작성자닉네임"), eq("테스트 미션 제목"));
        }
    }

    // ==================== 신고 ====================

    @Nested
    @DisplayName("handleContentReported 테스트")
    class HandleContentReportedTest {

        @Test
        @DisplayName("targetUserId가 있을 때 신고 당한 유저에게 알림을 생성한다")
        void handleContentReported_withTargetUserId_createsNotification() {
            ContentReportedEvent event = new ContentReportedEvent(
                REPORTER_ID, "FEED", "feed-123", TARGET_USER_ID, "피드", LocalDateTime.now());
            eventListener.handleContentReported(event);
            verify(notificationService).notifyContentReported(TARGET_USER_ID, "피드");
        }

        @Test
        @DisplayName("targetUserId가 null일 때 알림을 생성하지 않는다")
        void handleContentReported_withNullTargetUserId_doesNotCreateNotification() {
            ContentReportedEvent event = new ContentReportedEvent(
                REPORTER_ID, "FEED", "feed-123", null, "피드", LocalDateTime.now());
            eventListener.handleContentReported(event);
            verify(notificationService, never()).notifyContentReported(anyString(), anyString());
        }

        @Test
        @DisplayName("GUILD 타입 신고 시 길드 마스터에게도 알림을 생성한다")
        void handleContentReported_guildType_notifiesGuildMaster() {
            Long guildId = 100L;
            ContentReportedEvent event = new ContentReportedEvent(
                REPORTER_ID, "GUILD", String.valueOf(guildId), TARGET_USER_ID, "길드", LocalDateTime.now());
            when(guildQueryFacadeService.getGuildMasterId(guildId)).thenReturn(GUILD_MASTER_ID);
            eventListener.handleContentReported(event);
            verify(notificationService).notifyContentReported(TARGET_USER_ID, "길드");
            verify(notificationService).notifyGuildContentReported(GUILD_MASTER_ID, "길드", guildId);
        }

        @Test
        @DisplayName("GUILD_NOTICE 타입 신고 시 길드 마스터에게도 알림을 생성한다")
        void handleContentReported_guildNoticeType_notifiesGuildMaster() {
            Long postId = 1L;
            Long guildId = 100L;
            ContentReportedEvent event = new ContentReportedEvent(
                REPORTER_ID, "GUILD_NOTICE", String.valueOf(postId), TARGET_USER_ID, "길드 공지", LocalDateTime.now());
            var postInfo = new GuildQueryFacadeService.GuildPostInfo(guildId, GUILD_MASTER_ID);
            when(guildQueryFacadeService.getGuildInfoByPostId(postId)).thenReturn(postInfo);
            eventListener.handleContentReported(event);
            verify(notificationService).notifyContentReported(TARGET_USER_ID, "길드 공지");
            verify(notificationService).notifyGuildContentReported(GUILD_MASTER_ID, "길드 공지", guildId);
        }

        @Test
        @DisplayName("신고 당한 유저가 길드 마스터일 때 중복 알림을 생성하지 않는다")
        void handleContentReported_targetUserIsGuildMaster_noDuplicateNotification() {
            Long guildId = 100L;
            ContentReportedEvent event = new ContentReportedEvent(
                REPORTER_ID, "GUILD", String.valueOf(guildId), TARGET_USER_ID, "길드", LocalDateTime.now());
            when(guildQueryFacadeService.getGuildMasterId(guildId)).thenReturn(TARGET_USER_ID);
            eventListener.handleContentReported(event);
            verify(notificationService).notifyContentReported(TARGET_USER_ID, "길드");
            verify(notificationService, never()).notifyGuildContentReported(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("FEED 타입 신고 시 길드 마스터 알림을 생성하지 않는다")
        void handleContentReported_feedType_noGuildMasterNotification() {
            ContentReportedEvent event = new ContentReportedEvent(
                REPORTER_ID, "FEED", "feed-123", TARGET_USER_ID, "피드", LocalDateTime.now());
            eventListener.handleContentReported(event);
            verify(notificationService).notifyContentReported(TARGET_USER_ID, "피드");
            verify(notificationService, never()).notifyGuildContentReported(anyString(), anyString(), anyLong());
        }
    }
}
