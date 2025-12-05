package io.pinkspider.global.util;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ValidationUtils {

    private final Validator validator;

    public <T> void validate(List<T> dataList) {
        for (T data : dataList) {
            Set<ConstraintViolation<T>> violations = validator.validate(data);

            if (violations != null && !violations.isEmpty()) {

                for (ConstraintViolation violation : violations) {
                    throw new ValidationException(violation.getMessage());
                }
            }
        }
    }

    public <T> void validate(T data) {
        Set<ConstraintViolation<T>> violations = validator.validate(data);

        if (violations != null && !violations.isEmpty()) {
            for (ConstraintViolation violation : violations) {
                throw new ValidationException(violation.getMessage());
            }
        }
    }
}
