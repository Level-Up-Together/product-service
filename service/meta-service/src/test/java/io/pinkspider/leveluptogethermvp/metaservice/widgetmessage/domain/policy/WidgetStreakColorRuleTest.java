package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.policy.WidgetStreakColorRule.Range;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.policy.WidgetStreakColorRule.Violation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WidgetStreakColorRule — 위젯 'N일' 숫자 색 구간 규칙 (LUT-816)")
class WidgetStreakColorRuleTest {

    private static final String GREEN = "#1dec06";
    private static final String CYAN = "#00f9ff";
    private static final String PINK = "#ff4fa3";

    @Test
    @DisplayName("시작 일수 순으로 정렬하고 색을 소문자로 맞춘다")
    void normalize() {
        List<Range> normalized =
                WidgetStreakColorRule.normalize(
                        List.of(
                                new Range(31, null, "#FF4FA3"),
                                new Range(1, 7, "#1DEC06"),
                                new Range(8, 30, CYAN)));

        assertThat(normalized)
                .containsExactly(
                        new Range(1, 7, GREEN), new Range(8, 30, CYAN), new Range(31, null, PINK));
    }

    @Test
    @DisplayName("이어진 구간·마지막만 끝 없는 구간은 위반이 없다")
    void valid() {
        assertThat(WidgetStreakColorRule.firstViolation(List.of())).isNull();
        assertThat(WidgetStreakColorRule.firstViolation(List.of(new Range(1, null, GREEN))))
                .isNull();
        assertThat(
                        WidgetStreakColorRule.firstViolation(
                                List.of(
                                        new Range(1, 7, GREEN),
                                        new Range(8, 30, CYAN),
                                        new Range(31, null, PINK))))
                .isNull();
        // 끝이 있는 마지막 구간 뒤와 첫 구간 앞(0일)은 비어 있어도 된다
        assertThat(
                        WidgetStreakColorRule.firstViolation(
                                List.of(new Range(1, 7, GREEN), new Range(8, 30, CYAN))))
                .isNull();
        // 하루짜리 구간
        assertThat(
                        WidgetStreakColorRule.firstViolation(
                                List.of(new Range(0, 0, GREEN), new Range(1, 1, CYAN))))
                .isNull();
    }

    @Test
    @DisplayName("같은 일수를 함께 덮으면 겹침 — 경계가 같은 값이어도 겹친다")
    void overlap() {
        assertThat(
                        WidgetStreakColorRule.firstViolation(
                                List.of(new Range(1, 10, GREEN), new Range(8, 30, CYAN))))
                .isEqualTo(Violation.OVERLAP);
        assertThat(
                        WidgetStreakColorRule.firstViolation(
                                List.of(new Range(1, 7, GREEN), new Range(7, 30, CYAN))))
                .isEqualTo(Violation.OVERLAP);
        // 시작 값이 같은 두 구간
        assertThat(
                        WidgetStreakColorRule.firstViolation(
                                List.of(new Range(1, 7, GREEN), new Range(1, 7, CYAN))))
                .isEqualTo(Violation.OVERLAP);
    }

    @Test
    @DisplayName("끝 없는 구간 뒤에 구간이 있으면 겹침")
    void openEndedMustBeLast() {
        assertThat(
                        WidgetStreakColorRule.firstViolation(
                                List.of(new Range(1, null, GREEN), new Range(8, 30, CYAN))))
                .isEqualTo(Violation.OVERLAP);
    }

    @Test
    @DisplayName("앞 구간 끝 + 1 에서 시작하지 않으면 빈 곳")
    void gap() {
        assertThat(
                        WidgetStreakColorRule.firstViolation(
                                List.of(new Range(1, 7, GREEN), new Range(9, 30, CYAN))))
                .isEqualTo(Violation.GAP);
        assertThat(
                        WidgetStreakColorRule.firstViolation(
                                List.of(
                                        new Range(1, 7, GREEN),
                                        new Range(8, 30, CYAN),
                                        new Range(40, null, PINK))))
                .isEqualTo(Violation.GAP);
    }

    @Test
    @DisplayName("일수가 드는 구간의 색 — 양끝 포함, 드는 구간이 없으면 null")
    void colorFor() {
        List<Range> ranges =
                List.of(new Range(1, 7, GREEN), new Range(8, 30, CYAN), new Range(31, null, PINK));

        assertThat(WidgetStreakColorRule.colorFor(ranges, 0)).isNull();
        assertThat(WidgetStreakColorRule.colorFor(ranges, 1)).isEqualTo(GREEN);
        assertThat(WidgetStreakColorRule.colorFor(ranges, 7)).isEqualTo(GREEN);
        assertThat(WidgetStreakColorRule.colorFor(ranges, 8)).isEqualTo(CYAN);
        assertThat(WidgetStreakColorRule.colorFor(ranges, 30)).isEqualTo(CYAN);
        assertThat(WidgetStreakColorRule.colorFor(ranges, 31)).isEqualTo(PINK);
        assertThat(WidgetStreakColorRule.colorFor(ranges, 9_999)).isEqualTo(PINK);
        // 끝이 있는 마지막 구간 뒤
        assertThat(WidgetStreakColorRule.colorFor(List.of(new Range(1, 7, GREEN)), 8)).isNull();
        assertThat(WidgetStreakColorRule.colorFor(List.of(), 5)).isNull();
    }
}
