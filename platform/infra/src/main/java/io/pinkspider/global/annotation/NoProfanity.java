package io.pinkspider.global.annotation;

import io.pinkspider.global.validation.NoProfanityValidator;
import io.pinkspider.global.validation.ProfanityDetectionMode;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 비속어/금칙어 검증 어노테이션
 * <p>
 * DTO 필드에 적용하여 자동으로 비속어 검사를 수행합니다.
 * </p>
 *
 * <pre>
 * 사용 예시:
 * {@code
 * public class MissionCreateRequest {
 *     @NotBlank
 *     @NoProfanity(fieldName = "미션 제목")
 *     private String title;
 *
 *     @NoProfanity(fieldName = "미션 설명", mode = ProfanityDetectionMode.STRICT)
 *     private String description;
 * }
 * }
 * </pre>
 */
@Documented
@Constraint(validatedBy = NoProfanityValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoProfanity {

    /**
     * 기본 에러 메시지
     */
    String message() default "부적절한 표현이 포함되어 있습니다.";

    /**
     * 검증 그룹
     */
    Class<?>[] groups() default {};

    /**
     * 페이로드
     */
    Class<? extends Payload>[] payload() default {};

    /**
     * 탐지 모드
     * <ul>
     *   <li>LENIENT: 단순 포함 여부만 검사</li>
     *   <li>NORMAL (기본): 대소문자 무시 + 공백/특수문자 제거 + 초성 검사</li>
     *   <li>STRICT: NORMAL + 레벤슈타인 거리 유사도 검사</li>
     * </ul>
     */
    ProfanityDetectionMode mode() default ProfanityDetectionMode.NORMAL;

    /**
     * 에러 메시지에 표시할 필드명 (한글)
     * <p>
     * 예: "미션 제목" → "미션 제목에 부적절한 표현이 포함되어 있습니다."
     * </p>
     */
    String fieldName() default "";

    /**
     * 한글 초성(자음) 검사 여부
     * <p>
     * true인 경우 "ㅅㅂ", "ㅂㅅ" 같은 초성 비속어도 탐지합니다.
     * 주의: 초성 검사 시 "새벽", "사별" 등 일반 단어도 차단될 수 있어 기본값은 false입니다.
     * </p>
     */
    boolean checkKoreanJamo() default false;

    /**
     * 레벤슈타인 거리 임계값 (STRICT 모드에서만 사용)
     * <p>
     * 0: 정확히 일치하는 경우만 탐지 (기본)
     * 1: 1글자 오타까지 허용 (예: "시벌" → "시발" 탐지)
     * </p>
     */
    int levenshteinThreshold() default 0;
}
