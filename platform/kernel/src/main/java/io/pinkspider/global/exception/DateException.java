package io.pinkspider.global.exception;

public class DateException extends RuntimeException {

    private int code;
    private String message;

    public DateException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
}
