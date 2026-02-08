package io.pinkspider.global.event.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.pinkspider.global.event.AchievementCompletedEvent;
import io.pinkspider.global.event.FeedCommentEvent;
import io.pinkspider.global.event.FriendRequestAcceptedEvent;
import io.pinkspider.global.event.FriendRequestEvent;
import io.pinkspider.global.event.FriendRequestProcessedEvent;
import io.pinkspider.global.event.FriendRequestRejectedEvent;
import io.pinkspider.global.event.GuildBulletinCreatedEvent;
import io.pinkspider.global.event.GuildMissionArrivedEvent;
import io.pinkspider.global.event.TitleAcquiredEvent;
import io.pinkspider.leveluptogethermvp.notificationservice.application.NotificationService;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.NotificationType;
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

    @InjectMocks
    private NotificationEventListener eventListener;

    @Nested
    @DisplayName("칭호 획득 이벤트 처리")
    class HandleTitleAcquiredTest {

        @Test
        @DisplayName("칭호 획득 이벤트 발생 시 알림 서비스 호출")
        void shouldCallNotificationServiceOnTitleAcquired() {
            // given
            TitleAcquiredEvent event = new TitleAcquiredEvent(
                "user-123", 1L, "전설적인 모험가", "LEGENDARY"
            );

            // when
            eventListener.handleTitleAcquired(event);

            // then
            verify(notificationService).sendNotification(
                eq("user-123"), eq(NotificationType.TITLE_ACQUIRED),
                eq(1L), eq("rarity:LEGENDARY"), eq("전설적인 모험가")
            );
        }

        @Test
        @DisplayName("알림 서비스 실패해도 예외를 던지지 않음")
        void shouldNotThrowExceptionOnNotificationFailure() {
            // given
            TitleAcquiredEvent event = new TitleAcquiredEvent(
                "user-123", 1L, "전설적인 모험가", "LEGENDARY"
            );
            doThrow(new RuntimeException("알림 전송 실패"))
                .when(notificationService).sendNotification(
                    anyString(), eq(NotificationType.TITLE_ACQUIRED),
                    anyLong(), anyString(), anyString());

            // when & then - 예외가 발생하지 않아야 함
            eventListener.handleTitleAcquired(event);
        }
    }

    @Nested
    @DisplayName("업적 달성 이벤트 처리")
    class HandleAchievementCompletedTest {

        @Test
        @DisplayName("업적 달성 이벤트 발생 시 알림 서비스 호출")
        void shouldCallNotificationServiceOnAchievementCompleted() {
            // given
            AchievementCompletedEvent event = new AchievementCompletedEvent(
                "user-123", 1L, "첫 미션 완료"
            );

            // when
            eventListener.handleAchievementCompleted(event);

            // then
            verify(notificationService).sendNotification(
                eq("user-123"), eq(NotificationType.ACHIEVEMENT_COMPLETED),
                eq(1L), isNull(), eq("첫 미션 완료")
            );
        }
    }

    @Nested
    @DisplayName("친구 요청 이벤트 처리")
    class HandleFriendRequestTest {

        @Test
        @DisplayName("친구 요청 이벤트 발생 시 알림 서비스 호출")
        void shouldCallNotificationServiceOnFriendRequest() {
            // given
            FriendRequestEvent event = new FriendRequestEvent(
                "requester-123", "target-456", "테스트유저", 1L
            );

            // when
            eventListener.handleFriendRequest(event);

            // then
            verify(notificationService).sendNotification(
                eq("target-456"), eq(NotificationType.FRIEND_REQUEST),
                eq(1L), isNull(), eq("테스트유저")
            );
        }
    }

    @Nested
    @DisplayName("친구 요청 수락 이벤트 처리")
    class HandleFriendRequestAcceptedTest {

        @Test
        @DisplayName("친구 요청 수락 이벤트 발생 시 알림 서비스 호출")
        void shouldCallNotificationServiceOnFriendRequestAccepted() {
            // given
            FriendRequestAcceptedEvent event = new FriendRequestAcceptedEvent(
                "accepter-123", "requester-456", "수락자닉네임", 1L
            );

            // when
            eventListener.handleFriendRequestAccepted(event);

            // then
            verify(notificationService).sendNotification(
                eq("requester-456"), eq(NotificationType.FRIEND_ACCEPTED),
                eq(1L), isNull(), eq("수락자닉네임")
            );
        }
    }

    @Nested
    @DisplayName("친구 요청 거절 이벤트 처리")
    class HandleFriendRequestRejectedTest {

        @Test
        @DisplayName("친구 요청 거절 이벤트 발생 시 알림 서비스 호출")
        void shouldCallNotificationServiceOnFriendRequestRejected() {
            // given
            FriendRequestRejectedEvent event = new FriendRequestRejectedEvent(
                "rejecter-123", "requester-456", "거절자닉네임", 1L
            );

            // when
            eventListener.handleFriendRequestRejected(event);

            // then
            verify(notificationService).sendNotification(
                eq("requester-456"), eq(NotificationType.FRIEND_REJECTED),
                eq(1L), isNull(), eq("거절자닉네임")
            );
        }
    }

    @Nested
    @DisplayName("친구 요청 처리 완료 이벤트")
    class HandleFriendRequestProcessedTest {

        @Test
        @DisplayName("친구 요청 처리 완료 시 알림 삭제 호출")
        void shouldDeleteNotificationOnFriendRequestProcessed() {
            // given
            FriendRequestProcessedEvent event = new FriendRequestProcessedEvent(
                "user-123", 1L
            );

            // when
            eventListener.handleFriendRequestProcessed(event);

            // then
            verify(notificationService).deleteByReference(
                eq("FRIEND_REQUEST"), eq(1L)
            );
        }
    }

    @Nested
    @DisplayName("길드 미션 도착 이벤트 처리")
    class HandleGuildMissionArrivedTest {

        @Test
        @DisplayName("길드 미션 도착 이벤트 발생 시 각 멤버에게 알림 전송")
        void shouldNotifyAllMembersOnGuildMissionArrived() {
            // given
            List<String> memberIds = List.of("member-1", "member-2", "member-3");
            GuildMissionArrivedEvent event = new GuildMissionArrivedEvent(
                "creator-123", memberIds, 1L, "테스트 미션"
            );

            // when
            eventListener.handleGuildMissionArrived(event);

            // then
            verify(notificationService, times(3)).sendNotification(
                anyString(), eq(NotificationType.GUILD_MISSION_ARRIVED),
                eq(1L), isNull(), eq("테스트 미션")
            );
        }

        @Test
        @DisplayName("일부 멤버 알림 실패해도 나머지는 계속 처리")
        void shouldContinueOnPartialFailure() {
            // given
            List<String> memberIds = List.of("member-1", "member-2", "member-3");
            GuildMissionArrivedEvent event = new GuildMissionArrivedEvent(
                "creator-123", memberIds, 1L, "테스트 미션"
            );

            doThrow(new RuntimeException("알림 실패"))
                .when(notificationService).sendNotification(
                    eq("member-2"), eq(NotificationType.GUILD_MISSION_ARRIVED),
                    anyLong(), any(), any());

            // when
            eventListener.handleGuildMissionArrived(event);

            // then - member-1, member-3도 호출되어야 함
            verify(notificationService, times(3)).sendNotification(
                anyString(), eq(NotificationType.GUILD_MISSION_ARRIVED),
                eq(1L), isNull(), eq("테스트 미션")
            );
        }
    }

    @Nested
    @DisplayName("피드 댓글 이벤트 처리")
    class HandleFeedCommentTest {

        @Test
        @DisplayName("피드 댓글 이벤트 발생 시 피드 주인에게 알림 전송")
        void shouldNotifyFeedOwnerOnComment() {
            // given
            FeedCommentEvent event = new FeedCommentEvent(
                "commenter-123", "owner-456", "댓글러닉네임", 1L
            );

            // when
            eventListener.handleFeedComment(event);

            // then
            verify(notificationService).sendNotification(
                eq("owner-456"), eq(NotificationType.COMMENT_ON_MY_FEED),
                eq(1L), isNull(), eq("댓글러닉네임")
            );
        }

        @Test
        @DisplayName("자기 글에 자신이 댓글 달면 알림 전송하지 않음")
        void shouldNotNotifyWhenCommentingOnOwnFeed() {
            // given
            FeedCommentEvent event = new FeedCommentEvent(
                "user-123", "user-123", "유저닉네임", 1L
            );

            // when
            eventListener.handleFeedComment(event);

            // then
            verify(notificationService, never()).sendNotification(
                anyString(), any(NotificationType.class), anyLong(), any(), any());
        }
    }

    @Nested
    @DisplayName("길드 공지사항 등록 이벤트 처리")
    class HandleGuildBulletinCreatedTest {

        @Test
        @DisplayName("길드 공지사항 등록 이벤트 발생 시 각 멤버에게 알림 전송")
        void shouldNotifyAllMembersOnGuildBulletinCreated() {
            // given
            List<String> memberIds = List.of("member-1", "member-2", "member-3");
            GuildBulletinCreatedEvent event = new GuildBulletinCreatedEvent(
                "author-123", memberIds, 1L, "테스트 길드", 10L, "중요 공지사항"
            );

            // when
            eventListener.handleGuildBulletinCreated(event);

            // then
            verify(notificationService, times(3)).sendNotification(
                anyString(), eq(NotificationType.GUILD_BULLETIN),
                eq(10L), isNull(),
                eq("테스트 길드"), eq("중요 공지사항"), eq("1")
            );
        }

        @Test
        @DisplayName("일부 멤버 알림 실패해도 나머지는 계속 처리")
        void shouldContinueOnPartialFailure() {
            // given
            List<String> memberIds = List.of("member-1", "member-2", "member-3");
            GuildBulletinCreatedEvent event = new GuildBulletinCreatedEvent(
                "author-123", memberIds, 1L, "테스트 길드", 10L, "중요 공지사항"
            );

            doThrow(new RuntimeException("알림 실패"))
                .when(notificationService).sendNotification(
                    eq("member-2"), eq(NotificationType.GUILD_BULLETIN),
                    anyLong(), any(), any(), any(), any());

            // when
            eventListener.handleGuildBulletinCreated(event);

            // then - member-1, member-3도 호출되어야 함
            verify(notificationService, times(3)).sendNotification(
                anyString(), eq(NotificationType.GUILD_BULLETIN),
                eq(10L), isNull(),
                eq("테스트 길드"), eq("중요 공지사항"), eq("1")
            );
        }
    }
}
