package io.pinkspider.global.image;

import io.pinkspider.global.exception.CustomException;
import org.springframework.http.HttpStatus;

/**
 * 업로드된 파일이 이미지가 아닐 때(헤더 시그니처 불일치) 던지는 예외 — HTTP 400 (LUT-710).
 *
 * <p>확장자·Content-Type 만 믿던 저장소 검증을 보완한다: 랜덤 바이트를 {@code .png} 로 올려도 예전엔 검수(fail-open)를 지나 그대로 저장됐다.
 * {@code ImageUploadExceptionHandler} 가 받아 400 으로 응답한다(본문 {@code code 000012}, {@code message}
 * i18n).
 */
public class InvalidImageException extends CustomException {

    public static final String NOT_IMAGE_CODE = "000012";
    public static final String NOT_IMAGE_MESSAGE_KEY = "error.image.not_image";

    private InvalidImageException(String code, String messageKey) {
        super(code, messageKey);
    }

    public static InvalidImageException notImage() {
        return new InvalidImageException(NOT_IMAGE_CODE, NOT_IMAGE_MESSAGE_KEY);
    }

    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
