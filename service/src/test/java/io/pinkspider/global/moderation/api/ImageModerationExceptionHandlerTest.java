package io.pinkspider.global.moderation.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.moderation.exception.ImageModerationException;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("ImageModerationExceptionHandler (LUT-714 검수 거절 400/503)")
class ImageModerationExceptionHandlerTest {

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("부적절한 이미지: 400 + 000010, MessageSource 가 없으면 메시지 키 그대로")
    void blockedWithoutMessageSource() {
        ImageModerationExceptionHandler handler = new ImageModerationExceptionHandler(null);

        ResponseEntity<Object> response =
                handler.handleImageModeration(ImageModerationException.blocked());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResult<?> body = (ApiResult<?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("000010");
        assertThat(body.getMessage()).isEqualTo("error.moderation.inappropriate_image");
    }

    @Test
    @DisplayName("검수 불가(fail-closed): 503 + 000011")
    void unavailableIs503() {
        ImageModerationExceptionHandler handler = new ImageModerationExceptionHandler(null);

        ResponseEntity<Object> response =
                handler.handleImageModeration(ImageModerationException.unavailable());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ApiResult<?> body = (ApiResult<?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("000011");
        assertThat(body.getMessage()).isEqualTo("error.moderation.unavailable");
    }

    @Test
    @DisplayName("MessageSource 가 있으면 요청 로케일로 현지화한 메시지를 돌려준다")
    void localizedMessage() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage(
                "error.moderation.inappropriate_image", Locale.KOREAN, "부적절한 이미지가 감지되었습니다.");
        messageSource.addMessage(
                "error.moderation.inappropriate_image",
                Locale.ENGLISH,
                "Inappropriate image detected.");
        ImageModerationExceptionHandler handler =
                new ImageModerationExceptionHandler(messageSource);

        LocaleContextHolder.setLocale(Locale.KOREAN);
        ApiResult<?> ko =
                (ApiResult<?>)
                        handler.handleImageModeration(ImageModerationException.blocked()).getBody();
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        ApiResult<?> en =
                (ApiResult<?>)
                        handler.handleImageModeration(ImageModerationException.blocked()).getBody();

        assertThat(ko).isNotNull();
        assertThat(ko.getMessage()).isEqualTo("부적절한 이미지가 감지되었습니다.");
        assertThat(en).isNotNull();
        assertThat(en.getMessage()).isEqualTo("Inappropriate image detected.");
    }
}
