package io.pinkspider.global.exception;

import lombok.Getter;

@Getter
public class PolicyFilterRuleSetException extends CustomException {

    public PolicyFilterRuleSetException(String code, String message) {
        super(code, message);
    }
}
