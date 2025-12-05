package io.pinkspider.global.exception.feign;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CustomFeignErrorForm {

    private String code;
    private String message;

    public CustomFeignErrorForm(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
