package io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pinkspider.leveluptogethermvp.statsservice.api.exception.StatsInvalidParamException;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.enums.CompletionBucket;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.enums.MissionStatsSortField;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("미션 통계 기간·비율·정렬·분포 규칙 (LUT-790)")
class MissionStatsPolicyTest {

    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);

    @Test
    @DisplayName("기간 — KST 날짜를 UTC 구간으로, 날짜 목록은 양끝 포함")
    void period() {
        MissionStatsPeriod period = MissionStatsPeriod.of(NOV_1, NOV_1.plusDays(2));

        assertThat(period.utcFrom()).isEqualTo(LocalDateTime.of(2026, 10, 31, 15, 0));
        assertThat(period.utcTo()).isEqualTo(LocalDateTime.of(2026, 11, 3, 15, 0));
        assertThat(period.days()).containsExactly(NOV_1, NOV_1.plusDays(1), NOV_1.plusDays(2));
    }

    @Test
    @DisplayName("기간 — from > to 는 바꾸고, 400일까지 받고 401일은 400 100101")
    void periodBounds() {
        assertThat(MissionStatsPeriod.of(NOV_1.plusDays(5), NOV_1))
                .isEqualTo(new MissionStatsPeriod(NOV_1, NOV_1.plusDays(5)));
        assertThat(MissionStatsPeriod.of(NOV_1, NOV_1.plusDays(399)).days()).hasSize(400);
        assertThatThrownBy(() -> MissionStatsPeriod.of(NOV_1, NOV_1.plusDays(400)))
                .isInstanceOf(StatsInvalidParamException.class)
                .hasFieldOrPropertyWithValue("name", "to");
    }

    @Test
    @DisplayName("비율·평균 — 소수 한 자리, 분모 0 이면 null")
    void rates() {
        assertThat(MissionStatsRates.percent(2, 3)).isEqualTo(66.7);
        assertThat(MissionStatsRates.percent(3, 3)).isEqualTo(100.0);
        assertThat(MissionStatsRates.percent(0, 0)).isNull();
        assertThat(MissionStatsRates.average(7, 2)).isEqualTo(3.5);
        assertThat(MissionStatsRates.average(5, 0)).isNull();
    }

    @ParameterizedTest(name = "{0}회 → {1}")
    @CsvSource({
        "1, ONE",
        "2, TWO_TO_FOUR",
        "4, TWO_TO_FOUR",
        "5, FIVE_TO_NINE",
        "9, FIVE_TO_NINE",
        "10, TEN_OR_MORE",
        "250, TEN_OR_MORE"
    })
    @DisplayName("분포 구간 — 1 / 2~4 / 5~9 / 10 이상")
    void buckets(int completions, CompletionBucket expected) {
        assertThat(CompletionBucket.of(completions)).isEqualTo(expected);
    }

    @Test
    @DisplayName("정렬 — 비우면 완료 수 내림차순, 방향 없으면 열 기본(닉네임만 오름차순)")
    void sortDefaults() {
        assertThat(MissionStatsSort.parse(null)).isEqualTo(MissionStatsSort.DEFAULT);
        assertThat(MissionStatsSort.parse(" ").value()).isEqualTo("completions,desc");
        assertThat(MissionStatsSort.parse("nickname"))
                .isEqualTo(new MissionStatsSort(MissionStatsSortField.NICKNAME, true));
        assertThat(MissionStatsSort.parse("level"))
                .isEqualTo(new MissionStatsSort(MissionStatsSortField.LEVEL, false));
        assertThat(MissionStatsSort.parse("timer_minutes, ASC").value())
                .isEqualTo("timer_minutes,asc");
        assertThat(MissionStatsSort.parse("last_completed_at,desc").value())
                .isEqualTo("last_completed_at,desc");
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "completions,up", "completions,asc,1", "COMPLETIONS"})
    @DisplayName("정렬 — 허용값 밖이면 400 100101 (sort)")
    void sortInvalid(String sort) {
        assertThatThrownBy(() -> MissionStatsSort.parse(sort))
                .isInstanceOf(StatsInvalidParamException.class)
                .hasFieldOrPropertyWithValue("name", "sort")
                .hasFieldOrPropertyWithValue(
                        "expected",
                        "completions|timer_minutes|last_completed_at|nickname|level[,asc|desc]");
    }
}
