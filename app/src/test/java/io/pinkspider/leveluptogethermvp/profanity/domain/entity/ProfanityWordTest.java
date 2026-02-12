package io.pinkspider.leveluptogethermvp.profanity.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanityCategory;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanitySeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ProfanityWord 엔티티 테스트")
class ProfanityWordTest {

    @Nested
    @DisplayName("빌더 테스트")
    class BuilderTest {

        @Test
        @DisplayName("모든 필드가 정상적으로 설정된다")
        void builder_setsAllFields() {
            // given & when
            ProfanityWord profanityWord = ProfanityWord.builder()
                .id(1L)
                .word("테스트금칙어")
                .category(ProfanityCategory.GENERAL)
                .severity(ProfanitySeverity.MEDIUM)
                .isActive(true)
                .description("테스트용 금칙어입니다")
                .build();

            // then
            assertThat(profanityWord.getId()).isEqualTo(1L);
            assertThat(profanityWord.getWord()).isEqualTo("테스트금칙어");
            assertThat(profanityWord.getCategory()).isEqualTo(ProfanityCategory.GENERAL);
            assertThat(profanityWord.getSeverity()).isEqualTo(ProfanitySeverity.MEDIUM);
            assertThat(profanityWord.getIsActive()).isTrue();
            assertThat(profanityWord.getDescription()).isEqualTo("테스트용 금칙어입니다");
        }

        @Test
        @DisplayName("description 없이도 생성 가능하다")
        void builder_worksWithoutDescription() {
            // given & when
            ProfanityWord profanityWord = ProfanityWord.builder()
                .id(1L)
                .word("테스트")
                .category(ProfanityCategory.SEXUAL)
                .severity(ProfanitySeverity.HIGH)
                .isActive(true)
                .build();

            // then
            assertThat(profanityWord.getWord()).isEqualTo("테스트");
            assertThat(profanityWord.getDescription()).isNull();
        }
    }

    @Nested
    @DisplayName("카테고리별 생성 테스트")
    class CategoryTest {

        @Test
        @DisplayName("GENERAL 카테고리로 생성할 수 있다")
        void createWithGeneralCategory() {
            // given & when
            ProfanityWord profanityWord = ProfanityWord.builder()
                .word("일반욕설")
                .category(ProfanityCategory.GENERAL)
                .severity(ProfanitySeverity.LOW)
                .isActive(true)
                .build();

            // then
            assertThat(profanityWord.getCategory()).isEqualTo(ProfanityCategory.GENERAL);
            assertThat(profanityWord.getCategory().getName()).isEqualTo("일반");
        }

        @Test
        @DisplayName("SEXUAL 카테고리로 생성할 수 있다")
        void createWithSexualCategory() {
            // given & when
            ProfanityWord profanityWord = ProfanityWord.builder()
                .word("성적욕설")
                .category(ProfanityCategory.SEXUAL)
                .severity(ProfanitySeverity.HIGH)
                .isActive(true)
                .build();

            // then
            assertThat(profanityWord.getCategory()).isEqualTo(ProfanityCategory.SEXUAL);
            assertThat(profanityWord.getCategory().getName()).isEqualTo("성적");
        }

        @Test
        @DisplayName("DISCRIMINATION 카테고리로 생성할 수 있다")
        void createWithDiscriminationCategory() {
            // given & when
            ProfanityWord profanityWord = ProfanityWord.builder()
                .word("차별표현")
                .category(ProfanityCategory.DISCRIMINATION)
                .severity(ProfanitySeverity.HIGH)
                .isActive(true)
                .build();

            // then
            assertThat(profanityWord.getCategory()).isEqualTo(ProfanityCategory.DISCRIMINATION);
            assertThat(profanityWord.getCategory().getName()).isEqualTo("차별");
        }

        @Test
        @DisplayName("VIOLENCE 카테고리로 생성할 수 있다")
        void createWithViolenceCategory() {
            // given & when
            ProfanityWord profanityWord = ProfanityWord.builder()
                .word("폭력표현")
                .category(ProfanityCategory.VIOLENCE)
                .severity(ProfanitySeverity.MEDIUM)
                .isActive(true)
                .build();

            // then
            assertThat(profanityWord.getCategory()).isEqualTo(ProfanityCategory.VIOLENCE);
            assertThat(profanityWord.getCategory().getName()).isEqualTo("폭력");
        }

        @Test
        @DisplayName("POLITICS 카테고리로 생성할 수 있다")
        void createWithPoliticsCategory() {
            // given & when
            ProfanityWord profanityWord = ProfanityWord.builder()
                .word("정치표현")
                .category(ProfanityCategory.POLITICS)
                .severity(ProfanitySeverity.LOW)
                .isActive(true)
                .build();

            // then
            assertThat(profanityWord.getCategory()).isEqualTo(ProfanityCategory.POLITICS);
            assertThat(profanityWord.getCategory().getName()).isEqualTo("정치");
        }
    }

    @Nested
    @DisplayName("심각도별 생성 테스트")
    class SeverityTest {

        @Test
        @DisplayName("LOW 심각도로 생성할 수 있다")
        void createWithLowSeverity() {
            // given & when
            ProfanityWord profanityWord = ProfanityWord.builder()
                .word("경미한욕설")
                .category(ProfanityCategory.GENERAL)
                .severity(ProfanitySeverity.LOW)
                .isActive(true)
                .build();

            // then
            assertThat(profanityWord.getSeverity()).isEqualTo(ProfanitySeverity.LOW);
            assertThat(profanityWord.getSeverity().getName()).isEqualTo("낮음");
        }

        @Test
        @DisplayName("MEDIUM 심각도로 생성할 수 있다")
        void createWithMediumSeverity() {
            // given & when
            ProfanityWord profanityWord = ProfanityWord.builder()
                .word("중간욕설")
                .category(ProfanityCategory.GENERAL)
                .severity(ProfanitySeverity.MEDIUM)
                .isActive(true)
                .build();

            // then
            assertThat(profanityWord.getSeverity()).isEqualTo(ProfanitySeverity.MEDIUM);
            assertThat(profanityWord.getSeverity().getName()).isEqualTo("중간");
        }

        @Test
        @DisplayName("HIGH 심각도로 생성할 수 있다")
        void createWithHighSeverity() {
            // given & when
            ProfanityWord profanityWord = ProfanityWord.builder()
                .word("심각한욕설")
                .category(ProfanityCategory.GENERAL)
                .severity(ProfanitySeverity.HIGH)
                .isActive(true)
                .build();

            // then
            assertThat(profanityWord.getSeverity()).isEqualTo(ProfanitySeverity.HIGH);
            assertThat(profanityWord.getSeverity().getName()).isEqualTo("높음");
        }
    }

    @Nested
    @DisplayName("활성화 상태 테스트")
    class ActiveStatusTest {

        @Test
        @DisplayName("활성화 상태로 생성할 수 있다")
        void createWithActiveStatus() {
            // given & when
            ProfanityWord profanityWord = ProfanityWord.builder()
                .word("활성금칙어")
                .category(ProfanityCategory.GENERAL)
                .severity(ProfanitySeverity.LOW)
                .isActive(true)
                .build();

            // then
            assertThat(profanityWord.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("비활성화 상태로 생성할 수 있다")
        void createWithInactiveStatus() {
            // given & when
            ProfanityWord profanityWord = ProfanityWord.builder()
                .word("비활성금칙어")
                .category(ProfanityCategory.GENERAL)
                .severity(ProfanitySeverity.LOW)
                .isActive(false)
                .build();

            // then
            assertThat(profanityWord.getIsActive()).isFalse();
        }
    }
}
