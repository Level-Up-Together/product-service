package io.pinkspider.leveluptogethermvp.gamificationservice.season.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.season.application.SeasonRewardProcessorService;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRewardHistoryRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

/**
 * SeasonScheduledTaskManager 단위 테스트.
 *
 * <p>특히 {@code end_at}이 UTC LocalDateTime으로 저장되어 있다는 가정 하에
 * 정확한 Instant로 변환하는지 회귀 방지 검증한다 (QA-103 후속 수정).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SeasonScheduledTaskManager 단위 테스트")
class SeasonScheduledTaskManagerTest {

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private SeasonRewardHistoryRepository rewardHistoryRepository;

    @Mock
    private SeasonRewardProcessorService rewardProcessorService;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    @InjectMocks
    private SeasonScheduledTaskManager manager;

    @BeforeEach
    void setUp() throws Exception {
        // private final TaskScheduler를 mock으로 교체
        replaceField(manager, "taskScheduler", taskScheduler);
        // scheduledTasks Map 초기화 (혹시 이전 테스트 잔여 방지)
        replaceField(manager, "scheduledTasks", new ConcurrentHashMap<Long, ScheduledFuture<?>>());
    }

    @Nested
    @DisplayName("schedule 테스트")
    class ScheduleTest {

        @Test
        @DisplayName("end_at(UTC LocalDateTime)을 UTC offset으로 변환하여 정확한 Instant에 등록한다 (QA-103 회귀 방지)")
        void schedule_convertsEndAtAsUtc() {
            // given: 충분히 먼 미래 (현재 시각 영향 배제)
            LocalDateTime endAtUtc = LocalDateTime.of(2099, 4, 27, 23, 30, 0);
            Season season = buildSeason(8L, endAtUtc, true);

            when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenAnswer(inv -> scheduledFuture);

            // when
            manager.schedule(season);

            // then: KST가 아닌 UTC 기준 Instant에 등록되어야 함
            ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(taskScheduler).schedule(any(Runnable.class), instantCaptor.capture());

            Instant expectedUtcInstant = endAtUtc.toInstant(ZoneOffset.UTC);
            assertThat(instantCaptor.getValue()).isEqualTo(expectedUtcInstant);

            // KST 기반 (틀린 변환) 결과와는 달라야 함 — 9시간 차이
            Instant wrongKstInstant = endAtUtc.atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant();
            assertThat(instantCaptor.getValue()).isNotEqualTo(wrongKstInstant);
            assertThat(java.time.Duration.between(instantCaptor.getValue(), wrongKstInstant).abs())
                .isEqualTo(java.time.Duration.ofHours(9));
        }

        @Test
        @DisplayName("이미 종료된 시즌은 즉시 처리(triggerImmediately)된다")
        void schedule_pastSeason_triggersImmediately() {
            // given: 1시간 전에 종료된 시즌
            LocalDateTime pastEndAt = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
            Season season = buildSeason(8L, pastEndAt, true);
            when(rewardHistoryRepository.existsBySeasonId(8L)).thenReturn(false);
            when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenAnswer(inv -> scheduledFuture);

            // when
            manager.schedule(season);

            // then: 미래 시점이 아닌 Instant.now() 직후로 즉시 처리 호출됨
            ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(taskScheduler).schedule(any(Runnable.class), instantCaptor.capture());

            Instant scheduled = instantCaptor.getValue();
            // 즉시 실행이므로 현재 시각과 가까워야 함 (5초 이내)
            assertThat(scheduled).isCloseTo(Instant.now(), within(5_000));
        }

        @Test
        @DisplayName("이미 종료됐으나 보상 이력이 있으면 아무것도 하지 않는다")
        void schedule_pastSeasonWithRewards_doesNothing() {
            // given
            LocalDateTime pastEndAt = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
            Season season = buildSeason(8L, pastEndAt, true);
            when(rewardHistoryRepository.existsBySeasonId(8L)).thenReturn(true);

            // when
            manager.schedule(season);

            // then
            verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        }

        @Test
        @DisplayName("isActive=false 시즌은 스케줄링하지 않는다")
        void schedule_inactiveSeason_skips() {
            // given
            LocalDateTime endAt = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
            Season season = buildSeason(8L, endAt, false);

            // when
            manager.schedule(season);

            // then
            verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        }

        @Test
        @DisplayName("end_at이 null인 시즌은 스케줄링하지 않는다")
        void schedule_nullEndAt_skips() {
            // given
            Season season = buildSeason(8L, null, true);

            // when
            manager.schedule(season);

            // then
            verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        }

        @Test
        @DisplayName("같은 시즌을 다시 schedule하면 이전 작업이 취소된다")
        void schedule_sameSeasonTwice_cancelsPrevious() {
            // given
            LocalDateTime endAt = LocalDateTime.of(2027, 1, 1, 0, 0);
            Season season = buildSeason(8L, endAt, true);
            when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenAnswer(inv -> scheduledFuture);
            when(scheduledFuture.isDone()).thenReturn(false);

            // when: 두 번 등록
            manager.schedule(season);
            manager.schedule(season);

            // then: 두 번째 호출 시 cancel 호출됨
            verify(scheduledFuture).cancel(false);
            verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
        }
    }

    @Nested
    @DisplayName("cancel 테스트")
    class CancelTest {

        @Test
        @DisplayName("등록된 작업을 취소한다")
        void cancel_existingTask() {
            // given
            LocalDateTime endAt = LocalDateTime.of(2027, 1, 1, 0, 0);
            Season season = buildSeason(8L, endAt, true);
            when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenAnswer(inv -> scheduledFuture);
            when(scheduledFuture.isDone()).thenReturn(false);
            manager.schedule(season);

            // when
            manager.cancel(8L);

            // then
            verify(scheduledFuture).cancel(false);
        }

        @Test
        @DisplayName("등록되지 않은 시즌 ID에 대해 cancel 호출 시 예외 없음")
        void cancel_nonExistent_noException() {
            manager.cancel(999L);
            // no verify needed — should not throw
        }
    }

    @Nested
    @DisplayName("triggerImmediately 테스트")
    class TriggerImmediatelyTest {

        @Test
        @DisplayName("Instant.now() 시점에 보상 처리 등록")
        void triggerImmediately_schedulesAtNow() {
            // when
            manager.triggerImmediately(8L, "수동 트리거");

            // then
            ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
            verify(taskScheduler).schedule(any(Runnable.class), captor.capture());
            assertThat(captor.getValue()).isCloseTo(Instant.now(), within(5_000));
        }
    }

    // === Helpers ===

    private Season buildSeason(Long id, LocalDateTime endAt, boolean isActive) {
        Season season = Season.builder()
            .title("Test Season")
            .startAt(endAt != null ? endAt.minusDays(7) : null)
            .endAt(endAt)
            .isActive(isActive)
            .build();
        try {
            Field idField = Season.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(season, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set id on Season", e);
        }
        return season;
    }

    private static void replaceField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * AssertJ Instant.isCloseTo helper — 임의 millis 허용 범위.
     */
    private static org.assertj.core.data.TemporalUnitOffset within(long millis) {
        return new org.assertj.core.data.TemporalUnitWithinOffset(millis, java.time.temporal.ChronoUnit.MILLIS);
    }
}
