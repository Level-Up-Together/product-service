package io.pinkspider.leveluptogethermvp.userservice.core.exception.custom;

import io.pinkspider.global.exception.CustomException;
import lombok.Getter;

@Getter
public class CustomMissingRequestHeaderException extends CustomException {

    public CustomMissingRequestHeaderException(String code, String message) {
        super(code, message);
    }
}
