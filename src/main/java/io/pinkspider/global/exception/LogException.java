package io.pinkspider.global.exception;

import lombok.Getter;

@Getter
public class LogException extends RuntimeException {

    private int code;
    private String message;

    public LogException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
}
