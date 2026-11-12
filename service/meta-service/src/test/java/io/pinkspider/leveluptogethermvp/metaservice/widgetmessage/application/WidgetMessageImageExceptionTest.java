package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("WidgetMessageImageException — 배경 이미지 오류 코드 (LUT-797)")
class WidgetMessageImageExceptionTest {

    @Test
    @DisplayName("형식·크기 거절 400 080203 · 저장 실패 500 080204 · 발급하지 않은 주소 400 080205")
    void codes() {
        WidgetMessageImageException invalid = WidgetMessageImageException.invalid();
        assertThat(invalid.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getCode()).isEqualTo("080203");
        assertThat(invalid.getMessageKey()).isEqualTo("error.widget_message.image_invalid");

        WidgetMessageImageException saveFailed = WidgetMessageImageException.saveFailed();
        assertThat(saveFailed.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(saveFailed.getCode()).isEqualTo("080204");
        assertThat(saveFailed.getMessageKey()).isEqualTo("error.widget_message.image_save_failed");

        WidgetMessageImageException notOwned = WidgetMessageImageException.notOwned();
        assertThat(notOwned.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(notOwned.getCode()).isEqualTo("080205");
        assertThat(notOwned.getMessageKey()).isEqualTo("error.widget_message.image_not_owned");
    }
}
