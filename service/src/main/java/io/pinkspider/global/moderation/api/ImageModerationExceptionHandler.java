package io.pinkspider.global.moderation.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.moderation.exception.ImageModerationException;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 이미지 검수 거절({@link ImageModerationException})을 4xx/5xx 로 응답한다 (LUT-714).
 *
 * <p>플랫폼 {@code RestExceptionHandler} 는 {@code @Order(HIGHEST_PRECEDENCE + 1)} 로 모든 {@code
 * CustomException} 을 500 으로 바꾸므로, 그보다 앞({@code HIGHEST_PRECEDENCE})에서 이 예외 타입만 가로챈다. 다른 예외는 손대지
 * 않는다(핸들러가 이 타입 하나뿐이라 나머지는 뒤 순위 어드바이스로 넘어간다). 패키지 제한을 두지 않는 이유는 {@code @ModerateImage} 가
 * 프로필·미션·피드·길드·채팅·이벤트 등 모든 서비스의 업로드에 붙기 때문이다. 상세 사유(점수)는 애스펙트가 이미 WARN 으로 남겼으니 여기서는 INFO 한 줄만 남긴다 —
 * 플랫폼 핸들러의 ERROR 스택({@code ##### api error #####})은 더 이상 나오지 않는다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ImageModerationExceptionHandler {

    @Nullable private final MessageSource messageSource;

    public ImageModerationExceptionHandler(@Nullable MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ImageModerationException.class)
    public ResponseEntity<Object> handleImageModeration(ImageModerationException e) {
        log.info(
                "[moderation] 이미지 업로드 거절 응답: status={} code={}",
                e.getStatus().value(),
                e.getCode());
        return ResponseEntity.status(e.getStatus())
                .body(
                        ApiResult.builder()
                                .code(e.getCode())
                                .message(resolveMessage(e.getMessage()))
                                .build());
    }

    private String resolveMessage(String messageKey) {
        if (messageSource == null) {
            return messageKey;
        }
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(messageKey, null, messageKey, locale);
    }
}
