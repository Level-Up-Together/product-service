package io.pinkspider.global.exception.feign;

import io.pinkspider.global.exception.CustomException;
import lombok.Getter;

@Getter
public class FeignClientCallException extends CustomException {

    public FeignClientCallException(String code, String message) {
        super(code, message);
    }
}
