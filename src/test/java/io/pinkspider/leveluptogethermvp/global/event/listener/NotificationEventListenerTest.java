package io.pinkspider.leveluptogethermvp.global.event.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.ContentReportedEvent;
import io.pinkspider.global.event.GuildChatMessageEvent;
import io.pinkspider.global.event.GuildCreationEligibleEvent;
import io.pinkspider.global.event.GuildInvitationEvent;
import io.pinkspider.global.event.MissionCommentEvent;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryFacadeService;
import io.pinkspider.leveluptogethermvp.notificationservice.application.NotificationService;
import io.pinkspider.global.enums.NotificationType;
import io.pinkspider.global.event.listener.NotificationEventListener;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private GuildQueryFacadeService guildQueryFacadeService;

    @InjectMocks
    private NotificationEventListener notificationEventListener;

    private static final String REPORTER_ID = "reporter-123";
    private static final String TARGET_USER_ID = "target-user-456";
    private static final String GUILD_MASTER_ID = "guild-master-789";
    private static final String SENDER_ID = "sender-user-100";
    private static final String MEMBER_ID_1 = "member-user-101";
    private static final String MEMBER_ID_2 = "member-user-102";
    private static final String INVITEE_ID = "invitee-user-200";
    private static final String MISSION_CREATOR_ID = "mission-creator-300";


    @Nested
    @DisplayName("handleContentReported 테스트")
    class HandleContentReportedTest {

        @Test
        @DisplayName("targetUserId가 있을 때 신고 당한 유저에게 알림을 생성한다")
        void handleContentReported_withTargetUserId_createsNotification() {
            // given
            ContentReportedEvent event = new ContentReportedEvent(
                REPORTER_ID, "FEED", "feed-123", TARGET_USER_ID, "피드", LocalDateTime.now()
            );

            // when
            notificationEventListener.handleContentReported(event);

            // then
            verify(notificationService).notifyContentReported(TARGET_USER_ID, "피드");
        }

        @Test
        @DisplayName("targetUserId가 null일 때 알림을 생성하지 않는다")
        void handleContentReported_withNullTargetUserId_doesNotCreateNotification() {
            // given
            ContentReportedEvent event = new ContentReportedEvent(
                REPORTER_ID, "FEED", "feed-123", null, "피드", LocalDateTime.now()
            );

            // when
            notificationEventListener.handleContentReported(event);

            // then
            verify(notificationService, never()).notifyContentReported(anyString(), anyString());
        }

        @Test
        @DisplayName("targetUserId가 빈 문자열일 때 알림을 생성하지 않는다")
        void handleContentReported_withBlankTargetUserId_doesNotCreateNotification() {
            // given
            ContentReportedEvent event = new ContentReportedEvent(
                REPORTER_ID, "FEED", "feed-123", "  ", "피드", LocalDateTime.now()
            );

            // when
            notificationEventListener.handleContentReported(event);

            // then
            verify(notificationService, never()).notifyContentReported(anyString(), anyString());
        }

        @Test
        @DisplayName("GUILD 타입 신고 시 길드 마스터에게도 알림을 생성한다")
        void handleContentReported_guildType_notifiesGuildMaster() {
            // given
            Long guildId = 100L;
            ContentReportedEvent event = new ContentReportedEvent(
                REPORTER_ID, "GUILD", String.valueOf(guildId), TARGET_USER_ID, "길드", LocalDateTime.now()
            );
            when(guildQueryFacadeService.getGuildMasterId(guildId)).thenReturn(GUILD_MASTER_ID);

            // when
            notificationEventListener.handleContentReported(event);

            // then
            verify(notificationService).notifyContentReported(TARGET_USER_ID, "길드");
            verify(notificationService).notifyGuildContentReported(GUILD_MASTER_ID, "길드", guildId);
        }

        @Test
        @DisplayName("GUILD_NOTICE 타입 신고 시 길드 마스터에게도 알림을 생성한다")
        void handleContentReported_guildNoticeType_notifiesGuildMaster() {
            // given
            Long postId = 1L;
            Long guildId = 100L;
            ContentReportedEvent event = new ContentReportedEvent(
                REPORTER_ID, "GUILD_NOTICE", String.valueOf(postId), TARGET_USER_ID, "길드 공지", LocalDateTime.now()
            );
            var postInfo = new io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryFacadeService.GuildPostInfo(
                guildId, GUILD_MASTER_ID
            );
            when(guildQueryFacadeService.getGuildInfoByPostId(postId)).thenReturn(postInfo);

            // when
            notificationEventListener.handleContentReported(event);

            // then
            verify(notificationService).notifyContentReported(TARGET_USER_ID, "길드 공지");
            verify(notificationService).notifyGuildContentReported(GUILD_MASTER_ID, "길드 공지", guildId);
        }

        @Test
        @DisplayName("신고 당한 유저가 길드 마스터일 때 중복 알림을 생성하지 않는다")
        void handleContentReported_targetUserIsGuildMaster_noDuplicateNotification() {
            // given
            Long guildId = 100L;
            ContentReportedEvent event = new ContentReportedEvent(
                REPORTER_ID, "GUILD", String.valueOf(guildId), TARGET_USER_ID, "길드", LocalDateTime.now()
            );
            when(guildQueryFacadeService.getGuildMasterId(guildId)).thenReturn(TARGET_USER_ID);

            // when
            notificationEventListener.handleContentReported(event);

            // then
            verify(notificationService).notifyContentReported(TARGET_USER_ID, "길드");
            verify(notificationService, never()).notifyGuildContentReported(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("길드가 없을 때 마스터 알림을 생성하지 않는다")
        void handleContentReported_guildNotFound_noMasterNotification() {
            // given
            Long guildId = 999L;
            ContentReportedEvent event = new ContentReportedEvent(
                REPORTER_ID, "GUILD", String.valueOf(guildId), TARGET_USER_ID, "길드", LocalDateTime.now()
            );
            when(guildQueryFacadeService.getGuildMasterId(guildId)).thenReturn(null);

            // when
            notificationEventListener.handleContentReported(event);

            // then
            verify(notificationService).notifyContentReported(TARGET_USER_ID, "길드");
            verify(notificationService, never()).notifyGuildContentReported(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("FEED 타입 신고 시 길드 마스터 알림을 생성하지 않는다")
        void handleContentReported_feedType_noGuildMasterNotification() {
            // given
            ContentReportedEvent event = new ContentReportedEvent(
                REPORTER_ID, "FEED", "feed-123", TARGET_USER_ID, "피드", LocalDateTime.now()
            );

            // when
            notificationEventListener.handleContentReported(event);

            // then
            verify(notificationService).notifyContentReported(TARGET_USER_ID, "피드");
            verify(notificationService, never()).notifyGuildContentReported(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("잘못된 길드 ID 형식일 때 마스터 알림을 생성하지 않는다")
        void handleContentReported_invalidGuildId_noMasterNotification() {
            // given
            ContentReportedEvent event = new ContentReportedEvent(
                REPORTER_ID, "GUILD", "invalid-id", TARGET_USER_ID, "길드", LocalDateTime.now()
            );

            // when
            notificationEventListener.handleContentReported(event);

            // then
            verify(notificationService).notifyContentReported(TARGET_USER_ID, "길드");
            verify(notificationService, never()).notifyGuildContentReported(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("NotificationService에서 예외 발생 시 다른 알림 처리는 계속된다")
        void handleContentReported_notificationServiceThrows_continuesProcessing() {
            // given
            Long guildId = 100L;
            ContentReportedEvent event = new ContentReportedEvent(
                REPORTER_ID, "GUILD", String.valueOf(guildId), TARGET_USER_ID, "길드", LocalDateTime.now()
            );
            when(guildQueryFacadeService.getGuildMasterId(guildId)).thenReturn(GUILD_MASTER_ID);
            org.mockito.Mockito.doThrow(new RuntimeException("알림 생성 실패"))
                .when(notificationService).notifyContentReported(anyString(), anyString());

            // when
            notificationEventListener.handleContentReported(event);

            // then
            verify(notificationService).notifyContentReported(TARGET_USER_ID, "길드");
            verify(notificationService).notifyGuildContentReported(GUILD_MASTER_ID, "길드", guildId);
        }
    }

    @Nested
    @DisplayName("handleGuildChatMessage 테스트")
    class HandleGuildChatMessageTest {

        @Test
        @DisplayName("길드 채팅 메시지 이벤트 처리 시 발송자 본인 제외하고 멤버들에게 알림을 생성한다")
        void handleGuildChatMessage_success_notifiesAllMembersExceptSender() {
            // given
            Long guildId = 100L;
            Long messageId = 1L;
            String guildName = "테스트 길드";
            String messageContent = "안녕하세요 여러분!";
            List<String> memberIds = Arrays.asList(SENDER_ID, MEMBER_ID_1, MEMBER_ID_2);

            GuildChatMessageEvent event = new GuildChatMessageEvent(
                SENDER_ID, "발송자닉네임", guildId, guildName, messageId, messageContent, memberIds, LocalDateTime.now()
            );

            // when
            notificationEventListener.handleGuildChatMessage(event);

            // then - 발송자 본인 제외, 2명에게만 알림
            verify(notificationService).sendNotification(
                eq(MEMBER_ID_1), eq(NotificationType.GUILD_CHAT),
                eq(messageId), isNull(),
                eq(guildName), eq("발송자닉네임"), eq(messageContent), eq(guildId.toString()));
            verify(notificationService).sendNotification(
                eq(MEMBER_ID_2), eq(NotificationType.GUILD_CHAT),
                eq(messageId), isNull(),
                eq(guildName), eq("발송자닉네임"), eq(messageContent), eq(guildId.toString()));
            verify(notificationService, never()).sendNotification(
                eq(SENDER_ID), eq(NotificationType.GUILD_CHAT),
                anyLong(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("일부 멤버 알림 실패해도 다른 멤버들은 알림을 받는다")
        void handleGuildChatMessage_partialFailure_continuesProcessing() {
            // given
            Long guildId = 100L;
            Long messageId = 1L;
            List<String> memberIds = Arrays.asList(SENDER_ID, MEMBER_ID_1, MEMBER_ID_2);

            GuildChatMessageEvent event = new GuildChatMessageEvent(
                SENDER_ID, "발송자닉네임", guildId, "길드명", messageId, "메시지 내용", memberIds
            );

            org.mockito.Mockito.doThrow(new RuntimeException("알림 실패"))
                .when(notificationService).sendNotification(
                    eq(MEMBER_ID_1), eq(NotificationType.GUILD_CHAT),
                    anyLong(), any(), any(), any(), any(), any());

            // when
            notificationEventListener.handleGuildChatMessage(event);

            // then - MEMBER_ID_2는 여전히 알림 받음
            verify(notificationService).sendNotification(
                eq(MEMBER_ID_2), eq(NotificationType.GUILD_CHAT),
                anyLong(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("handleGuildInvitation 테스트")
    class HandleGuildInvitationTest {

        @Test
        @DisplayName("길드 초대 이벤트 처리 시 초대받은 유저에게 알림을 생성한다")
        void handleGuildInvitation_success_createsNotification() {
            // given
            Long guildId = 100L;
            Long invitationId = 1L;
            GuildInvitationEvent event = new GuildInvitationEvent(
                GUILD_MASTER_ID, INVITEE_ID, "마스터닉네임", guildId, "테스트 길드", invitationId, LocalDateTime.now()
            );

            // when
            notificationEventListener.handleGuildInvitation(event);

            // then
            verify(notificationService).sendNotification(
                eq(INVITEE_ID), eq(NotificationType.GUILD_INVITE),
                eq(invitationId), isNull(),
                eq("마스터닉네임"), eq("테스트 길드"));
        }

        @Test
        @DisplayName("NotificationService에서 예외 발생 시 에러를 전파하지 않는다")
        void handleGuildInvitation_notificationServiceThrows_doesNotPropagateError() {
            // given
            GuildInvitationEvent event = new GuildInvitationEvent(
                GUILD_MASTER_ID, INVITEE_ID, "마스터닉네임", 1L, "길드명", 1L
            );

            org.mockito.Mockito.doThrow(new RuntimeException("알림 생성 실패"))
                .when(notificationService).sendNotification(
                    anyString(), eq(NotificationType.GUILD_INVITE),
                    anyLong(), any(), any(), any());

            // when & then - 예외가 전파되지 않음
            notificationEventListener.handleGuildInvitation(event);
        }
    }

    @Nested
    @DisplayName("handleMissionComment 테스트")
    class HandleMissionCommentTest {

        @Test
        @DisplayName("미션 댓글 이벤트 처리 시 미션 생성자에게 알림을 생성한다")
        void handleMissionComment_success_createsNotification() {
            // given
            Long missionId = 100L;
            MissionCommentEvent event = new MissionCommentEvent(
                TARGET_USER_ID, MISSION_CREATOR_ID, "댓글작성자닉네임", missionId, "테스트 미션 제목", LocalDateTime.now()
            );

            // when
            notificationEventListener.handleMissionComment(event);

            // then
            verify(notificationService).sendNotification(
                eq(MISSION_CREATOR_ID), eq(NotificationType.COMMENT_ON_MY_MISSION),
                eq(missionId), isNull(),
                eq("댓글작성자닉네임"), eq("테스트 미션 제목"));
        }

        @Test
        @DisplayName("NotificationService에서 예외 발생 시 에러를 전파하지 않는다")
        void handleMissionComment_notificationServiceThrows_doesNotPropagateError() {
            // given
            MissionCommentEvent event = new MissionCommentEvent(
                TARGET_USER_ID, MISSION_CREATOR_ID, "댓글작성자닉네임", 1L, "미션 제목"
            );

            org.mockito.Mockito.doThrow(new RuntimeException("알림 생성 실패"))
                .when(notificationService).sendNotification(
                    anyString(), eq(NotificationType.COMMENT_ON_MY_MISSION),
                    anyLong(), any(), any(), any());

            // when & then - 예외가 전파되지 않음
            notificationEventListener.handleMissionComment(event);
        }
    }

    @Nested
    @DisplayName("handleGuildCreationEligible 테스트")
    class HandleGuildCreationEligibleTest {

        @Test
        @DisplayName("길드 창설 가능 레벨 도달 이벤트 처리 시 유저에게 알림을 생성한다")
        void handleGuildCreationEligible_success_createsNotification() {
            // given
            GuildCreationEligibleEvent event = new GuildCreationEligibleEvent(
                TARGET_USER_ID, 20, LocalDateTime.now()
            );

            // when
            notificationEventListener.handleGuildCreationEligible(event);

            // then
            verify(notificationService).sendNotification(
                eq(TARGET_USER_ID), eq(NotificationType.GUILD_CREATION_ELIGIBLE),
                isNull(), isNull());
        }

        @Test
        @DisplayName("NotificationService에서 예외 발생 시 에러를 전파하지 않는다")
        void handleGuildCreationEligible_notificationServiceThrows_doesNotPropagateError() {
            // given
            GuildCreationEligibleEvent event = new GuildCreationEligibleEvent(TARGET_USER_ID, 20);

            org.mockito.Mockito.doThrow(new RuntimeException("알림 생성 실패"))
                .when(notificationService).sendNotification(
                    anyString(), eq(NotificationType.GUILD_CREATION_ELIGIBLE),
                    any(), any());

            // when & then - 예외가 전파되지 않음
            notificationEventListener.handleGuildCreationEligible(event);
        }
    }
}
