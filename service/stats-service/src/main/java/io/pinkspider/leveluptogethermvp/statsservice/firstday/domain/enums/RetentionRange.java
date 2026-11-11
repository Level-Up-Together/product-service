package io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums;

import java.util.Arrays;
import java.util.Optional;

/** LUT-779: 조회 기간 — 최근 4·8·12주 가입자. API 값은 {@code 4w·8w·12w} */
public enum RetentionRange {
    W4("4w", 4),
    W8("8w", 8),
    W12("12w", 12);

    private final String value;
    private final int weeks;

    RetentionRange(String value, int weeks) {
        this.value = value;
        this.weeks = weeks;
    }

    public String value() {
        return value;
    }

    public int days() {
        return weeks * 7;
    }

    public static Optional<RetentionRange> parse(String value) {
        return Arrays.stream(values()).filter(r -> r.value.equalsIgnoreCase(value)).findFirst();
    }
}
