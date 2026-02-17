package io.pinkspider.global.moderation.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이미지 업로드 메서드에 적용하는 이미지 모더레이션 어노테이션.
 *
 * 이 어노테이션이 적용된 메서드의 MultipartFile 파라미터를 자동으로 탐색하여
 * ImageModerationService를 통해 부적절한 콘텐츠 여부를 검사합니다.
 *
 * 사용 예시:
 * <pre>
 * {@code @ModerateImage}
 * {@code @Transactional(transactionManager = "guildTransactionManager")}
 * public GuildResponse uploadGuildImage(Long guildId, String userId, MultipartFile imageFile) {
 *     // 이미지 모더레이션은 AOP에서 자동 처리됨
 *     ...
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ModerateImage {
}
