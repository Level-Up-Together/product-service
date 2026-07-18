package io.pinkspider.leveluptogethermvp.missionservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.GuildExpBackfillResultResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuildExpBackfillService 테스트 (LUT-236)")
class GuildExpBackfillServiceTest {

    @Mock
    private MissionExecutionRepository executionRepository;

    @Mock
    private DailyMissionInstanceRepository instanceRepository;

    @Mock
    private GuildExpBackfillExecutor executor;

    @InjectMocks
    private GuildExpBackfillService service;

    private MissionExecution execWithId(long id) {
        MissionExecution execution = MissionExecution.builder().build();
        setId(execution, id);
        return execution;
    }

    private DailyMissionInstance instanceWithId(long id) {
        DailyMissionInstance instance = DailyMissionInstance.builder().build();
        setId(instance, id);
        return instance;
    }

    @Test
    @DisplayName("keyset 으로 배치 순회하며 건별 지급하고, 대상 소진 시 종료한다")
    void iteratesBatchesAndAggregates() {
        // 첫 배치(id>0): 2건, 둘째 배치(id>2): 0건 → 종료
        when(executionRepository.findAutoCompletedGuildExecutionsNeedingGuildExp(eq(0L), any(Pageable.class)))
            .thenReturn(List.of(execWithId(1L), execWithId(2L)));
        when(executionRepository.findAutoCompletedGuildExecutionsNeedingGuildExp(eq(2L), any(Pageable.class)))
            .thenReturn(List.of());
        when(executor.grantForExecution(1L)).thenReturn(120);
        when(executor.grantForExecution(2L)).thenReturn(0); // 지급 없음(예: 이미 처리)

        GuildExpBackfillResultResponse result = service.backfillAutoCompletedGuildExp();

        assertThat(result.executionsScanned()).isEqualTo(2);
        assertThat(result.guildExpGranted()).isEqualTo(1);
        assertThat(result.totalExpGranted()).isEqualTo(120);
        assertThat(result.failed()).isEqualTo(0);
    }

    @Test
    @DisplayName("고정 미션 인스턴스 누락분도 함께 소급하고 합산한다")
    void backfillsInstancesToo() {
        // 일반 미션 1건(120) + 고정 미션 인스턴스 2건(120, 0)
        when(executionRepository.findAutoCompletedGuildExecutionsNeedingGuildExp(eq(0L), any(Pageable.class)))
            .thenReturn(List.of(execWithId(1L)));
        when(executionRepository.findAutoCompletedGuildExecutionsNeedingGuildExp(eq(1L), any(Pageable.class)))
            .thenReturn(List.of());
        when(executor.grantForExecution(1L)).thenReturn(120);

        when(instanceRepository.findAutoCompletedGuildInstancesNeedingGuildExp(eq(0L), any(Pageable.class)))
            .thenReturn(List.of(instanceWithId(3978L), instanceWithId(3764L)));
        when(instanceRepository.findAutoCompletedGuildInstancesNeedingGuildExp(eq(3764L), any(Pageable.class)))
            .thenReturn(List.of());
        when(executor.grantForInstance(3978L)).thenReturn(120);
        when(executor.grantForInstance(3764L)).thenReturn(120);

        GuildExpBackfillResultResponse result = service.backfillAutoCompletedGuildExp();

        assertThat(result.executionsScanned()).isEqualTo(3); // 1 execution + 2 instances
        assertThat(result.guildExpGranted()).isEqualTo(3);
        assertThat(result.totalExpGranted()).isEqualTo(360);
        assertThat(result.failed()).isEqualTo(0);
    }

    @Test
    @DisplayName("건별 예외는 failed로 집계되고 keyset이 전진해 무한 루프에 빠지지 않는다")
    void perExecutionFailureIsSkipped() {
        when(executionRepository.findAutoCompletedGuildExecutionsNeedingGuildExp(eq(0L), any(Pageable.class)))
            .thenReturn(List.of(execWithId(5L)));
        when(executionRepository.findAutoCompletedGuildExecutionsNeedingGuildExp(eq(5L), any(Pageable.class)))
            .thenReturn(List.of());
        when(executor.grantForExecution(5L)).thenThrow(new RuntimeException("guild down"));

        GuildExpBackfillResultResponse result = service.backfillAutoCompletedGuildExp();

        assertThat(result.executionsScanned()).isEqualTo(1);
        assertThat(result.guildExpGranted()).isEqualTo(0);
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    @DisplayName("대상이 없으면 아무것도 지급하지 않는다")
    void noTargets() {
        when(executionRepository.findAutoCompletedGuildExecutionsNeedingGuildExp(eq(0L), any(Pageable.class)))
            .thenReturn(List.of());

        GuildExpBackfillResultResponse result = service.backfillAutoCompletedGuildExp();

        assertThat(result.executionsScanned()).isEqualTo(0);
        assertThat(result.totalExpGranted()).isEqualTo(0);
    }
}
