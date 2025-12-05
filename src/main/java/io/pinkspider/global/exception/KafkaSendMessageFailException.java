package io.pinkspider.global.exception;

import lombok.Getter;

@Getter
public class KafkaSendMessageFailException extends ClientException {

    private String code;
    private String message;

    public KafkaSendMessageFailException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public KafkaSendMessageFailException(String message) {
        super(message);
        this.message = message;
    }
}
