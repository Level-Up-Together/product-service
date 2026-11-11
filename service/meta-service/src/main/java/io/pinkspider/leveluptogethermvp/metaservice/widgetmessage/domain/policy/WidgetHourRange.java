package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.policy;

import java.util.ArrayList;
import java.util.List;

/**
 * LUT-775: 위젯 문장 시간대 규칙. 구간은 유저 로컬 시각 {@code [start, end)} — start 0~23, end 1~24, 같으면 안 된다. {@code
 * start > end} 면 자정을 넘는 구간이다(22~2 = 22·23·0·1시). 하루 전체는 0~24.
 */
public final class WidgetHourRange {

    public static final int MIN_START = 0;
    public static final int MAX_START = 23;
    public static final int MIN_END = 1;
    public static final int MAX_END = 24;

    private WidgetHourRange() {}

    /** {@code hour}(0~23)가 구간 안인가 */
    public static boolean contains(int start, int end, int hour) {
        if (start < end) {
            return hour >= start && hour < end;
        }
        return hour >= start || hour < end;
    }

    /**
     * 구간 목록이 덮지 못하는 시각을 이어진 구간으로 돌려준다(0시부터 순서대로). 자정을 넘는 빈 구간은 둘로 나뉜다(예: 23~24, 0~2). 어드민 "빈 시간대"
     * 경고에 쓴다.
     */
    public static List<Gap> gaps(List<int[]> ranges) {
        boolean[] covered = new boolean[24];
        for (int[] r : ranges) {
            for (int hour = 0; hour < 24; hour++) {
                if (contains(r[0], r[1], hour)) {
                    covered[hour] = true;
                }
            }
        }
        List<Gap> gaps = new ArrayList<>();
        int hour = 0;
        while (hour < 24) {
            if (covered[hour]) {
                hour++;
                continue;
            }
            int start = hour;
            while (hour < 24 && !covered[hour]) {
                hour++;
            }
            gaps.add(new Gap(start, hour));
        }
        return gaps;
    }

    /** 빈 구간 {@code [startHour, endHour)} */
    public record Gap(int startHour, int endHour) {}
}
