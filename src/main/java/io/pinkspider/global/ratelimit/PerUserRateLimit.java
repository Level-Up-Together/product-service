package io.pinkspider.global.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 사용자별 Rate Limit 어노테이션
 * Redis를 사용하여 사용자별로 요청 횟수를 제한합니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PerUserRateLimit {
    /**
     * Rate Limit 이름 (Redis key prefix로 사용)
     */
    String name();

    /**
     * 허용 요청 횟수
     */
    int limit() default 10;

    /**
     * 시간 윈도우 (초)
     */
    int windowSeconds() default 60;
}
