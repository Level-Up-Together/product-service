package io.pinkspider.global.exception;

import lombok.Getter;

@Getter
public class JwtNotValidException extends CustomException {

    public JwtNotValidException(String code, String message) {
        super(code, message);
    }
}
