package io.pinkspider.leveluptogethermvp.userservice.core.exception.handler;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.custom.JwtException;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * JWT 인증 실패(010102 무효 refresh / 010105 블랙리스트 / 010106 절대 상한 초과)를
 * 500이 아닌 401로 응답한다. 클라이언트(RN/웹)가 서버 장애와 구분해 재로그인 플로우로
 * 진입할 수 있게 하기 위함. 예기치 못한 오류(TOKEN_REISSUE_FAILED 등 CustomException)는
 * 기존대로 플랫폼 RestExceptionHandler 가 500으로 처리한다.
 */
@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class JwtExceptionHandler {

    @Autowired(required = false)
    private MessageSource messageSource;

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<Object> handleJwtException(JwtException e) {
        // 인증 실패는 정상 플로우의 일부이므로 error 가 아닌 info 로 남긴다
        log.info("[jwt] auth failure code={} message={}", e.getCode(), e.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(
                        ApiResult.builder()
                                .code(e.getCode())
                                .message(resolveMessage(e.getMessage()))
                                .build());
    }

    private String resolveMessage(String messageOrKey) {
        if (messageSource == null || messageOrKey == null) {
            return messageOrKey;
        }
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(messageOrKey, null, messageOrKey, locale);
    }
}
