package io.pinkspider.leveluptogethermvp.gamificationservice.stats.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserStatsRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.stats.domain.dto.UserStatsResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class UserStatsServiceTest {

    @Mock private UserStatsRepository userStatsRepository;

    @Mock private UserStatsCreator userStatsCreator;

    @Mock private UserQueryFacade userQueryFacade;

    @Mock private GuildQueryFacade guildQueryFacade;

    @InjectMocks private UserStatsService userStatsService;

    private static final String TEST_USER_ID = "test-user-123";

    private UserStats createTestUserStats(
            Long id, String userId, int totalMissionCompletions, int currentStreak) {
        UserStats stats =
                UserStats.builder()
                        .userId(userId)
                        .totalMissionCompletions(totalMissionCompletions)
                        .totalMissionFullCompletions(5)
                        .totalTitlesAcquired(3)
                        .totalAchievementsCompleted(2)
                        .currentStreak(currentStreak)
                        .maxStreak(currentStreak)
                        .rankingPoints(100L)
                        .build();
        setId(stats, id);
        return stats;
    }

    @Nested
    @DisplayName("getOrCreateUserStats 테스트")
    class GetOrCreateUserStatsTest {

        @Test
        @DisplayName("기존 사용자 통계를 반환한다")
        void getOrCreateUserStats_exists() {
            // given
            UserStats existingStats = createTestUserStats(1L, TEST_USER_ID, 10, 5);

            when(userStatsRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(existingStats));

            // when
            UserStats result = userStatsService.getOrCreateUserStats(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getTotalMissionCompletions()).isEqualTo(10);
        }

        @Test
        @DisplayName("사용자 통계가 없으면 별도 트랜잭션으로 생성하고 재조회한 managed 엔티티를 반환한다")
        void getOrCreateUserStats_creates() {
            // given — 생성 전엔 비어 있고, creator 가 행을 만든 뒤에는 조회된다
            UserStats created = createTestUserStats(1L, TEST_USER_ID, 0, 0);
            AtomicReference<UserStats> row = new AtomicReference<>();
            when(userStatsRepository.findByUserId(TEST_USER_ID))
                    .thenAnswer(invocation -> Optional.ofNullable(row.get()));
            doAnswer(
                            invocation -> {
                                row.set(created);
                                return null;
                            })
                    .when(userStatsCreator)
                    .create(TEST_USER_ID);

            // when
            UserStats result = userStatsService.getOrCreateUserStats(TEST_USER_ID);

            // then
            assertThat(result).isSameAs(created);
            verify(userStatsCreator).create(TEST_USER_ID);
            // LUT-538: 생성은 creator(REQUIRES_NEW) 담당 — 이 트랜잭션에서 직접 insert 하지 않는다
            verify(userStatsRepository, never()).save(any(UserStats.class));
        }

        @Test
        @DisplayName("LUT-538: 동시 요청이 먼저 생성해 중복 예외가 나도 그 행을 재조회해 반환한다")
        void getOrCreateUserStats_duplicateKey_returnsExistingRow() {
            // given — 신규 가입 직후 /api/v1/mypage 2건이 동시에 첫 행을 만들려는 상황
            UserStats createdByOther = createTestUserStats(2L, TEST_USER_ID, 0, 0);
            AtomicReference<UserStats> row = new AtomicReference<>();
            when(userStatsRepository.findByUserId(TEST_USER_ID))
                    .thenAnswer(invocation -> Optional.ofNullable(row.get()));
            doAnswer(
                            invocation -> {
                                // 상대 요청이 선점 → 내부 트랜잭션만 롤백되고 중복 예외가 올라온다
                                row.set(createdByOther);
                                throw new DataIntegrityViolationException("duplicate key");
                            })
                    .when(userStatsCreator)
                    .create(TEST_USER_ID);

            // when
            UserStats result = userStatsService.getOrCreateUserStats(TEST_USER_ID);

            // then — 예외가 호출자로 전파되지 않고(= API 500 아님) 상대가 만든 행을 쓴다
            assertThat(result).isSameAs(createdByOther);
        }

        @Test
        @DisplayName("LUT-538: 생성 후에도 행을 찾을 수 없으면 IllegalStateException")
        void getOrCreateUserStats_missingAfterCreate_throws() {
            // given
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userStatsService.getOrCreateUserStats(TEST_USER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("getUserStats 테스트")
    class GetUserStatsTest {

        @Test
        @DisplayName("사용자 통계를 조회한다")
        void getUserStats_success() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            UserStatsResponse result = userStatsService.getUserStats(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("recordMissionCompletion 테스트")
    class RecordMissionCompletionTest {

        @Test
        @DisplayName("미션 완료를 기록한다")
        void recordMissionCompletion_success() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            userStatsService.recordMissionCompletion(TEST_USER_ID, false);

            // then
            assertThat(stats.getTotalMissionCompletions()).isEqualTo(11);
        }

        @Test
        @DisplayName("길드 미션 완료를 기록한다")
        void recordMissionCompletion_guildMission() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            userStatsService.recordMissionCompletion(TEST_USER_ID, true);

            // then
            assertThat(stats.getTotalMissionCompletions()).isEqualTo(11);
            assertThat(stats.getTotalGuildMissionCompletions()).isEqualTo(1);
        }

        // LUT-405: 서버 UTC 날짜(LocalDate.now())를 쓰면 KST 00~09시 미션 완료가 어제
        // 날짜로 들어가 streak 을 리셋시킨다 — 출석과 같은 유저 타임존 날짜를 써야 한다.
        @Test
        @DisplayName("LUT-405: streak 갱신은 유저 preferred_timezone 날짜를 사용한다")
        void recordMissionCompletion_usesUserTimezoneDate() {
            // given
            ZoneId userZone = ZoneId.of("Pacific/Auckland");
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);
            stats.setLastActivityDate(LocalDate.now(userZone).minusDays(1));

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));
            when(userQueryFacade.getPreferredTimezone(TEST_USER_ID)).thenReturn("Pacific/Auckland");

            // when
            userStatsService.recordMissionCompletion(TEST_USER_ID, false);

            // then — 유저 존 기준 어제 → 오늘이므로 연속 증가
            assertThat(stats.getCurrentStreak()).isEqualTo(6);
            assertThat(stats.getLastActivityDate()).isEqualTo(LocalDate.now(userZone));
        }

        @Test
        @DisplayName("LUT-405: 타임존 조회 실패 시 Asia/Seoul 로 폴백한다")
        void recordMissionCompletion_timezoneLookupFails_fallsBackToSeoul() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));
            when(userQueryFacade.getPreferredTimezone(TEST_USER_ID))
                    .thenThrow(new RuntimeException("user-db unavailable"));

            // when
            userStatsService.recordMissionCompletion(TEST_USER_ID, false);

            // then — 폴백 존 기준 오늘 날짜로 기록
            assertThat(stats.getLastActivityDate())
                    .isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")));
        }
    }

    @Nested
    @DisplayName("recordMissionFullCompletion 테스트")
    class RecordMissionFullCompletionTest {

        @Test
        @DisplayName("미션 전체 완료를 기록한다")
        void recordMissionFullCompletion_success() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            userStatsService.recordMissionFullCompletion(TEST_USER_ID, 30);

            // then
            assertThat(stats.getTotalMissionFullCompletions()).isEqualTo(6);
            assertThat(stats.getMaxCompletedMissionDuration()).isEqualTo(30);
        }

        @Test
        @DisplayName("더 긴 기간의 미션을 완주하면 maxCompletedMissionDuration이 업데이트된다")
        void recordMissionFullCompletion_updatesMaxDuration() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);
            // 기존 maxCompletedMissionDuration은 0

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when - 7일 미션 완주
            userStatsService.recordMissionFullCompletion(TEST_USER_ID, 7);

            // then
            assertThat(stats.getMaxCompletedMissionDuration()).isEqualTo(7);

            // when - 30일 미션 완주
            userStatsService.recordMissionFullCompletion(TEST_USER_ID, 30);

            // then - 더 긴 기간으로 업데이트
            assertThat(stats.getMaxCompletedMissionDuration()).isEqualTo(30);
        }

        @Test
        @DisplayName("더 짧은 기간의 미션을 완주해도 maxCompletedMissionDuration은 유지된다")
        void recordMissionFullCompletion_keepsMaxDuration() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when - 30일 미션 완주 후 7일 미션 완주
            userStatsService.recordMissionFullCompletion(TEST_USER_ID, 30);
            userStatsService.recordMissionFullCompletion(TEST_USER_ID, 7);

            // then - 30일이 유지됨
            assertThat(stats.getMaxCompletedMissionDuration()).isEqualTo(30);
            // totalMissionFullCompletions는 2번 증가
            assertThat(stats.getTotalMissionFullCompletions()).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("recordAchievementCompleted 테스트")
    class RecordAchievementCompletedTest {

        @Test
        @DisplayName("업적 완료를 기록한다")
        void recordAchievementCompleted_success() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            userStatsService.recordAchievementCompleted(TEST_USER_ID);

            // then
            assertThat(stats.getTotalAchievementsCompleted()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("recordTitleAcquired 테스트")
    class RecordTitleAcquiredTest {

        @Test
        @DisplayName("칭호 획득을 기록한다")
        void recordTitleAcquired_success() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            userStatsService.recordTitleAcquired(TEST_USER_ID);

            // then
            assertThat(stats.getTotalTitlesAcquired()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("getCurrentStreak 테스트")
    class GetCurrentStreakTest {

        @Test
        @DisplayName("현재 연속 일수를 반환한다")
        void getCurrentStreak_success() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 7);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            int result = userStatsService.getCurrentStreak(TEST_USER_ID);

            // then
            assertThat(result).isEqualTo(7);
        }

        @Test
        @DisplayName("통계가 없으면 0을 반환한다")
        void getCurrentStreak_noStats() {
            // given
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

            // when
            int result = userStatsService.getCurrentStreak(TEST_USER_ID);

            // then
            assertThat(result).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("incrementLikesReceived 테스트")
    class IncrementLikesReceivedTest {

        @Test
        @DisplayName("좋아요 카운터를 증가시킨다")
        void incrementLikesReceived_success() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            userStatsService.incrementLikesReceived(TEST_USER_ID);

            // then
            assertThat(stats.getTotalLikesReceived()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("decrementLikesReceived 테스트")
    class DecrementLikesReceivedTest {

        @Test
        @DisplayName("좋아요 카운터를 감소시킨다")
        void decrementLikesReceived_success() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);
            stats.setTotalLikesReceived(5L);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            userStatsService.decrementLikesReceived(TEST_USER_ID);

            // then
            assertThat(stats.getTotalLikesReceived()).isEqualTo(4L);
        }

        @Test
        @DisplayName("좋아요 카운터가 0이면 감소하지 않는다")
        void decrementLikesReceived_zeroDoesNotGoNegative() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            userStatsService.decrementLikesReceived(TEST_USER_ID);

            // then
            assertThat(stats.getTotalLikesReceived()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("syncGuildJoinCount 테스트 (LUT-418)")
    class SyncGuildJoinCountTest {

        @Test
        @DisplayName("길드 가입 카운터를 가입해 본 distinct 길드 수로 덮어쓴다")
        void syncGuildJoinCount_setsDistinctCount() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));
            when(guildQueryFacade.countDistinctJoinedGuilds(TEST_USER_ID)).thenReturn(3L);

            // when
            userStatsService.syncGuildJoinCount(TEST_USER_ID);

            // then
            assertThat(stats.getGuildJoinCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("같은 길드 재가입으로 부풀려진 카운터가 실제 distinct 길드 수로 정정된다")
        void syncGuildJoinCount_correctsInflatedCount() {
            // given - 탈퇴/재가입 반복으로 5까지 부풀려졌지만 실제 가입해 본 길드는 1개
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);
            stats.setGuildJoinCount(5);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));
            when(guildQueryFacade.countDistinctJoinedGuilds(TEST_USER_ID)).thenReturn(1L);

            // when
            userStatsService.syncGuildJoinCount(TEST_USER_ID);

            // then
            assertThat(stats.getGuildJoinCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("반복 호출해도 결과가 같다 (멱등)")
        void syncGuildJoinCount_idempotent() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));
            when(guildQueryFacade.countDistinctJoinedGuilds(TEST_USER_ID)).thenReturn(2L);

            // when
            userStatsService.syncGuildJoinCount(TEST_USER_ID);
            userStatsService.syncGuildJoinCount(TEST_USER_ID);
            userStatsService.syncGuildJoinCount(TEST_USER_ID);

            // then
            assertThat(stats.getGuildJoinCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("incrementFriendCount 테스트")
    class IncrementFriendCountTest {

        @Test
        @DisplayName("친구 카운터를 증가시킨다")
        void incrementFriendCount_success() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            userStatsService.incrementFriendCount(TEST_USER_ID);

            // then
            assertThat(stats.getFriendCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("decrementFriendCount 테스트")
    class DecrementFriendCountTest {

        @Test
        @DisplayName("친구 카운터를 감소시킨다")
        void decrementFriendCount_success() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);
            stats.setFriendCount(3);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            userStatsService.decrementFriendCount(TEST_USER_ID);

            // then
            assertThat(stats.getFriendCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("친구 카운터가 0이면 감소하지 않는다")
        void decrementFriendCount_zeroDoesNotGoNegative() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 5);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            userStatsService.decrementFriendCount(TEST_USER_ID);

            // then
            assertThat(stats.getFriendCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getMaxStreak 테스트")
    class GetMaxStreakTest {

        @Test
        @DisplayName("최대 연속 일수를 반환한다")
        void getMaxStreak_success() {
            // given
            UserStats stats = createTestUserStats(1L, TEST_USER_ID, 10, 7);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            int result = userStatsService.getMaxStreak(TEST_USER_ID);

            // then
            assertThat(result).isEqualTo(7);
        }

        @Test
        @DisplayName("통계가 없으면 0을 반환한다")
        void getMaxStreak_noStats() {
            // given
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

            // when
            int result = userStatsService.getMaxStreak(TEST_USER_ID);

            // then
            assertThat(result).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("undoMissionCompletion 테스트")
    class UndoMissionCompletionTest {

        @Test
        @DisplayName("길드 미션 완료 취소 시 전체·길드 미션 카운터를 모두 감소시킨다")
        void undoMissionCompletion_guildMission_decrementsBoth() {
            // given
            UserStats stats =
                    UserStats.builder()
                            .userId(TEST_USER_ID)
                            .totalMissionCompletions(5)
                            .totalGuildMissionCompletions(2)
                            .build();
            setId(stats, 1L);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            userStatsService.undoMissionCompletion(TEST_USER_ID, true);

            // then
            assertThat(stats.getTotalMissionCompletions()).isEqualTo(4);
            assertThat(stats.getTotalGuildMissionCompletions()).isEqualTo(1);
        }

        @Test
        @DisplayName("일반 미션 완료 취소 시 길드 미션 카운터는 건드리지 않는다")
        void undoMissionCompletion_regularMission_keepsGuildCount() {
            // given
            UserStats stats =
                    UserStats.builder()
                            .userId(TEST_USER_ID)
                            .totalMissionCompletions(5)
                            .totalGuildMissionCompletions(2)
                            .build();
            setId(stats, 1L);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            userStatsService.undoMissionCompletion(TEST_USER_ID, false);

            // then
            assertThat(stats.getTotalMissionCompletions()).isEqualTo(4);
            assertThat(stats.getTotalGuildMissionCompletions()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("calculateRankingPercentile 테스트")
    class CalculateRankingPercentileTest {

        @Test
        @DisplayName("전체 유저가 0명이면 100.0 을 반환한다")
        void calculateRankingPercentile_noUsers_returns100() {
            // given
            when(userStatsRepository.countTotalUsers()).thenReturn(0L);

            // when
            Double result = userStatsService.calculateRankingPercentile(500L);

            // then
            assertThat(result).isEqualTo(100.0);
            verify(userStatsRepository, org.mockito.Mockito.never())
                    .calculateRank(org.mockito.ArgumentMatchers.anyLong());
        }

        @Test
        @DisplayName("순위/전체 비율을 소수점 첫째 자리까지 백분율로 계산한다")
        void calculateRankingPercentile_computesRatio() {
            // given
            when(userStatsRepository.countTotalUsers()).thenReturn(8L);
            when(userStatsRepository.calculateRank(500L)).thenReturn(3L);

            // when
            Double result = userStatsService.calculateRankingPercentile(500L);

            // then
            assertThat(result).isEqualTo(37.5);
        }
    }
}
