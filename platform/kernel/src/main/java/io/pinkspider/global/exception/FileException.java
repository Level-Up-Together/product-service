package io.pinkspider.global.exception;

import lombok.Getter;

@Getter
public class FileException extends RuntimeException {

    private String code;
    private String message;

    public FileException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
}

