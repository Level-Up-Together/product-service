package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.policy;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * LUT-816: 위젯 'N일' 숫자 색 구간 규칙. 구간은 연속 실천 일수 {@code [min, max]} 양끝 포함, {@code max} 가 null 이면 끝
 * 없음(이상)이다.
 *
 * <ul>
 *   <li>구간끼리 겹치면 안 된다. 끝 없는 구간은 마지막에만 올 수 있다(뒤에 구간이 있으면 겹친다).
 *   <li>구간 사이에 빈 곳이 있으면 안 된다 — 다음 구간은 앞 구간 끝 + 1 에서 시작한다.
 *   <li>첫 구간 앞(예: 0일)과 끝이 있는 마지막 구간 뒤는 비어 있어도 된다 — 그 N 은 위젯 기본 색으로 그린다.
 * </ul>
 *
 * 같은 규칙을 어드민 화면(LUT-816 widget-streak-color.ts)이 저장 전에 먼저 본다.
 */
public final class WidgetStreakColorRule {

    public static final int MAX_RANGES = 20;
    public static final int MAX_DAYS = 99_999;

    /** {@code #rrggbb} — 저장은 소문자 */
    public static final String COLOR_PATTERN = "^#[0-9a-fA-F]{6}$";

    private WidgetStreakColorRule() {}

    public enum Violation {
        /** 두 구간이 같은 일수를 함께 덮는다(끝 없는 구간 뒤에 구간이 있는 경우 포함) */
        OVERLAP,
        /** 두 구간 사이에 어느 구간에도 들지 않는 일수가 있다 */
        GAP
    }

    /** 구간 {@code [min, max]} + 색 */
    public record Range(int min, Integer max, String color) {

        public boolean contains(int days) {
            return days >= min && (max == null || days <= max);
        }
    }

    /** 시작 일수 순으로 정렬하고 색을 소문자로 맞춘다 */
    public static List<Range> normalize(List<Range> ranges) {
        return ranges.stream()
                .map(r -> new Range(r.min(), r.max(), r.color().toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparingInt(Range::min))
                .toList();
    }

    /** 정렬된 구간 목록의 첫 위반 — 없으면 null. 겹침을 빈 곳보다 먼저 본다 */
    public static Violation firstViolation(List<Range> sorted) {
        for (int i = 1; i < sorted.size(); i++) {
            Range previous = sorted.get(i - 1);
            Range next = sorted.get(i);
            if (previous.max() == null || next.min() <= previous.max()) {
                return Violation.OVERLAP;
            }
            if (next.min() > previous.max() + 1) {
                return Violation.GAP;
            }
        }
        return null;
    }

    /** {@code days} 가 드는 구간의 색 — 없으면 null(위젯 기본 색) */
    public static String colorFor(List<Range> ranges, int days) {
        for (Range range : ranges) {
            if (range.contains(days)) {
                return range.color();
            }
        }
        return null;
    }
}
