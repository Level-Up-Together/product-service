package io.pinkspider.global.image;

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

@DisplayName("ImageUploadExceptionHandler (LUT-714 검수 400/503 · LUT-710 비이미지 400)")
class ImageUploadExceptionHandlerTest {

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("부적절한 이미지: 400 + 000010, MessageSource 가 없으면 메시지 키 그대로")
    void blockedWithoutMessageSource() {
        ImageUploadExceptionHandler handler = new ImageUploadExceptionHandler(null);

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
        ImageUploadExceptionHandler handler = new ImageUploadExceptionHandler(null);

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
        ImageUploadExceptionHandler handler = new ImageUploadExceptionHandler(messageSource);

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

    @Test
    @DisplayName("이미지 아닌 파일(LUT-710): 400 + 000012")
    void notImageIs400() {
        ImageUploadExceptionHandler handler = new ImageUploadExceptionHandler(null);

        ResponseEntity<Object> response =
                handler.handleInvalidImage(InvalidImageException.notImage());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResult<?> body = (ApiResult<?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("000012");
        assertThat(body.getMessage()).isEqualTo("error.image.not_image");
    }
}
