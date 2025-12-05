package io.pinkspider.global.exception.feign;

import io.pinkspider.global.exception.CustomException;
import lombok.Getter;

@Getter
public class FeignClientException extends CustomException {

    private final CustomFeignErrorForm errorForm;

    public FeignClientException(CustomFeignErrorForm errorForm) {//, Map<String, Collection<String>> header, CustomFeignErrorForm errorForm) {
        super(errorForm.getCode(), errorForm.getMessage());
        this.errorForm = errorForm;
    }
}
