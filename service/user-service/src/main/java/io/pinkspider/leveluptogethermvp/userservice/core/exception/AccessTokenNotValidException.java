package io.pinkspider.leveluptogethermvp.userservice.core.exception;

import io.pinkspider.global.exception.CustomException;
import lombok.Getter;

@Getter
public class AccessTokenNotValidException extends CustomException {

    public AccessTokenNotValidException(String code, String message) {
        super(code, message);
    }
}
