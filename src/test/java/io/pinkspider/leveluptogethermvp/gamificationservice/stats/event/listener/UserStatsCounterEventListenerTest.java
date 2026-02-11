package io.pinkspider.leveluptogethermvp.gamificationservice.stats.event.listener;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.anyString;

import io.pinkspider.global.event.FeedLikedEvent;
import io.pinkspider.global.event.FeedUnlikedEvent;
import io.pinkspider.global.event.FriendRemovedEvent;
import io.pinkspider.global.event.FriendRequestAcceptedEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.gamificationservice.stats.application.UserStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserStatsCounterEventListenerTest {

    @Mock
    private UserStatsService userStatsService;

    @Mock
    private AchievementService achievementService;

    @InjectMocks
    private UserStatsCounterEventListener listener;

    private static final String LIKER_ID = "liker-user-123";
    private static final String FEED_OWNER_ID = "feed-owner-456";
    private static final String USER_ID = "user-123";
    private static final String REQUESTER_ID = "requester-456";
    private static final String FRIEND_ID = "friend-789";

    @Nested
    @DisplayName("handleFeedLiked 테스트")
    class HandleFeedLikedTest {

        @Test
        @DisplayName("좋아요 카운터를 증가시키고 업적을 체크한다")
        void handleFeedLiked_success() {
            // given
            FeedLikedEvent event = new FeedLikedEvent(LIKER_ID, FEED_OWNER_ID, 1L);

            // when
            listener.handleFeedLiked(event);

            // then
            verify(userStatsService).incrementLikesReceived(FEED_OWNER_ID);
            verify(achievementService).checkAchievementsByDataSource(FEED_OWNER_ID, "FEED_SERVICE");
        }

        @Test
        @DisplayName("예외 발생 시 로그만 남기고 전파하지 않는다")
        void handleFeedLiked_exceptionHandled() {
            // given
            FeedLikedEvent event = new FeedLikedEvent(LIKER_ID, FEED_OWNER_ID, 1L);
            doThrow(new RuntimeException("DB error")).when(userStatsService).incrementLikesReceived(FEED_OWNER_ID);

            // when - 예외가 전파되지 않음
            listener.handleFeedLiked(event);

            // then
            verify(achievementService, never()).checkAchievementsByDataSource(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("handleFeedUnliked 테스트")
    class HandleFeedUnlikedTest {

        @Test
        @DisplayName("좋아요 카운터를 감소시킨다")
        void handleFeedUnliked_success() {
            // given
            FeedUnlikedEvent event = new FeedUnlikedEvent(LIKER_ID, FEED_OWNER_ID, 1L);

            // when
            listener.handleFeedUnliked(event);

            // then
            verify(userStatsService).decrementLikesReceived(FEED_OWNER_ID);
        }

        @Test
        @DisplayName("예외 발생 시 로그만 남기고 전파하지 않는다")
        void handleFeedUnliked_exceptionHandled() {
            // given
            FeedUnlikedEvent event = new FeedUnlikedEvent(LIKER_ID, FEED_OWNER_ID, 1L);
            doThrow(new RuntimeException("DB error")).when(userStatsService).decrementLikesReceived(FEED_OWNER_ID);

            // when - 예외가 전파되지 않음
            listener.handleFeedUnliked(event);
        }
    }

    @Nested
    @DisplayName("handleFriendAccepted 테스트")
    class HandleFriendAcceptedTest {

        @Test
        @DisplayName("양쪽 친구 카운터를 증가시키고 업적을 체크한다")
        void handleFriendAccepted_success() {
            // given
            FriendRequestAcceptedEvent event = new FriendRequestAcceptedEvent(
                USER_ID, REQUESTER_ID, "수락자", 1L
            );

            // when
            listener.handleFriendAccepted(event);

            // then
            verify(userStatsService).incrementFriendCount(USER_ID);
            verify(userStatsService).incrementFriendCount(REQUESTER_ID);
            verify(achievementService).checkAchievementsByDataSource(USER_ID, "FRIEND_SERVICE");
            verify(achievementService).checkAchievementsByDataSource(REQUESTER_ID, "FRIEND_SERVICE");
        }

        @Test
        @DisplayName("예외 발생 시 로그만 남기고 전파하지 않는다")
        void handleFriendAccepted_exceptionHandled() {
            // given
            FriendRequestAcceptedEvent event = new FriendRequestAcceptedEvent(
                USER_ID, REQUESTER_ID, "수락자", 1L
            );
            doThrow(new RuntimeException("DB error")).when(userStatsService).incrementFriendCount(USER_ID);

            // when - 예외가 전파되지 않음
            listener.handleFriendAccepted(event);
        }
    }

    @Nested
    @DisplayName("handleFriendRemoved 테스트")
    class HandleFriendRemovedTest {

        @Test
        @DisplayName("양쪽 친구 카운터를 감소시킨다")
        void handleFriendRemoved_success() {
            // given
            FriendRemovedEvent event = new FriendRemovedEvent(USER_ID, FRIEND_ID);

            // when
            listener.handleFriendRemoved(event);

            // then
            verify(userStatsService).decrementFriendCount(USER_ID);
            verify(userStatsService).decrementFriendCount(FRIEND_ID);
        }

        @Test
        @DisplayName("예외 발생 시 로그만 남기고 전파하지 않는다")
        void handleFriendRemoved_exceptionHandled() {
            // given
            FriendRemovedEvent event = new FriendRemovedEvent(USER_ID, FRIEND_ID);
            doThrow(new RuntimeException("DB error")).when(userStatsService).decrementFriendCount(USER_ID);

            // when - 예외가 전파되지 않음
            listener.handleFriendRemoved(event);
        }
    }
}
