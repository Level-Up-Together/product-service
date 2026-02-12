package io.pinkspider.leveluptogethermvp.userservice.core.exception;

import io.pinkspider.global.exception.CustomException;
import lombok.Getter;

@Getter
public class CustomBadCredentialException extends CustomException {

    public CustomBadCredentialException(String code, String message) {
        super(code, message);
    }
}
