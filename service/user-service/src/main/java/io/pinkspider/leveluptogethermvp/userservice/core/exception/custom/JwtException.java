package io.pinkspider.leveluptogethermvp.userservice.core.exception.custom;

import io.pinkspider.global.exception.CustomException;
import lombok.Getter;

@Getter
public class JwtException extends CustomException {

    public JwtException(String code, String message) {
        super(code, message);
    }
}
