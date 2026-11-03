package io.pinkspider.global.image;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.moderation.exception.ImageModerationException;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 이미지 업로드 거절을 4xx/5xx 로 응답한다 (LUT-714 검수, LUT-710 형식 검증).
 *
 * <p>플랫폼 {@code RestExceptionHandler} 는 {@code @Order(HIGHEST_PRECEDENCE + 1)} 로 모든 {@code
 * CustomException} 을 500 으로 바꾸므로, 그보다 앞({@code HIGHEST_PRECEDENCE})에서 여기 적힌 예외 타입만 가로챈다 — 다른 예외는 뒤
 * 순위 어드바이스로 넘어간다. 패키지 제한을 두지 않는 이유는 업로드가 프로필·미션·피드·길드·채팅·이벤트·어드민 내부 등 모든 서비스에 있기 때문이다. 상세 사유는 던지는
 * 쪽이 이미 WARN 으로 남겼으니 여기서는 INFO 한 줄만 남긴다(플랫폼 핸들러의 ERROR 스택 없음).
 *
 * <ul>
 *   <li>{@link ImageModerationException} — 부적절한 이미지 {@code 000010} → 400, 검수 불가 {@code 000011} →
 *       503
 *   <li>{@link InvalidImageException} — 이미지 아닌 파일 {@code 000012} → 400
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ImageUploadExceptionHandler {

    @Nullable private final MessageSource messageSource;

    public ImageUploadExceptionHandler(@Nullable MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ImageModerationException.class)
    public ResponseEntity<Object> handleImageModeration(ImageModerationException e) {
        return respond(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(InvalidImageException.class)
    public ResponseEntity<Object> handleInvalidImage(InvalidImageException e) {
        return respond(e.getStatus(), e.getCode(), e.getMessage());
    }

    private ResponseEntity<Object> respond(HttpStatus status, String code, String messageKey) {
        log.info("[image] 업로드 거절 응답: status={} code={}", status.value(), code);
        return ResponseEntity.status(status)
                .body(ApiResult.builder().code(code).message(resolveMessage(messageKey)).build());
    }

    private String resolveMessage(String messageKey) {
        if (messageSource == null) {
            return messageKey;
        }
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(messageKey, null, messageKey, locale);
    }
}
