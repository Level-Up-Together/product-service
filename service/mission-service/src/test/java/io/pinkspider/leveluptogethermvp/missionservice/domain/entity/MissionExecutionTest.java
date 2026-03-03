package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MissionExecution 엔티티 테스트")
class MissionExecutionTest {

    private static final String TEST_USER_ID = "test-user-123";

    private Mission mission;
    private MissionParticipant participant;

    @BeforeEach
    void setUp() {
        mission = Mission.builder()
            .title("30분 독서")
            .description("매일 30분 독서하기")
            .creatorId(TEST_USER_ID)
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PRIVATE)
            .type(MissionType.PERSONAL)
            .categoryId(1L)
            .categoryName("독서")
            .expPerCompletion(30)
            .build();
        setId(mission, 1L);

        participant = MissionParticipant.builder()
            .mission(mission)
            .userId(TEST_USER_ID)
            .status(ParticipantStatus.ACCEPTED)
            .build();
        setId(participant, 1L);
    }

    private MissionExecution createExecution(LocalDate date) {
        MissionExecution execution = MissionExecution.builder()
            .participant(participant)
            .executionDate(date)
            .status(ExecutionStatus.PENDING)
            .build();
        setId(execution, 1L);
        return execution;
    }

    @Nested
    @DisplayName("autoCompleteForDateChange 메서드 테스트")
    class AutoCompleteForDateChangeTest {

        @Test
        @DisplayName("IN_PROGRESS 실행을 날짜 변경으로 자동 완료한다")
        void autoCompleteForDateChange_success() {
            // given
            MissionExecution execution = createExecution(LocalDate.now().minusDays(1));
            execution.start();
            TestReflectionUtils.setField(execution, "startedAt", LocalDateTime.now().minusMinutes(35));

            // when
            boolean result = execution.autoCompleteForDateChange();

            // then
            assertThat(result).isTrue();
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(execution.getCompletedAt()).isNotNull();
            assertThat(execution.getExpEarned()).isGreaterThanOrEqualTo(35);
            assertThat(execution.getIsAutoCompleted()).isTrue();
        }

        @Test
        @DisplayName("2시간 미만이어도 자동 완료된다")
        void autoCompleteForDateChange_lessThanTwoHours() {
            // given
            MissionExecution execution = createExecution(LocalDate.now().minusDays(1));
            execution.start();
            TestReflectionUtils.setField(execution, "startedAt", LocalDateTime.now().minusMinutes(10));

            // when
            boolean result = execution.autoCompleteForDateChange();

            // then
            assertThat(result).isTrue();
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(execution.getExpEarned()).isGreaterThanOrEqualTo(10);
        }

        @Test
        @DisplayName("PENDING 상태에서는 false를 반환한다")
        void autoCompleteForDateChange_pendingStatus_returnsFalse() {
            // given
            MissionExecution execution = createExecution(LocalDate.now());

            // when
            boolean result = execution.autoCompleteForDateChange();

            // then
            assertThat(result).isFalse();
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        }

        @Test
        @DisplayName("startedAt이 null이면 false를 반환한다")
        void autoCompleteForDateChange_noStartedAt_returnsFalse() {
            // given
            MissionExecution execution = createExecution(LocalDate.now());
            TestReflectionUtils.setField(execution, "status", ExecutionStatus.IN_PROGRESS);

            // when
            boolean result = execution.autoCompleteForDateChange();

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("23:30 시작 → 00:05 자동 완료 시나리오")
        void autoCompleteForDateChange_midnightScenario() {
            // given - 어제 23:30 시작
            MissionExecution execution = createExecution(LocalDate.now().minusDays(1));
            execution.start();
            // 어제 23:30에 시작한 것으로 설정 (약 35분 전)
            LocalDateTime startTime = LocalDate.now().atStartOfDay().minusMinutes(30);
            TestReflectionUtils.setField(execution, "startedAt", startTime);

            // when - 자정 스케줄러 실행 (00:05)
            boolean result = execution.autoCompleteForDateChange();

            // then
            assertThat(result).isTrue();
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(execution.getCompletedAt()).isNotNull();
            // 23:30 ~ now (약 30+분) → 최소 30 EXP
            assertThat(execution.getExpEarned()).isGreaterThanOrEqualTo(30);
            assertThat(execution.getIsAutoCompleted()).isTrue();
            // executionDate는 어제 날짜 유지 (캘린더에 어제로 표시)
            assertThat(execution.getExecutionDate()).isEqualTo(LocalDate.now().minusDays(1));
        }
    }
}
