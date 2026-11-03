package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import static io.pinkspider.global.test.TestReflectionUtils.setField;
import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.application.DailyMissionInstanceCreator;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * LUT-539: 다음 인스턴스 생성 스텝 — insert 는 {@link DailyMissionInstanceCreator}(REQUIRES_NEW)에 맡기고,
 * sequence 충돌 시 이 스텝의 컨텍스트는 깨끗하게 남아 재조회·재시도가 실제로 동작해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class CreateNextPinnedInstanceStepTest {

    @Mock private DailyMissionInstanceRepository instanceRepository;
    @Mock private DailyMissionInstanceCreator instanceCreator;

    @InjectMocks private CreateNextPinnedInstanceStep step;

    private Mission mission;
    private MissionParticipant participant;
    private MissionCompletionContext context;
    private final LocalDate date = LocalDate.of(2026, 10, 13);

    @BeforeEach
    void setUp() {
        mission =
                Mission.builder()
                        .title("매일 운동")
                        .creatorId("user-1")
                        .status(MissionStatus.IN_PROGRESS)
                        .visibility(MissionVisibility.PRIVATE)
                        .type(MissionType.PERSONAL)
                        .isPinned(true)
                        .build();
        setId(mission, 1L);
        participant =
                MissionParticipant.builder()
                        .mission(mission)
                        .userId("user-1")
                        .status(ParticipantStatus.IN_PROGRESS)
                        .progress(0)
                        .build();
        setId(participant, 7L);

        context = MissionCompletionContext.forPinned(10L, "user-1", null, false);
        context.setParticipant(participant);
        context.setMission(mission);
        setField(context, "instanceDate", date);
    }

    @Test
    @DisplayName("고정 미션에만 실행된다")
    void shouldExecute_onlyPinned() {
        assertThat(step.shouldExecute().test(context)).isTrue();
        assertThat(step.getName()).isEqualTo("CreateNextPinnedInstance");
    }

    @Test
    @DisplayName("max sequence + 1 로 크리에이터에 생성을 맡기고 생성 id 를 컨텍스트에 기록한다")
    void execute_createsNextSequenceViaCreator() {
        when(instanceRepository.findMaxSequenceNumber(7L, date)).thenReturn(1);
        when(instanceCreator.create(7L, date, 2)).thenReturn(99L);

        SagaStepResult result = step.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(context.getNextInstanceId()).isEqualTo(99L);
        verify(instanceCreator).create(7L, date, 2);
    }

    @Test
    @DisplayName("sequence 충돌(내부 트랜잭션 롤백) 시 max sequence 를 재조회해 다음 번호로 재시도한다")
    void execute_sequenceCollision_retriesWithFreshSequence() {
        when(instanceRepository.findMaxSequenceNumber(7L, date)).thenReturn(1, 2);
        when(instanceCreator.create(7L, date, 2))
                .thenThrow(new DataIntegrityViolationException("uk sequence"));
        when(instanceCreator.create(7L, date, 3)).thenReturn(100L);

        SagaStepResult result = step.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(context.getNextInstanceId()).isEqualTo(100L);
        verify(instanceCreator).create(7L, date, 2);
        verify(instanceCreator).create(7L, date, 3);
    }

    @Test
    @DisplayName("3회 모두 충돌하면 실패 결과를 돌려준다 (예외는 스텝 밖으로 새지 않음)")
    void execute_collisionsExhausted_returnsFailure() {
        when(instanceRepository.findMaxSequenceNumber(7L, date)).thenReturn(1);
        when(instanceCreator.create(eq(7L), eq(date), anyInt()))
                .thenThrow(new DataIntegrityViolationException("uk sequence"));

        SagaStepResult result = step.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(context.getNextInstanceId()).isNull();
        verify(instanceCreator, times(3)).create(eq(7L), eq(date), anyInt());
    }

    @Test
    @DisplayName("일일 수행 제한에 도달했으면 생성을 건너뛴다")
    void execute_dailyLimitReached_skips() {
        mission.setDailyExecutionLimit(2);
        when(instanceRepository.countCompletedByParticipantIdAndDate(7L, date)).thenReturn(2L);

        SagaStepResult result = step.execute(context);

        assertThat(result.isSuccess()).isTrue();
        verify(instanceCreator, never()).create(anyLong(), eq(date), anyInt());
    }

    @Test
    @DisplayName("보상: 생성된 다음 인스턴스를 삭제한다 / 생성 전이면 no-op")
    void compensate_deletesCreatedInstance() {
        context.setNextInstanceId(99L);
        assertThat(step.compensate(context).isSuccess()).isTrue();
        verify(instanceRepository).deleteById(99L);

        context.setNextInstanceId(null);
        assertThat(step.compensate(context).isSuccess()).isTrue();
    }
}
