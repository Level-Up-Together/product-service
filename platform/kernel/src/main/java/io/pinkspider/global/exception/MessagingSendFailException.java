package io.pinkspider.global.exception;

import lombok.Getter;

@Getter
public class MessagingSendFailException extends ClientException {

    private String code;
    private String message;

    public MessagingSendFailException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public MessagingSendFailException(String message) {
        super(message);
        this.message = message;
    }
}
