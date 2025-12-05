package io.pinkspider.leveluptogethermvp.userservice.core.exception;

import io.pinkspider.global.exception.CustomException;

public class NotExistUserException extends CustomException {

    public NotExistUserException(String code, String message) {
        super(code, message);
    }
}
