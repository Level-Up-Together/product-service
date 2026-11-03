package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("ItemPushSettingRequest 검증 (LUT-573 슬롯 시각 범위)")
class ItemPushSettingRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<ItemPushSettingRequest>> validate(String before, String after) {
        return validator.validate(
                ItemPushSettingRequest.builder()
                        .beforeActivityTime(before)
                        .afterCompleteTime(after)
                        .build());
    }

    @ParameterizedTest(name = "아침 {0} → 유효")
    @CsvSource({"08:00", "09:30", "11:59"})
    void morningValid(String time) {
        assertThat(validate(time, null)).isEmpty();
    }

    @ParameterizedTest(name = "아침 {0} → 400 (08:00~11:59 밖)")
    @CsvSource({"07:59", "12:00", "15:00", "9:00", "ab:cd"})
    void morningInvalid(String time) {
        assertThat(validate(time, null)).isNotEmpty();
    }

    @ParameterizedTest(name = "오후 {0} → 유효")
    @CsvSource({"12:00", "18:00", "20:59"})
    void afternoonValid(String time) {
        assertThat(validate(null, time)).isEmpty();
    }

    @ParameterizedTest(name = "오후 {0} → 400 (12:00~20:59 밖)")
    @CsvSource({"11:00", "11:59", "21:00", "08:00"})
    void afternoonInvalid(String time) {
        assertThat(validate(null, time)).isNotEmpty();
    }

    @Test
    @DisplayName("E5/E6: 한쪽만 또는 둘 다 null 은 허용 (null 슬롯은 미발송, 둘 다 null 은 전체 미발송)")
    void nullsAllowed() {
        assertThat(validate("09:00", null)).isEmpty();
        assertThat(validate(null, "18:00")).isEmpty();
        assertThat(validate(null, null)).isEmpty();
    }
}
