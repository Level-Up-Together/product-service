package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.policy.WidgetHourRange.Gap;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("WidgetHourRange — 위젯 문장 시간대 규칙 (LUT-775)")
class WidgetHourRangeTest {

    @Nested
    @DisplayName("contains")
    class Contains {

        @ParameterizedTest(name = "6~12 에 {0}시 = {1}")
        @CsvSource({"5, false", "6, true", "11, true", "12, false"})
        void normal(int hour, boolean expected) {
            assertThat(WidgetHourRange.contains(6, 12, hour)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "22~2 에 {0}시 = {1}")
        @CsvSource({"21, false", "22, true", "23, true", "0, true", "1, true", "2, false"})
        void crossesMidnight(int hour, boolean expected) {
            assertThat(WidgetHourRange.contains(22, 2, hour)).isEqualTo(expected);
        }

        @Test
        @DisplayName("0~24 는 하루 전체")
        void wholeDay() {
            for (int hour = 0; hour < 24; hour++) {
                assertThat(WidgetHourRange.contains(0, 24, hour)).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("gaps")
    class Gaps {

        @Test
        @DisplayName("구간이 없으면 하루 전체가 빈 구간")
        void empty() {
            assertThat(WidgetHourRange.gaps(List.of())).containsExactly(new Gap(0, 24));
        }

        @Test
        @DisplayName("빈 시각을 이어진 구간으로 묶는다")
        void merged() {
            List<int[]> ranges = List.of(new int[] {6, 12}, new int[] {12, 18});
            assertThat(WidgetHourRange.gaps(ranges))
                    .containsExactly(new Gap(0, 6), new Gap(18, 24));
        }

        @Test
        @DisplayName("자정을 넘는 구간도 덮는다 — 초기 데이터 4구간이면 빈 곳 없음")
        void seedCoversWholeDay() {
            List<int[]> ranges =
                    List.of(
                            new int[] {6, 12},
                            new int[] {12, 18},
                            new int[] {18, 22},
                            new int[] {22, 6});
            assertThat(WidgetHourRange.gaps(ranges)).isEmpty();
        }

        @Test
        @DisplayName("자정을 넘는 빈 구간은 둘로 나뉜다")
        void gapAcrossMidnight() {
            List<int[]> ranges = List.of(new int[] {2, 23});
            assertThat(WidgetHourRange.gaps(ranges))
                    .containsExactly(new Gap(0, 2), new Gap(23, 24));
        }

        @Test
        @DisplayName("겹치는 구간은 한 번만 센다")
        void overlap() {
            List<int[]> ranges = List.of(new int[] {0, 10}, new int[] {5, 24});
            assertThat(WidgetHourRange.gaps(ranges)).isEmpty();
        }
    }
}
