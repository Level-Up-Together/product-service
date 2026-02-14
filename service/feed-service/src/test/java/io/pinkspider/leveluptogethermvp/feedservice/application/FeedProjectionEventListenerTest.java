package io.pinkspider.leveluptogethermvp.feedservice.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserQueryFacadeService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedProjectionEventListener 단위 테스트")
class FeedProjectionEventListenerTest {

    @Mock
    private FeedCommandService feedCommandService;

    @Mock
    private UserQueryFacadeService userQueryFacadeService;

    @InjectMocks
    private FeedProjectionEventListener feedProjectionEventListener;

    private static final String TEST_USER_ID = "test-user-123";
    private static final String REQUESTER_USER_ID = "requester-user-456";
    private UserProfileCache testProfile;
    private UserProfileCache requesterProfile;

    @BeforeEach
    void setUp() {
        testProfile = new UserProfileCache(
            TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
            10, "초보 모험가", TitleRarity.COMMON, "#FFFFFF"
        );
        requesterProfile = new UserProfileCache(
            REQUESTER_USER_ID, "요청자유저", "https://example.com/requester.jpg",
            5, "견습생", TitleRarity.COMMON, "#CCCCCC"
        );
    }

    @Nested
    @DisplayName("handleTitleAcquired 테스트")
    class HandleTitleAcquiredTest {

        @Test
        @DisplayName("칭호 획득 이벤트를 수신하면 피드를 생성한다")
        void handleTitleAcquired_success() {
            // given
            TitleAcquiredEvent event = new TitleAcquiredEvent(TEST_USER_ID, 1L, "전설의 모험가", "LEGENDARY");
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(testProfile);

            // when
            feedProjectionEventListener.handleTitleAcquired(event);

            // then
            verify(feedCommandService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(10), eq("초보 모험가"), eq(TitleRarity.COMMON), eq("#FFFFFF"),
                eq(ActivityType.TITLE_ACQUIRED),
                eq("칭호 획득: 전설의 모험가"), eq("LEGENDARY 등급 칭호를 획득했습니다!"),
                eq("TITLE"), eq(1L), eq("전설의 모험가"),
                eq(FeedVisibility.PUBLIC), isNull(), isNull(), isNull()
            );
        }

        @Test
        @DisplayName("프로필 조회 실패 시 예외를 삼키고 피드를 생성하지 않는다")
        void handleTitleAcquired_profileFetchFails() {
            // given
            TitleAcquiredEvent event = new TitleAcquiredEvent(TEST_USER_ID, 1L, "전설의 모험가", "LEGENDARY");
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID))
                .thenThrow(new RuntimeException("프로필 조회 실패"));

            // when
            feedProjectionEventListener.handleTitleAcquired(event);

            // then
            verify(feedCommandService, never()).createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), anyString(),
                any(TitleRarity.class), anyString(), any(ActivityType.class),
                anyString(), anyString(), anyString(), anyLong(), anyString(),
                any(FeedVisibility.class), any(), anyString(), anyString()
            );
        }
    }

    @Nested
    @DisplayName("handleAchievementCompleted 테스트")
    class HandleAchievementCompletedTest {

        @Test
        @DisplayName("업적 달성 이벤트를 수신하면 피드를 생성한다")
        void handleAchievementCompleted_success() {
            // given
            AchievementCompletedEvent event = new AchievementCompletedEvent(TEST_USER_ID, 10L, "첫 걸음");
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(testProfile);

            // when
            feedProjectionEventListener.handleAchievementCompleted(event);

            // then
            verify(feedCommandService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(10), eq("초보 모험가"), eq(TitleRarity.COMMON), eq("#FFFFFF"),
                eq(ActivityType.ACHIEVEMENT_UNLOCKED),
                eq("업적 달성: 첫 걸음"), eq("업적을 달성했습니다!"),
                eq("ACHIEVEMENT"), eq(10L), eq("첫 걸음"),
                eq(FeedVisibility.PUBLIC), isNull(), isNull(), isNull()
            );
        }

        @Test
        @DisplayName("피드 생성 실패 시 예외를 삼킨다")
        void handleAchievementCompleted_feedCreationFails() {
            // given
            AchievementCompletedEvent event = new AchievementCompletedEvent(TEST_USER_ID, 10L, "첫 걸음");
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(testProfile);
            when(feedCommandService.createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), anyString(),
                any(TitleRarity.class), anyString(), any(ActivityType.class),
                anyString(), anyString(), anyString(), anyLong(), anyString(),
                any(FeedVisibility.class), any(), any(), any()
            )).thenThrow(new RuntimeException("DB 저장 실패"));

            // when - should not throw
            feedProjectionEventListener.handleAchievementCompleted(event);

            // then - verify it was attempted
            verify(feedCommandService).createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), anyString(),
                any(TitleRarity.class), anyString(), any(ActivityType.class),
                anyString(), anyString(), anyString(), anyLong(), anyString(),
                any(FeedVisibility.class), any(), any(), any()
            );
        }
    }

    @Nested
    @DisplayName("handleGuildJoined 테스트")
    class HandleGuildJoinedTest {

        @Test
        @DisplayName("길드 가입 이벤트를 수신하면 피드를 생성한다")
        void handleGuildJoined_success() {
            // given
            GuildJoinedEvent event = new GuildJoinedEvent(TEST_USER_ID, 5L, "최강 길드");
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(testProfile);

            // when
            feedProjectionEventListener.handleGuildJoined(event);

            // then
            verify(feedCommandService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(10), eq("초보 모험가"), eq(TitleRarity.COMMON), eq("#FFFFFF"),
                eq(ActivityType.GUILD_JOINED),
                eq("길드 가입: 최강 길드"), eq("길드에 가입했습니다!"),
                eq("GUILD"), eq(5L), eq("최강 길드"),
                eq(FeedVisibility.PUBLIC), eq(5L), isNull(), isNull()
            );
        }
    }

    @Nested
    @DisplayName("handleFriendRequestAccepted 테스트")
    class HandleFriendRequestAcceptedTest {

        @Test
        @DisplayName("친구 수락 이벤트를 수신하면 양쪽 모두 피드를 생성한다")
        void handleFriendRequestAccepted_createsTwoFeeds() {
            // given
            FriendRequestAcceptedEvent event = new FriendRequestAcceptedEvent(
                TEST_USER_ID, REQUESTER_USER_ID, "테스트유저", 100L
            );
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(testProfile);
            when(userQueryFacadeService.getUserProfile(REQUESTER_USER_ID)).thenReturn(requesterProfile);

            // when
            feedProjectionEventListener.handleFriendRequestAccepted(event);

            // then - 수락자 피드
            verify(feedCommandService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(10), eq("초보 모험가"), eq(TitleRarity.COMMON), eq("#FFFFFF"),
                eq(ActivityType.FRIEND_ADDED),
                eq("새로운 친구!"), eq("새로운 친구가 되었습니다!"),
                isNull(), eq(100L), isNull(),
                eq(FeedVisibility.PUBLIC), isNull(), isNull(), isNull()
            );

            // then - 요청자 피드
            verify(feedCommandService).createActivityFeed(
                eq(REQUESTER_USER_ID), eq("요청자유저"), eq("https://example.com/requester.jpg"),
                eq(5), eq("견습생"), eq(TitleRarity.COMMON), eq("#CCCCCC"),
                eq(ActivityType.FRIEND_ADDED),
                eq("새로운 친구!"), eq("새로운 친구가 되었습니다!"),
                isNull(), eq(100L), isNull(),
                eq(FeedVisibility.PUBLIC), isNull(), isNull(), isNull()
            );

            verify(feedCommandService, times(2)).createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), anyString(),
                any(TitleRarity.class), anyString(), any(ActivityType.class),
                anyString(), anyString(), any(), anyLong(), any(),
                any(FeedVisibility.class), any(), any(), any()
            );
        }

        @Test
        @DisplayName("프로필 조회 실패 시 예외를 삼킨다")
        void handleFriendRequestAccepted_profileFetchFails() {
            // given
            FriendRequestAcceptedEvent event = new FriendRequestAcceptedEvent(
                TEST_USER_ID, REQUESTER_USER_ID, "테스트유저", 100L
            );
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID))
                .thenThrow(new RuntimeException("프로필 조회 실패"));

            // when
            feedProjectionEventListener.handleFriendRequestAccepted(event);

            // then
            verify(feedCommandService, never()).createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), anyString(),
                any(TitleRarity.class), anyString(), any(ActivityType.class),
                anyString(), anyString(), any(), any(), any(),
                any(FeedVisibility.class), any(), any(), any()
            );
        }
    }

    @Nested
    @DisplayName("handleUserLevelUp 테스트")
    class HandleUserLevelUpTest {

        @Test
        @DisplayName("레벨업 이벤트를 수신하면 피드를 생성한다")
        void handleUserLevelUp_success() {
            // given
            UserLevelUpEvent event = new UserLevelUpEvent(TEST_USER_ID, 15, 5000L);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(testProfile);

            // when
            feedProjectionEventListener.handleUserLevelUp(event);

            // then
            verify(feedCommandService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(15), eq("초보 모험가"), eq(TitleRarity.COMMON), eq("#FFFFFF"),
                eq(ActivityType.LEVEL_UP),
                eq("레벨 15 달성!"), eq("레벨 15에 도달했습니다!"),
                isNull(), isNull(), isNull(),
                eq(FeedVisibility.PUBLIC), isNull(), isNull(), isNull()
            );
        }
    }

    @Nested
    @DisplayName("handleGuildCreated 테스트")
    class HandleGuildCreatedTest {

        @Test
        @DisplayName("길드 창설 이벤트를 수신하면 피드를 생성한다")
        void handleGuildCreated_success() {
            // given
            GuildCreatedEvent event = new GuildCreatedEvent(TEST_USER_ID, 7L, "새로운 길드");
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(testProfile);

            // when
            feedProjectionEventListener.handleGuildCreated(event);

            // then
            verify(feedCommandService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(10), eq("초보 모험가"), eq(TitleRarity.COMMON), eq("#FFFFFF"),
                eq(ActivityType.GUILD_CREATED),
                eq("길드 창설: 새로운 길드"), eq("새로운 길드를 만들었습니다!"),
                eq("GUILD"), eq(7L), eq("새로운 길드"),
                eq(FeedVisibility.PUBLIC), eq(7L), isNull(), isNull()
            );
        }
    }

    @Nested
    @DisplayName("handleGuildLevelUp 테스트")
    class HandleGuildLevelUpTest {

        @Test
        @DisplayName("길드 레벨업 이벤트를 수신하면 피드를 생성한다")
        void handleGuildLevelUp_success() {
            // given
            GuildLevelUpEvent event = new GuildLevelUpEvent(TEST_USER_ID, 5L, "최강 길드", 3);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(testProfile);

            // when
            feedProjectionEventListener.handleGuildLevelUp(event);

            // then
            verify(feedCommandService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(10), eq("초보 모험가"), eq(TitleRarity.COMMON), eq("#FFFFFF"),
                eq(ActivityType.GUILD_LEVEL_UP),
                eq("길드 레벨업!"), eq("최강 길드 길드가 레벨 3에 도달했습니다!"),
                eq("GUILD"), eq(5L), eq("최강 길드"),
                eq(FeedVisibility.PUBLIC), eq(5L), isNull(), isNull()
            );
        }
    }

    @Nested
    @DisplayName("handleAttendanceStreak 테스트")
    class HandleAttendanceStreakTest {

        @Test
        @DisplayName("연속 출석 이벤트를 수신하면 피드를 생성한다")
        void handleAttendanceStreak_success() {
            // given
            AttendanceStreakEvent event = new AttendanceStreakEvent(TEST_USER_ID, 30);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID)).thenReturn(testProfile);

            // when
            feedProjectionEventListener.handleAttendanceStreak(event);

            // then
            verify(feedCommandService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(10), eq("초보 모험가"), eq(TitleRarity.COMMON), eq("#FFFFFF"),
                eq(ActivityType.ATTENDANCE_STREAK),
                eq("30일 연속 출석!"), eq("30일 연속 출석을 달성했습니다!"),
                isNull(), isNull(), isNull(),
                eq(FeedVisibility.PUBLIC), isNull(), isNull(), isNull()
            );
        }

        @Test
        @DisplayName("예외 발생 시 삼킨다")
        void handleAttendanceStreak_exceptionSwallowed() {
            // given
            AttendanceStreakEvent event = new AttendanceStreakEvent(TEST_USER_ID, 7);
            when(userQueryFacadeService.getUserProfile(TEST_USER_ID))
                .thenThrow(new RuntimeException("캐시 실패"));

            // when - should not throw
            feedProjectionEventListener.handleAttendanceStreak(event);

            // then
            verify(feedCommandService, never()).createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), anyString(),
                any(TitleRarity.class), anyString(), any(ActivityType.class),
                anyString(), anyString(), any(), any(), any(),
                any(FeedVisibility.class), any(), any(), any()
            );
        }
    }

    @Nested
    @DisplayName("handleTitleEquipped 테스트")
    class HandleTitleEquippedTest {

        @Test
        @DisplayName("칭호 장착 이벤트를 수신하면 피드 칭호를 업데이트한다")
        void handleTitleEquipped_success() {
            // given
            TitleEquippedEvent event = new TitleEquippedEvent(
                TEST_USER_ID, "전설적인 모험가", TitleRarity.LEGENDARY, "#FFD700");
            when(feedCommandService.updateFeedTitles(
                TEST_USER_ID, "전설적인 모험가", TitleRarity.LEGENDARY, "#FFD700"))
                .thenReturn(5);

            // when
            feedProjectionEventListener.handleTitleEquipped(event);

            // then
            verify(feedCommandService).updateFeedTitles(
                TEST_USER_ID, "전설적인 모험가", TitleRarity.LEGENDARY, "#FFD700");
        }

        @Test
        @DisplayName("피드 업데이트 실패 시 예외를 삼킨다")
        void handleTitleEquipped_failure() {
            // given
            TitleEquippedEvent event = new TitleEquippedEvent(
                TEST_USER_ID, "전설적인 모험가", TitleRarity.LEGENDARY, "#FFD700");
            when(feedCommandService.updateFeedTitles(anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB 오류"));

            // when - 예외가 발생하지 않아야 함
            feedProjectionEventListener.handleTitleEquipped(event);

            // then
            verify(feedCommandService).updateFeedTitles(anyString(), any(), any(), any());
        }
    }
}
