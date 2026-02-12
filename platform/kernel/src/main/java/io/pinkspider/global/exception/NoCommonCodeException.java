package io.pinkspider.global.exception;

import lombok.Getter;

@Getter
public class NoCommonCodeException extends ClientException {

    private String code;
    private String message;

    public NoCommonCodeException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public NoCommonCodeException(String message) {
        super(message);
        this.message = message;
    }
}
