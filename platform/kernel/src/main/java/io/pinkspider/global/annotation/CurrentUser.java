package io.pinkspider.global.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JWT 토큰에서 추출한 현재 로그인 사용자 ID를 주입하는 어노테이션.
 *
 * <p>사용 예시:</p>
 * <pre>
 * {@code
 * @GetMapping("/my")
 * public ResponseEntity<?> getMyData(@CurrentUser String userId) {
 *     // userId는 JWT 토큰에서 자동 추출됨
 * }
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {

    /**
     * 사용자 ID가 필수인지 여부.
     * false로 설정하면 인증되지 않은 경우 null 반환.
     * true(기본값)로 설정하면 인증되지 않은 경우 예외 발생.
     */
    boolean required() default true;
}
