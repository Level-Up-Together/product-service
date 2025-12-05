package io.pinkspider.global.exception;

import lombok.Getter;

@Getter
public class ClientException extends RuntimeException {

    private String code;
    private String message;

    public ClientException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public ClientException(String message) {
        super(message);
        this.message = message;
    }
}
