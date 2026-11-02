package io.pinkspider.leveluptogethermvp.missionservice.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("MissionExpPolicy 단위 테스트 (LUT-568)")
class MissionExpPolicyTest {

    @Nested
    @DisplayName("timedExp — 60분까지 분당 1, 초과 구간 5분당 1")
    class TimedExpTest {

        @ParameterizedTest(name = "{0}분 → {1} EXP")
        @CsvSource({
            "0, 1", // 1분 미만도 최소 1
            "1, 1",
            "33, 33",
            "59, 59",
            "60, 60",
            "90, 90", // LUT-696: 120분까지 분당 1 (예전 60분 기준이면 66)
            "120, 120",
            "121, 120", // 초과 1분은 5분 단위 미달 → 0
            "124, 120",
            "125, 121",
            "180, 132",
            "240, 144", // 티켓 예시: 120 + 24
            "480, 144", // 240분 상한
            "1000, 144"
        })
        void timedExp(long minutes, int expected) {
            assertThat(MissionExpPolicy.timedExp(minutes)).isEqualTo(expected);
        }

        @Test
        @DisplayName("음수 분은 최소 1 로 처리한다")
        void timedExp_negative_returnsOne() {
            assertThat(MissionExpPolicy.timedExp(-5)).isEqualTo(1);
        }

        @Test
        @DisplayName("쪼개기 유인 없음 — 10분×12회와 120분×1회가 같다")
        void timedExp_noSplitIncentiveUpToOneHour() {
            int split = 12 * MissionExpPolicy.timedExp(10);
            int whole = MissionExpPolicy.timedExp(120);
            assertThat(split).isEqualTo(whole);
        }
    }

    @Nested
    @DisplayName("milestoneReached — 당일 체크 완료 누계가 3·5 에 정확히 도달한 경우만")
    class MilestoneReachedTest {

        @ParameterizedTest(name = "누계 {0} → 마일스톤 {1}")
        @CsvSource({"0, 0", "1, 0", "2, 0", "3, 3", "4, 0", "5, 5", "6, 0", "10, 0", "11, 0"})
        void milestoneReached(long completions, int expected) {
            assertThat(MissionExpPolicy.milestoneReached(completions)).isEqualTo(expected);
        }

        @Test
        @DisplayName("마일스톤 보너스는 20, 대상은 3·5개")
        void constants() {
            assertThat(MissionExpPolicy.MILESTONE_BONUS_EXP).isEqualTo(20);
            assertThat(MissionExpPolicy.SIMPLE_DAILY_MILESTONES).containsExactly(3, 5);
        }
    }

    @Nested
    @DisplayName("체크 미션 상수")
    class SimpleTest {

        @Test
        @DisplayName("체크 완료당 10, 하루 10회 한도")
        void simpleConstants() {
            assertThat(MissionExpPolicy.SIMPLE_EXP).isEqualTo(10);
            assertThat(MissionExpPolicy.SIMPLE_DAILY_LIMIT).isEqualTo(10);
        }

        @Test
        @DisplayName("티켓 예시 — 체크 10개(한도) = 100 + 마일스톤 40 = 140")
        void ticketExample() {
            int checks = MissionExpPolicy.SIMPLE_DAILY_LIMIT * MissionExpPolicy.SIMPLE_EXP;
            int milestones =
                    MissionExpPolicy.SIMPLE_DAILY_MILESTONES.size()
                            * MissionExpPolicy.MILESTONE_BONUS_EXP;
            assertThat(checks + milestones).isEqualTo(140);
        }
    }

    @Nested
    @DisplayName("autoCompleteExp — 설정값을 쓰되 정상 4시간 완료(96)를 넘지 못한다")
    class AutoCompleteExpTest {

        @Test
        @DisplayName("설정값 200 은 144 로 클램프된다 (방치가 정상 완료보다 유리해지지 않도록)")
        void clampsToFourHourCompletion() {
            assertThat(MissionExpPolicy.autoCompleteExp(200)).isEqualTo(144);
            assertThat(MissionExpPolicy.autoCompleteExp(120)).isEqualTo(120);
        }

        @Test
        @DisplayName("설정값이 96 이하면 그대로 쓴다")
        void keepsSmallerConfiguredValue() {
            assertThat(MissionExpPolicy.autoCompleteExp(48)).isEqualTo(48);
            assertThat(MissionExpPolicy.autoCompleteExp(0)).isEqualTo(0);
        }
    }
}
