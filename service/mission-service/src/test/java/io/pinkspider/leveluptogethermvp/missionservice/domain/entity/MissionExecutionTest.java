package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("MissionExecution 엔티티 테스트")
class MissionExecutionTest {

    private static final String TEST_USER_ID = "test-user-123";

    private Mission mission;
    private MissionParticipant participant;

    @BeforeEach
    void setUp() {
        mission =
                Mission.builder()
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

        participant =
                MissionParticipant.builder()
                        .mission(mission)
                        .userId(TEST_USER_ID)
                        .status(ParticipantStatus.ACCEPTED)
                        .build();
        setId(participant, 1L);
    }

    private MissionExecution createExecution(LocalDate date) {
        MissionExecution execution =
                MissionExecution.builder()
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
            TestReflectionUtils.setField(
                    execution, "startedAt", LocalDateTime.now().minusMinutes(35));

            // when
            boolean result = execution.autoCompleteForDateChange(10);

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
            TestReflectionUtils.setField(
                    execution, "startedAt", LocalDateTime.now().minusMinutes(10));

            // when
            boolean result = execution.autoCompleteForDateChange(10);

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
            boolean result = execution.autoCompleteForDateChange(10);

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
            boolean result = execution.autoCompleteForDateChange(10);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("2시간 이내 시작 → 자동 완료 시 분당 경험치 적용")
        void autoCompleteForDateChange_withinTwoHours() {
            // given - 30분 전 시작
            MissionExecution execution = createExecution(LocalDate.now().minusDays(1));
            execution.start();
            TestReflectionUtils.setField(
                    execution, "startedAt", LocalDateTime.now().minusMinutes(30));

            // when
            boolean result = execution.autoCompleteForDateChange(10);

            // then
            assertThat(result).isTrue();
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(execution.getCompletedAt()).isNotNull();
            assertThat(execution.getExpEarned()).isGreaterThanOrEqualTo(30);
            assertThat(execution.getIsAutoCompleted()).isTrue();
            assertThat(execution.getExecutionDate()).isEqualTo(LocalDate.now().minusDays(1));
        }

        @Test
        @DisplayName("4시간 초과 시작 → 자동 완료 시 기본 경험치 적용")
        void autoCompleteForDateChange_overFourHours_appliesBaseExp() {
            // given - 5시간 전 시작 (QA-212: 자동종료 임계 4시간 초과)
            MissionExecution execution = createExecution(LocalDate.now().minusDays(1));
            execution.start();
            TestReflectionUtils.setField(
                    execution, "startedAt", LocalDateTime.now().minusMinutes(300));

            // when
            boolean result = execution.autoCompleteForDateChange(10);

            // then
            assertThat(result).isTrue();
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(execution.getExpEarned()).isEqualTo(10); // 기본 경험치
            assertThat(execution.getIsAutoCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("revertCompletion 메서드 테스트 (LUT-860 완료한 수행 취소)")
    class RevertCompletionTest {

        private MissionExecution completedExecution() {
            MissionExecution execution = createExecution(LocalDate.now());
            execution.setStatus(ExecutionStatus.COMPLETED);
            execution.setStartedAt(LocalDateTime.now().minusMinutes(40));
            execution.setCompletedAt(LocalDateTime.now());
            execution.setExpEarned(40);
            execution.setNote("메모");
            execution.setImageUrl("https://cdn/a.jpg");
            execution.setIsSharedToFeed(true);
            execution.setIsAutoCompleted(true);
            execution.setGuildExpGranted(true);
            return execution;
        }

        @Test
        @DisplayName("완료한 수행을 대기 상태로 되돌리고 경험치·기록·표시를 모두 비운다")
        void revertCompletion_fromCompleted_clearsEverything() {
            // given
            MissionExecution execution = completedExecution();

            // when
            execution.revertCompletion();

            // then
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.PENDING);
            assertThat(execution.getStartedAt()).isNull();
            assertThat(execution.getCompletedAt()).isNull();
            assertThat(execution.getExpEarned()).isZero();
            assertThat(execution.getNote()).isNull();
            assertThat(execution.getImageUrl()).isNull();
            assertThat(execution.getIsSharedToFeed()).isFalse();
            assertThat(execution.getIsAutoCompleted()).isFalse();
            assertThat(execution.getGuildExpGranted()).isFalse();
            // 수행 날짜·참여는 그대로
            assertThat(execution.getExecutionDate()).isEqualTo(LocalDate.now());
            assertThat(execution.getParticipant()).isSameAs(participant);
        }

        @Test
        @DisplayName("되돌린 수행은 다시 시작할 수 있다")
        void revertCompletion_thenStartAgain() {
            // given
            MissionExecution execution = completedExecution();
            execution.revertCompletion();

            // when
            execution.start();

            // then
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
            assertThat(execution.getStartedAt()).isNotNull();
        }

        @ParameterizedTest(name = "{0} 상태에서는 취소할 수 없다")
        @EnumSource(
                value = ExecutionStatus.class,
                names = {"COMPLETED"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("완료 상태가 아니면 IllegalStateException 이고 값은 그대로다")
        void revertCompletion_notCompleted_throws(ExecutionStatus status) {
            // given
            MissionExecution execution = completedExecution();
            execution.setStatus(status);

            // when & then
            assertThatThrownBy(execution::revertCompletion)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("완료된 수행 기록만 취소할 수 있습니다");
            assertThat(execution.getStatus()).isEqualTo(status);
            assertThat(execution.getExpEarned()).isEqualTo(40);
            assertThat(execution.getNote()).isEqualTo("메모");
        }
    }
}
