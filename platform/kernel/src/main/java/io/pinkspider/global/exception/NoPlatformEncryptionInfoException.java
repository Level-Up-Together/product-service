package io.pinkspider.global.exception;

import lombok.Getter;

@Getter
public class NoPlatformEncryptionInfoException extends CustomException {

    public NoPlatformEncryptionInfoException(String code, String message) {
        super(code, message);
    }
}
