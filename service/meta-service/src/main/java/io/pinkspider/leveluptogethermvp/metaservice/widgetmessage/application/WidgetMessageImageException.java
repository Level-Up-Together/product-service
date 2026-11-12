package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * LUT-797: 위젯 문장 배경 이미지 오류 — {@code WidgetMessageExceptionHandler} 가 상태·코드를 그대로 돌려준다(플랫폼 {@code
 * CustomException} 은 늘 500 이라 쓰지 않는다).
 */
@Getter
public class WidgetMessageImageException extends RuntimeException {

    /** 파일 없음·크기 초과·JPG/PNG 아님 */
    public static final String CODE_INVALID = "080203";

    public static final String CODE_SAVE_FAILED = "080204";

    /** 업로드 API 가 발급하지 않은 주소를 문장에 붙이려 함 */
    public static final String CODE_NOT_OWNED = "080205";

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    private WidgetMessageImageException(HttpStatus status, String code, String messageKey) {
        super(messageKey);
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
    }

    public static WidgetMessageImageException invalid() {
        return new WidgetMessageImageException(
                HttpStatus.BAD_REQUEST, CODE_INVALID, "error.widget_message.image_invalid");
    }

    public static WidgetMessageImageException saveFailed() {
        return new WidgetMessageImageException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                CODE_SAVE_FAILED,
                "error.widget_message.image_save_failed");
    }

    public static WidgetMessageImageException notOwned() {
        return new WidgetMessageImageException(
                HttpStatus.BAD_REQUEST, CODE_NOT_OWNED, "error.widget_message.image_not_owned");
    }
}
