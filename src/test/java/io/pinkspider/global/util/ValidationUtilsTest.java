package io.pinkspider.global.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidationUtils 단위 테스트")
class ValidationUtilsTest {

    @Mock
    private Validator validator;

    @Mock
    private ConstraintViolation<TestData> violation;

    private ValidationUtils validationUtils;

    @BeforeEach
    void setUp() {
        validationUtils = new ValidationUtils(validator);
    }

    static class TestData {
        @NotBlank
        private String name;

        @Size(min = 1, max = 100)
        private String description;

        public TestData(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    @Nested
    @DisplayName("validate(T) 테스트")
    class ValidateSingleTest {

        @Test
        @DisplayName("유효한 데이터면 예외가 발생하지 않는다")
        void validate_validData_noException() {
            // given
            TestData data = new TestData("테스트", "설명");
            when(validator.validate(any(TestData.class))).thenReturn(Collections.emptySet());

            // when & then
            assertThatCode(() -> validationUtils.validate(data))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("유효하지 않은 데이터면 ValidationException이 발생한다")
        void validate_invalidData_throwsException() {
            // given
            TestData data = new TestData("", "설명");
            Set<ConstraintViolation<TestData>> violations = new HashSet<>();
            violations.add(violation);

            when(validator.validate(any(TestData.class))).thenReturn(violations);
            when(violation.getMessage()).thenReturn("이름은 필수입니다");

            // when & then
            assertThatThrownBy(() -> validationUtils.validate(data))
                .isInstanceOf(ValidationException.class)
                .hasMessage("이름은 필수입니다");
        }

        @Test
        @DisplayName("violations가 null이면 예외가 발생하지 않는다")
        void validate_nullViolations_noException() {
            // given
            TestData data = new TestData("테스트", "설명");
            when(validator.validate(any(TestData.class))).thenReturn(null);

            // when & then
            assertThatCode(() -> validationUtils.validate(data))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("validate(List<T>) 테스트")
    class ValidateListTest {

        @Test
        @DisplayName("모든 데이터가 유효하면 예외가 발생하지 않는다")
        void validate_allValidData_noException() {
            // given
            List<TestData> dataList = List.of(
                new TestData("테스트1", "설명1"),
                new TestData("테스트2", "설명2")
            );
            when(validator.validate(any(TestData.class))).thenReturn(Collections.emptySet());

            // when & then
            assertThatCode(() -> validationUtils.validate(dataList))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("하나라도 유효하지 않으면 ValidationException이 발생한다")
        void validate_someInvalidData_throwsException() {
            // given
            List<TestData> dataList = List.of(
                new TestData("테스트1", "설명1"),
                new TestData("", "설명2")
            );

            Set<ConstraintViolation<TestData>> emptyViolations = Collections.emptySet();
            Set<ConstraintViolation<TestData>> violations = new HashSet<>();
            violations.add(violation);

            when(validator.validate(any(TestData.class)))
                .thenReturn(emptyViolations)
                .thenReturn(violations);
            when(violation.getMessage()).thenReturn("이름은 필수입니다");

            // when & then
            assertThatThrownBy(() -> validationUtils.validate(dataList))
                .isInstanceOf(ValidationException.class)
                .hasMessage("이름은 필수입니다");
        }

        @Test
        @DisplayName("빈 리스트면 예외가 발생하지 않는다")
        void validate_emptyList_noException() {
            // given
            List<TestData> dataList = Collections.emptyList();

            // when & then
            assertThatCode(() -> validationUtils.validate(dataList))
                .doesNotThrowAnyException();
        }
    }
}
