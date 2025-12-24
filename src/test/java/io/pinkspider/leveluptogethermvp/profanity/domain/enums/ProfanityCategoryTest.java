package io.pinkspider.leveluptogethermvp.profanity.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProfanityCategory Enum 테스트")
class ProfanityCategoryTest {

    @Test
    @DisplayName("GENERAL 카테고리의 속성이 올바르다")
    void general_hasCorrectProperties() {
        // given
        ProfanityCategory category = ProfanityCategory.GENERAL;

        // then
        assertThat(category.getName()).isEqualTo("일반");
        assertThat(category.getDescription()).isEqualTo("일반적인 욕설");
    }

    @Test
    @DisplayName("SEXUAL 카테고리의 속성이 올바르다")
    void sexual_hasCorrectProperties() {
        // given
        ProfanityCategory category = ProfanityCategory.SEXUAL;

        // then
        assertThat(category.getName()).isEqualTo("성적");
        assertThat(category.getDescription()).isEqualTo("성적인 욕설");
    }

    @Test
    @DisplayName("DISCRIMINATION 카테고리의 속성이 올바르다")
    void discrimination_hasCorrectProperties() {
        // given
        ProfanityCategory category = ProfanityCategory.DISCRIMINATION;

        // then
        assertThat(category.getName()).isEqualTo("차별");
        assertThat(category.getDescription()).isEqualTo("차별적 표현");
    }

    @Test
    @DisplayName("VIOLENCE 카테고리의 속성이 올바르다")
    void violence_hasCorrectProperties() {
        // given
        ProfanityCategory category = ProfanityCategory.VIOLENCE;

        // then
        assertThat(category.getName()).isEqualTo("폭력");
        assertThat(category.getDescription()).isEqualTo("폭력적 표현");
    }

    @Test
    @DisplayName("POLITICS 카테고리의 속성이 올바르다")
    void politics_hasCorrectProperties() {
        // given
        ProfanityCategory category = ProfanityCategory.POLITICS;

        // then
        assertThat(category.getName()).isEqualTo("정치");
        assertThat(category.getDescription()).isEqualTo("정치적 표현");
    }

    @Test
    @DisplayName("총 5개의 카테고리가 존재한다")
    void hasCorrectNumberOfCategories() {
        // then
        assertThat(ProfanityCategory.values()).hasSize(5);
    }

    @Test
    @DisplayName("문자열로부터 enum 값을 조회할 수 있다")
    void valueOf_returnsCorrectEnum() {
        // then
        assertThat(ProfanityCategory.valueOf("GENERAL")).isEqualTo(ProfanityCategory.GENERAL);
        assertThat(ProfanityCategory.valueOf("SEXUAL")).isEqualTo(ProfanityCategory.SEXUAL);
        assertThat(ProfanityCategory.valueOf("DISCRIMINATION")).isEqualTo(ProfanityCategory.DISCRIMINATION);
        assertThat(ProfanityCategory.valueOf("VIOLENCE")).isEqualTo(ProfanityCategory.VIOLENCE);
        assertThat(ProfanityCategory.valueOf("POLITICS")).isEqualTo(ProfanityCategory.POLITICS);
    }
}
