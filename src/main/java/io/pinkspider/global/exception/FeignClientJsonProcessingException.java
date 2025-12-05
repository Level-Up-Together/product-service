package io.pinkspider.global.exception;

public class FeignClientJsonProcessingException extends CustomException {

    public FeignClientJsonProcessingException(String code, String message) {
        super(code, message);
    }

}
