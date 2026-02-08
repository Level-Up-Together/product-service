package io.pinkspider.leveluptogethermvp.userservice.feed.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.userservice.feed.domain.dto.UserTitleInfo;
import static io.pinkspider.global.test.TestReflectionUtils.setId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityFeedNotificationServiceTest {

    @Mock
    private ActivityFeedService activityFeedService;

    @Mock
    private UserTitleInfoHelper userTitleInfoHelper;

    @InjectMocks
    private ActivityFeedNotificationService notificationService;

    private static final String TEST_USER_ID = "test-user-123";
    private static final String OTHER_USER_ID = "other-user-456";

    private ActivityFeed createTestFeed(Long id, String userId) {
        ActivityFeed feed = ActivityFeed.builder()
            .userId(userId)
            .activityType(ActivityType.MISSION_JOINED)
            .visibility(FeedVisibility.PUBLIC)
            .likeCount(0)
            .commentCount(0)
            .build();
        setId(feed, id);
        return feed;
    }

    @Nested
    @DisplayName("System Activity Feed 헬퍼 메서드 테스트")
    class SystemActivityFeedHelperTest {

        @Test
        @DisplayName("미션 참여 알림 피드를 생성한다")
        void notifyMissionJoined_success() {
            // given
            when(userTitleInfoHelper.getUserEquippedTitleInfo(TEST_USER_ID)).thenReturn(UserTitleInfo.empty());
            when(activityFeedService.createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), any(), any(), any(),
                any(ActivityType.class), anyString(), any(),
                anyString(), anyLong(), anyString(),
                any(FeedVisibility.class), any(), any(), any()
            )).thenReturn(createTestFeed(1L, TEST_USER_ID));

            // when
            notificationService.notifyMissionJoined(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 1L, "테스트 미션"
            );

            // then
            verify(activityFeedService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(5), isNull(), isNull(), isNull(),
                eq(ActivityType.MISSION_JOINED), anyString(), isNull(),
                eq("MISSION"), eq(1L), eq("테스트 미션"),
                eq(FeedVisibility.PUBLIC), isNull(), isNull(), isNull()
            );
        }

        @Test
        @DisplayName("레벨업 알림 피드를 생성한다")
        void notifyLevelUp_success() {
            // given
            when(userTitleInfoHelper.getUserEquippedTitleInfo(TEST_USER_ID)).thenReturn(UserTitleInfo.empty());
            when(activityFeedService.createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), any(), any(), any(),
                any(ActivityType.class), anyString(), any(),
                anyString(), anyLong(), anyString(),
                any(FeedVisibility.class), any(), any(), any()
            )).thenReturn(createTestFeed(1L, TEST_USER_ID));

            // when
            notificationService.notifyLevelUp(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                10, 5000
            );

            // then
            verify(activityFeedService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(10), isNull(), isNull(), isNull(),
                eq(ActivityType.LEVEL_UP), anyString(), anyString(),
                eq("LEVEL"), eq(10L), eq("레벨 10"),
                eq(FeedVisibility.PUBLIC), isNull(), isNull(), isNull()
            );
        }

        @Test
        @DisplayName("길드 가입 알림 피드를 생성한다")
        void notifyGuildJoined_success() {
            // given
            when(userTitleInfoHelper.getUserEquippedTitleInfo(TEST_USER_ID)).thenReturn(UserTitleInfo.empty());
            when(activityFeedService.createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), any(), any(), any(),
                any(ActivityType.class), anyString(), any(),
                anyString(), anyLong(), anyString(),
                any(FeedVisibility.class), any(), any(), any()
            )).thenReturn(createTestFeed(1L, TEST_USER_ID));

            // when
            notificationService.notifyGuildJoined(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 1L, "테스트 길드"
            );

            // then
            verify(activityFeedService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(5), isNull(), isNull(), isNull(),
                eq(ActivityType.GUILD_JOINED), anyString(), isNull(),
                eq("GUILD"), eq(1L), eq("테스트 길드"),
                eq(FeedVisibility.PUBLIC), eq(1L), isNull(), isNull()
            );
        }
    }

    @Nested
    @DisplayName("추가 알림 피드 생성 테스트")
    class AdditionalNotifyTest {

        @Test
        @DisplayName("미션 완료 알림 피드를 생성한다")
        void notifyMissionCompleted_success() {
            // given
            when(userTitleInfoHelper.getUserEquippedTitleInfo(TEST_USER_ID)).thenReturn(UserTitleInfo.empty());
            when(activityFeedService.createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), any(), any(), any(),
                any(ActivityType.class), anyString(), any(),
                anyString(), anyLong(), anyString(),
                any(FeedVisibility.class), any(), any(), any()
            )).thenReturn(createTestFeed(1L, TEST_USER_ID));

            // when
            notificationService.notifyMissionCompleted(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 1L, "테스트 미션", 50
            );

            // then
            verify(activityFeedService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(5), isNull(), isNull(), isNull(),
                eq(ActivityType.MISSION_COMPLETED), anyString(), anyString(),
                eq("MISSION"), eq(1L), eq("테스트 미션"),
                eq(FeedVisibility.PUBLIC), isNull(), isNull(), isNull()
            );
        }

        @Test
        @DisplayName("미션 전체 완료 알림 피드를 생성한다")
        void notifyMissionFullCompleted_success() {
            // given
            when(userTitleInfoHelper.getUserEquippedTitleInfo(TEST_USER_ID)).thenReturn(UserTitleInfo.empty());
            when(activityFeedService.createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), any(), any(), any(),
                any(ActivityType.class), anyString(), any(),
                anyString(), anyLong(), anyString(),
                any(FeedVisibility.class), any(), any(), any()
            )).thenReturn(createTestFeed(1L, TEST_USER_ID));

            // when
            notificationService.notifyMissionFullCompleted(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 1L, "테스트 미션"
            );

            // then
            verify(activityFeedService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(5), isNull(), isNull(), isNull(),
                eq(ActivityType.MISSION_FULL_COMPLETED), anyString(), isNull(),
                eq("MISSION"), eq(1L), eq("테스트 미션"),
                eq(FeedVisibility.PUBLIC), isNull(), isNull(), isNull()
            );
        }

        @Test
        @DisplayName("업적 달성 알림 피드를 생성한다")
        void notifyAchievementUnlocked_success() {
            // given
            when(userTitleInfoHelper.getUserEquippedTitleInfo(TEST_USER_ID)).thenReturn(UserTitleInfo.empty());
            when(activityFeedService.createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), any(), any(), any(),
                any(ActivityType.class), anyString(), any(),
                anyString(), anyLong(), anyString(),
                any(FeedVisibility.class), any(), any(), any()
            )).thenReturn(createTestFeed(1L, TEST_USER_ID));

            // when
            notificationService.notifyAchievementUnlocked(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 1L, "첫 미션 완료", "BRONZE"
            );

            // then
            verify(activityFeedService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(5), isNull(), isNull(), isNull(),
                eq(ActivityType.ACHIEVEMENT_UNLOCKED), anyString(), eq("BRONZE"),
                eq("ACHIEVEMENT"), eq(1L), eq("첫 미션 완료"),
                eq(FeedVisibility.PUBLIC), isNull(), isNull(), isNull()
            );
        }

        @Test
        @DisplayName("칭호 획득 알림 피드를 생성한다")
        void notifyTitleAcquired_success() {
            // given
            when(userTitleInfoHelper.getUserEquippedTitleInfo(TEST_USER_ID)).thenReturn(UserTitleInfo.empty());
            when(activityFeedService.createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), any(), any(), any(),
                any(ActivityType.class), anyString(), any(),
                anyString(), anyLong(), anyString(),
                any(FeedVisibility.class), any(), any(), any()
            )).thenReturn(createTestFeed(1L, TEST_USER_ID));

            // when
            notificationService.notifyTitleAcquired(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 1L, "전설적인"
            );

            // then
            verify(activityFeedService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(5), isNull(), isNull(), isNull(),
                eq(ActivityType.TITLE_ACQUIRED), anyString(), isNull(),
                eq("TITLE"), eq(1L), eq("전설적인"),
                eq(FeedVisibility.PUBLIC), isNull(), isNull(), isNull()
            );
        }

        @Test
        @DisplayName("길드 생성 알림 피드를 생성한다")
        void notifyGuildCreated_success() {
            // given
            when(userTitleInfoHelper.getUserEquippedTitleInfo(TEST_USER_ID)).thenReturn(UserTitleInfo.empty());
            when(activityFeedService.createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), any(), any(), any(),
                any(ActivityType.class), anyString(), any(),
                anyString(), anyLong(), anyString(),
                any(FeedVisibility.class), any(), any(), any()
            )).thenReturn(createTestFeed(1L, TEST_USER_ID));

            // when
            notificationService.notifyGuildCreated(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 1L, "테스트 길드"
            );

            // then
            verify(activityFeedService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(5), isNull(), isNull(), isNull(),
                eq(ActivityType.GUILD_CREATED), anyString(), isNull(),
                eq("GUILD"), eq(1L), eq("테스트 길드"),
                eq(FeedVisibility.PUBLIC), eq(1L), isNull(), isNull()
            );
        }

        @Test
        @DisplayName("길드 레벨업 알림 피드를 생성한다")
        void notifyGuildLevelUp_success() {
            // given
            when(userTitleInfoHelper.getUserEquippedTitleInfo(TEST_USER_ID)).thenReturn(UserTitleInfo.empty());
            when(activityFeedService.createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), any(), any(), any(),
                any(ActivityType.class), anyString(), any(),
                anyString(), anyLong(), anyString(),
                any(FeedVisibility.class), any(), any(), any()
            )).thenReturn(createTestFeed(1L, TEST_USER_ID));

            // when
            notificationService.notifyGuildLevelUp(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 1L, "테스트 길드", 10
            );

            // then
            verify(activityFeedService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(5), isNull(), isNull(), isNull(),
                eq(ActivityType.GUILD_LEVEL_UP), anyString(), isNull(),
                eq("GUILD"), eq(1L), eq("테스트 길드"),
                eq(FeedVisibility.GUILD), eq(1L), isNull(), isNull()
            );
        }

        @Test
        @DisplayName("친구 추가 알림 피드를 생성한다")
        void notifyFriendAdded_success() {
            // given
            when(userTitleInfoHelper.getUserEquippedTitleInfo(TEST_USER_ID)).thenReturn(UserTitleInfo.empty());
            when(activityFeedService.createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), any(), any(), any(),
                any(ActivityType.class), anyString(), any(),
                any(), any(), anyString(),
                any(FeedVisibility.class), any(), any(), any()
            )).thenReturn(createTestFeed(1L, TEST_USER_ID));

            // when
            notificationService.notifyFriendAdded(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, OTHER_USER_ID, "친구유저"
            );

            // then
            verify(activityFeedService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(5), isNull(), isNull(), isNull(),
                eq(ActivityType.FRIEND_ADDED), anyString(), isNull(),
                eq("USER"), isNull(), eq("친구유저"),
                eq(FeedVisibility.FRIENDS), isNull(), isNull(), isNull()
            );
        }

        @Test
        @DisplayName("연속 출석 알림 피드를 생성한다")
        void notifyAttendanceStreak_success() {
            // given
            when(userTitleInfoHelper.getUserEquippedTitleInfo(TEST_USER_ID)).thenReturn(UserTitleInfo.empty());
            when(activityFeedService.createActivityFeed(
                anyString(), anyString(), anyString(), anyInt(), any(), any(), any(),
                any(ActivityType.class), anyString(), any(),
                anyString(), anyLong(), anyString(),
                any(FeedVisibility.class), any(), any(), any()
            )).thenReturn(createTestFeed(1L, TEST_USER_ID));

            // when
            notificationService.notifyAttendanceStreak(
                TEST_USER_ID, "테스트유저", "https://example.com/profile.jpg",
                5, 7
            );

            // then
            verify(activityFeedService).createActivityFeed(
                eq(TEST_USER_ID), eq("테스트유저"), eq("https://example.com/profile.jpg"),
                eq(5), isNull(), isNull(), isNull(),
                eq(ActivityType.ATTENDANCE_STREAK), anyString(), isNull(),
                eq("ATTENDANCE"), eq(7L), eq("7일 연속 출석"),
                eq(FeedVisibility.PUBLIC), isNull(), isNull(), isNull()
            );
        }
    }
}
