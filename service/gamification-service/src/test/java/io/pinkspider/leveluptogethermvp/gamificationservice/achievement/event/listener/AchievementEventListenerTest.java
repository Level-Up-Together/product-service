package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.event.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.pinkspider.global.event.GuildJoinedEvent;
import io.pinkspider.global.event.GuildMasterAssignedEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.event.AchievementCheckRequestedEvent;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AchievementEventListener 테스트")
class AchievementEventListenerTest {

    @Mock
    private AchievementService achievementService;

    @InjectMocks
    private AchievementEventListener eventListener;

    @Nested
    @DisplayName("길드 가입 이벤트 처리")
    class HandleGuildJoinedTest {

        @Test
        @DisplayName("길드 가입 이벤트 발생 시 업적 서비스 호출")
        void shouldCallAchievementServiceOnGuildJoined() {
            // given
            GuildJoinedEvent event = new GuildJoinedEvent(
                "user-123", 1L, "테스트 길드"
            );

            // when
            eventListener.handleGuildJoined(event);

            // then
            verify(achievementService).checkAchievementsByDataSource("user-123", "GUILD_SERVICE");
        }

        @Test
        @DisplayName("업적 서비스 실패해도 예외를 던지지 않음")
        void shouldNotThrowExceptionOnAchievementFailure() {
            // given
            GuildJoinedEvent event = new GuildJoinedEvent(
                "user-123", 1L, "테스트 길드"
            );
            doThrow(new RuntimeException("업적 체크 실패"))
                .when(achievementService).checkAchievementsByDataSource(any(), eq("GUILD_SERVICE"));

            // when & then - 예외가 발생하지 않아야 함
            eventListener.handleGuildJoined(event);
        }
    }

    @Nested
    @DisplayName("길드 마스터 할당 이벤트 처리")
    class HandleGuildMasterAssignedTest {

        @Test
        @DisplayName("길드 마스터 할당 이벤트 발생 시 업적 서비스 호출")
        void shouldCallAchievementServiceOnGuildMasterAssigned() {
            // given
            GuildMasterAssignedEvent event = new GuildMasterAssignedEvent(
                "user-123", 1L, "테스트 길드"
            );

            // when
            eventListener.handleGuildMasterAssigned(event);

            // then
            verify(achievementService).checkAchievementsByDataSource("user-123", "GUILD_SERVICE");
        }

        @Test
        @DisplayName("업적 서비스 실패해도 예외를 던지지 않음")
        void shouldNotThrowExceptionOnAchievementFailure() {
            // given
            GuildMasterAssignedEvent event = new GuildMasterAssignedEvent(
                "user-123", 1L, "테스트 길드"
            );
            doThrow(new RuntimeException("업적 체크 실패"))
                .when(achievementService).checkAchievementsByDataSource(any(), eq("GUILD_SERVICE"));

            // when & then - 예외가 발생하지 않아야 함
            eventListener.handleGuildMasterAssigned(event);
        }
    }

    @Nested
    @DisplayName("커밋 후 업적 체크 요청 이벤트 처리")
    class HandleAchievementCheckRequestedTest {

        @Test
        @DisplayName("요청된 모든 데이터 소스를 순서대로 체크한다")
        void shouldCheckAllRequestedDataSources() {
            // given
            AchievementCheckRequestedEvent event = new AchievementCheckRequestedEvent(
                "user-123", List.of("USER_EXPERIENCE", "USER_CATEGORY_EXPERIENCE")
            );

            // when
            eventListener.handleAchievementCheckRequested(event);

            // then
            verify(achievementService).checkAchievementsByDataSource("user-123", "USER_EXPERIENCE");
            verify(achievementService).checkAchievementsByDataSource("user-123", "USER_CATEGORY_EXPERIENCE");
        }

        @Test
        @DisplayName("한 데이터 소스 체크가 실패해도 나머지 소스는 계속 체크한다")
        void shouldContinueWithRemainingDataSourcesOnFailure() {
            // given
            AchievementCheckRequestedEvent event = new AchievementCheckRequestedEvent(
                "user-123", List.of("USER_EXPERIENCE", "USER_CATEGORY_EXPERIENCE")
            );
            doThrow(new RuntimeException("업적 체크 실패"))
                .when(achievementService).checkAchievementsByDataSource("user-123", "USER_EXPERIENCE");

            // when - 예외가 발생하지 않아야 함
            eventListener.handleAchievementCheckRequested(event);

            // then - 두 번째 소스는 정상 체크
            verify(achievementService).checkAchievementsByDataSource("user-123", "USER_CATEGORY_EXPERIENCE");
        }
    }
}
