package io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** LUT-790: 유저별 기간 완료 수 분포 구간 — 1회 / 2~4회 / 5~9회 / 10회 이상 */
@Getter
@RequiredArgsConstructor
public enum CompletionBucket {
    ONE(1, 1),
    TWO_TO_FOUR(2, 4),
    FIVE_TO_NINE(5, 9),
    TEN_OR_MORE(10, null);

    private final int min;

    /** 상한(포함), 마지막 구간은 null */
    private final Integer max;

    /** 완료 수가 속한 구간 — 1 이상만 들어온다(0회 유저는 목록에 없다) */
    public static CompletionBucket of(int completions) {
        if (completions >= TEN_OR_MORE.min) {
            return TEN_OR_MORE;
        }
        if (completions >= FIVE_TO_NINE.min) {
            return FIVE_TO_NINE;
        }
        return completions >= TWO_TO_FOUR.min ? TWO_TO_FOUR : ONE;
    }
}
