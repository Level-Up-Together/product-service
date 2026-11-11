package io.pinkspider.leveluptogethermvp.statsservice.api.exception;

import lombok.Getter;

/**
 * LUT-779: 문자열 파라미터가 허용값 밖({@code range=5w} 등) — {@link StatsRequestExceptionHandler} 가 400 {@code
 * 100101} 로 바꾼다(enum 변환 실패와 같은 응답).
 */
@Getter
public class StatsInvalidParamException extends RuntimeException {

    private final String name;
    private final String expected;

    public StatsInvalidParamException(String name, String expected) {
        super("invalid parameter " + name + " (expected " + expected + ")");
        this.name = name;
        this.expected = expected;
    }
}
