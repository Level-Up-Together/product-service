package io.pinkspider.leveluptogethermvp.bffservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.bffservice.api.dto.MissionTodayDataResponse;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionExecutionQueryService;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BffMissionServiceTest {

    @Mock
    private MissionService missionService;

    @Mock
    private MissionExecutionQueryService missionExecutionQueryService;

    private BffMissionService bffMissionService;

    // 테스트용 동기 Executor - CompletableFuture가 즉시 실행되도록 함
    private final java.util.concurrent.Executor directExecutor = Runnable::run;

    @BeforeEach
    void setUpService() {
        // BffMissionService 수동 생성 (Executor 주입을 위해)
        bffMissionService =
                new BffMissionService(missionService, missionExecutionQueryService, directExecutor);
    }

    private static final String TEST_USER_ID = "test-user-123";

    private MissionResponse createTestMissionResponse(Long id) {
        return MissionResponse.builder()
            .id(id)
            .title("테스트 미션 " + id)
            .description("테스트 미션 설명")
            .build();
    }

    private MissionExecutionResponse createTestExecutionResponse(Long id, ExecutionStatus status) {
        return MissionExecutionResponse.builder()
            .id(id)
            .status(status)
            .build();
    }

    @Nested
    @DisplayName("getTodayMissions 테스트")
    class GetTodayMissionsTest {

        @Test
        @DisplayName("locale 지정 시 하위 서비스들에 locale을 전달한다 (LUT-255)")
        void getTodayMissions_passesLocaleToSubServices() {
            // given
            when(missionService.getMyMissions(TEST_USER_ID, "en")).thenReturn(List.of());
            when(missionExecutionQueryService.getTodayExecutions(TEST_USER_ID, "en"))
                .thenReturn(List.of());
            when(missionExecutionQueryService.getCompletedPinnedInstancesForToday(TEST_USER_ID, "en"))
                .thenReturn(List.of());

            // when
            bffMissionService.getTodayMissions(TEST_USER_ID, "en");

            // then
            org.mockito.Mockito.verify(missionService).getMyMissions(TEST_USER_ID, "en");
            org.mockito.Mockito.verify(missionExecutionQueryService).getTodayExecutions(TEST_USER_ID, "en");
            org.mockito.Mockito.verify(missionExecutionQueryService)
                .getCompletedPinnedInstancesForToday(TEST_USER_ID, "en");
        }

        @Test
        @DisplayName("오늘의 미션 데이터를 성공적으로 조회한다")
        void getTodayMissions_success() {
            // given
            List<MissionResponse> myMissions = List.of(
                createTestMissionResponse(1L),
                createTestMissionResponse(2L)
            );

            List<MissionExecutionResponse> todayExecutions = List.of(
                createTestExecutionResponse(1L, ExecutionStatus.COMPLETED),
                createTestExecutionResponse(2L, ExecutionStatus.IN_PROGRESS),
                createTestExecutionResponse(3L, ExecutionStatus.PENDING)
            );

            when(missionService.getMyMissions(TEST_USER_ID, null)).thenReturn(myMissions);
            when(missionExecutionQueryService.getTodayExecutions(TEST_USER_ID, null)).thenReturn(todayExecutions);

            // when
            MissionTodayDataResponse result = bffMissionService.getTodayMissions(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getMyMissions()).hasSize(2);
            assertThat(result.getTodayExecutions()).hasSize(3);
            assertThat(result.getCompletedCount()).isEqualTo(1);
            assertThat(result.getInProgressCount()).isEqualTo(1);
            assertThat(result.getPendingCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("미션 서비스 예외 발생 시 빈 리스트 반환")
        void getTodayMissions_missionServiceException_returnsEmptyList() {
            // given
            when(missionService.getMyMissions(TEST_USER_ID, null))
                .thenThrow(new RuntimeException("미션 서비스 오류"));
            when(missionExecutionQueryService.getTodayExecutions(TEST_USER_ID, null))
                .thenReturn(Collections.emptyList());

            // when
            MissionTodayDataResponse result = bffMissionService.getTodayMissions(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getMyMissions()).isEmpty();
            assertThat(result.getTodayExecutions()).isEmpty();
        }

        @Test
        @DisplayName("실행 서비스 예외 발생 시 빈 리스트 반환")
        void getTodayMissions_executionServiceException_returnsEmptyList() {
            // given
            when(missionService.getMyMissions(TEST_USER_ID, null))
                .thenReturn(Collections.emptyList());
            when(missionExecutionQueryService.getTodayExecutions(TEST_USER_ID, null))
                .thenThrow(new RuntimeException("실행 서비스 오류"));

            // when
            MissionTodayDataResponse result = bffMissionService.getTodayMissions(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getMyMissions()).isEmpty();
            assertThat(result.getTodayExecutions()).isEmpty();
        }

        @Test
        @DisplayName("미션이 없는 경우 통계가 모두 0")
        void getTodayMissions_noMissions_zeroStats() {
            // given
            when(missionService.getMyMissions(TEST_USER_ID, null)).thenReturn(Collections.emptyList());
            when(missionExecutionQueryService.getTodayExecutions(TEST_USER_ID, null)).thenReturn(Collections.emptyList());

            // when
            MissionTodayDataResponse result = bffMissionService.getTodayMissions(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getMyMissions()).isEmpty();
            assertThat(result.getTodayExecutions()).isEmpty();
            assertThat(result.getCompletedCount()).isEqualTo(0);
            assertThat(result.getInProgressCount()).isEqualTo(0);
            assertThat(result.getPendingCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("모든 실행이 완료된 경우")
        void getTodayMissions_allCompleted() {
            // given
            List<MissionResponse> myMissions = List.of(createTestMissionResponse(1L));

            List<MissionExecutionResponse> todayExecutions = List.of(
                createTestExecutionResponse(1L, ExecutionStatus.COMPLETED),
                createTestExecutionResponse(2L, ExecutionStatus.COMPLETED),
                createTestExecutionResponse(3L, ExecutionStatus.COMPLETED)
            );

            when(missionService.getMyMissions(TEST_USER_ID, null)).thenReturn(myMissions);
            when(missionExecutionQueryService.getTodayExecutions(TEST_USER_ID, null)).thenReturn(todayExecutions);

            // when
            MissionTodayDataResponse result = bffMissionService.getTodayMissions(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getCompletedCount()).isEqualTo(3);
            assertThat(result.getInProgressCount()).isEqualTo(0);
            assertThat(result.getPendingCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("모든 실행이 진행 중인 경우")
        void getTodayMissions_allInProgress() {
            // given
            List<MissionResponse> myMissions = List.of(createTestMissionResponse(1L));

            List<MissionExecutionResponse> todayExecutions = List.of(
                createTestExecutionResponse(1L, ExecutionStatus.IN_PROGRESS),
                createTestExecutionResponse(2L, ExecutionStatus.IN_PROGRESS)
            );

            when(missionService.getMyMissions(TEST_USER_ID, null)).thenReturn(myMissions);
            when(missionExecutionQueryService.getTodayExecutions(TEST_USER_ID, null)).thenReturn(todayExecutions);

            // when
            MissionTodayDataResponse result = bffMissionService.getTodayMissions(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getCompletedCount()).isEqualTo(0);
            assertThat(result.getInProgressCount()).isEqualTo(2);
            assertThat(result.getPendingCount()).isEqualTo(0);
        }
    }
}
