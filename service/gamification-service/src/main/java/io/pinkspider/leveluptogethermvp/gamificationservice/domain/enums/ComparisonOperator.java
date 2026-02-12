package io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 업적 체크 로직의 비교 연산자
 */
@Getter
@RequiredArgsConstructor
public enum ComparisonOperator {
    EQ("EQ", "같음 (==)"),
    GTE("GTE", "이상 (>=)"),
    GT("GT", "초과 (>)"),
    LTE("LTE", "이하 (<=)"),
    LT("LT", "미만 (<)"),
    NE("NE", "다름 (!=)");

    private final String code;
    private final String displayName;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ComparisonOperator fromCode(String code) {
        if (code == null) {
            return GTE; // 기본값
        }
        for (ComparisonOperator operator : values()) {
            if (operator.code.equalsIgnoreCase(code)) {
                return operator;
            }
        }
        return GTE; // 알 수 없는 값이면 기본값 반환
    }

    /**
     * 두 값을 비교합니다.
     *
     * @param currentValue 현재 값
     * @param targetValue  목표 값
     * @return 조건 충족 여부
     */
    public boolean compare(Number currentValue, Number targetValue) {
        if (currentValue == null || targetValue == null) {
            return false;
        }
        double current = currentValue.doubleValue();
        double target = targetValue.doubleValue();

        return switch (this) {
            case EQ -> current == target;
            case GTE -> current >= target;
            case GT -> current > target;
            case LTE -> current <= target;
            case LT -> current < target;
            case NE -> current != target;
        };
    }

    /**
     * boolean 값을 비교합니다. (EQ와 NE만 지원)
     *
     * @param currentValue 현재 값
     * @param targetValue  목표 값
     * @return 조건 충족 여부
     */
    public boolean compareBoolean(Boolean currentValue, Boolean targetValue) {
        if (currentValue == null || targetValue == null) {
            return false;
        }

        return switch (this) {
            case EQ -> currentValue.equals(targetValue);
            case NE -> !currentValue.equals(targetValue);
            default -> false;
        };
    }
}
