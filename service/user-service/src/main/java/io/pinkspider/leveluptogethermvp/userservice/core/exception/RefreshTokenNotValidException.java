package io.pinkspider.leveluptogethermvp.userservice.core.exception;

import io.pinkspider.global.exception.CustomException;
import lombok.Getter;

@Getter
public class RefreshTokenNotValidException extends CustomException {

    public RefreshTokenNotValidException(String code, String message) {
        super(code, message);
    }
}
