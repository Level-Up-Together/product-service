package io.pinkspider.leveluptogethermvp.profanity.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProfanitySeverity Enum 테스트")
class ProfanitySeverityTest {

    @Test
    @DisplayName("LOW 심각도의 속성이 올바르다")
    void low_hasCorrectProperties() {
        // given
        ProfanitySeverity severity = ProfanitySeverity.LOW;

        // then
        assertThat(severity.getName()).isEqualTo("낮음");
        assertThat(severity.getDescription()).isEqualTo("경미한 수준");
    }

    @Test
    @DisplayName("MEDIUM 심각도의 속성이 올바르다")
    void medium_hasCorrectProperties() {
        // given
        ProfanitySeverity severity = ProfanitySeverity.MEDIUM;

        // then
        assertThat(severity.getName()).isEqualTo("중간");
        assertThat(severity.getDescription()).isEqualTo("중간 수준");
    }

    @Test
    @DisplayName("HIGH 심각도의 속성이 올바르다")
    void high_hasCorrectProperties() {
        // given
        ProfanitySeverity severity = ProfanitySeverity.HIGH;

        // then
        assertThat(severity.getName()).isEqualTo("높음");
        assertThat(severity.getDescription()).isEqualTo("심각한 수준");
    }

    @Test
    @DisplayName("총 3개의 심각도 레벨이 존재한다")
    void hasCorrectNumberOfSeverityLevels() {
        // then
        assertThat(ProfanitySeverity.values()).hasSize(3);
    }

    @Test
    @DisplayName("문자열로부터 enum 값을 조회할 수 있다")
    void valueOf_returnsCorrectEnum() {
        // then
        assertThat(ProfanitySeverity.valueOf("LOW")).isEqualTo(ProfanitySeverity.LOW);
        assertThat(ProfanitySeverity.valueOf("MEDIUM")).isEqualTo(ProfanitySeverity.MEDIUM);
        assertThat(ProfanitySeverity.valueOf("HIGH")).isEqualTo(ProfanitySeverity.HIGH);
    }

    @Test
    @DisplayName("심각도 순서가 LOW, MEDIUM, HIGH 순이다")
    void hasCorrectOrder() {
        // given
        ProfanitySeverity[] values = ProfanitySeverity.values();

        // then
        assertThat(values[0]).isEqualTo(ProfanitySeverity.LOW);
        assertThat(values[1]).isEqualTo(ProfanitySeverity.MEDIUM);
        assertThat(values[2]).isEqualTo(ProfanitySeverity.HIGH);
    }
}
