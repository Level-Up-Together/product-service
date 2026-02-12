package io.pinkspider.leveluptogethermvp.profanity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.profanity.domain.entity.ProfanityWord;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanityCategory;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanitySeverity;
import io.pinkspider.leveluptogethermvp.profanity.infrastructure.ProfanityWordRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfanityValidationService 단위 테스트")
class ProfanityValidationServiceTest {

    @Mock
    private ProfanityWordRepository profanityWordRepository;

    @InjectMocks
    private ProfanityValidationService profanityValidationService;

    private List<ProfanityWord> activeProfanityWords;

    @BeforeEach
    void setUp() {
        activeProfanityWords = List.of(
            createProfanityWord(1L, "금칙어1", ProfanityCategory.GENERAL, ProfanitySeverity.LOW),
            createProfanityWord(2L, "욕설단어", ProfanityCategory.GENERAL, ProfanitySeverity.MEDIUM),
            createProfanityWord(3L, "비속어", ProfanityCategory.GENERAL, ProfanitySeverity.HIGH)
        );
    }

    private ProfanityWord createProfanityWord(Long id, String word, ProfanityCategory category, ProfanitySeverity severity) {
        return ProfanityWord.builder()
            .id(id)
            .word(word)
            .category(category)
            .severity(severity)
            .isActive(true)
            .description("테스트용 금칙어")
            .build();
    }

    @Nested
    @DisplayName("getActiveProfanityWords 테스트")
    class GetActiveProfanityWordsTest {

        @Test
        @DisplayName("활성화된 금칙어 목록을 반환한다")
        void getActiveProfanityWords_returnsActiveWords() {
            // given
            when(profanityWordRepository.findAllByIsActiveTrue()).thenReturn(activeProfanityWords);

            // when
            var result = profanityValidationService.getActiveProfanityWords();

            // then
            assertThat(result).hasSize(3);
            assertThat(result).contains("금칙어1", "욕설단어", "비속어");
        }

        @Test
        @DisplayName("활성화된 금칙어가 없으면 빈 Set을 반환한다")
        void getActiveProfanityWords_returnsEmptySetWhenNoWords() {
            // given
            when(profanityWordRepository.findAllByIsActiveTrue()).thenReturn(List.of());

            // when
            var result = profanityValidationService.getActiveProfanityWords();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("validateContent 테스트")
    class ValidateContentTest {

        @Test
        @DisplayName("금칙어가 포함되지 않은 컨텐츠는 정상 통과한다")
        void validateContent_passesWhenNoProfinaty() {
            // given
            when(profanityWordRepository.findAllByIsActiveTrue()).thenReturn(activeProfanityWords);
            String content = "안녕하세요. 좋은 하루 되세요.";

            // when & then
            profanityValidationService.validateContent(content, "제목");
            // 예외가 발생하지 않으면 성공
        }

        @Test
        @DisplayName("금칙어가 포함된 컨텐츠는 CustomException을 던진다")
        void validateContent_throwsExceptionWhenProfanityDetected() {
            // given
            when(profanityWordRepository.findAllByIsActiveTrue()).thenReturn(activeProfanityWords);
            String content = "이 문장에는 욕설단어가 포함되어 있습니다.";

            // when & then
            assertThatThrownBy(() -> profanityValidationService.validateContent(content, "제목"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("제목에 부적절한 표현이 포함되어 있습니다.");
        }

        @Test
        @DisplayName("null 컨텐츠는 검증을 통과한다")
        void validateContent_passesWhenContentIsNull() {
            // when & then
            profanityValidationService.validateContent(null, "제목");
            // 예외가 발생하지 않으면 성공
        }

        @Test
        @DisplayName("빈 문자열 컨텐츠는 검증을 통과한다")
        void validateContent_passesWhenContentIsEmpty() {
            // when & then
            profanityValidationService.validateContent("", "제목");
            // 예외가 발생하지 않으면 성공
        }

        @Test
        @DisplayName("공백만 있는 컨텐츠는 검증을 통과한다")
        void validateContent_passesWhenContentIsBlank() {
            // when & then
            profanityValidationService.validateContent("   ", "제목");
            // 예외가 발생하지 않으면 성공
        }
    }

    @Nested
    @DisplayName("validateContents 테스트")
    class ValidateContentsTest {

        @Test
        @DisplayName("여러 필드를 한 번에 검증한다")
        void validateContents_validatesMultipleFields() {
            // given
            when(profanityWordRepository.findAllByIsActiveTrue()).thenReturn(activeProfanityWords);
            Map<String, String> contents = new HashMap<>();
            contents.put("제목", "안녕하세요");
            contents.put("설명", "좋은 미션입니다");

            // when & then
            profanityValidationService.validateContents(contents);
            // 예외가 발생하지 않으면 성공
        }

        @Test
        @DisplayName("여러 필드 중 하나라도 금칙어가 있으면 예외를 던진다")
        void validateContents_throwsExceptionWhenAnyFieldContainsProfanity() {
            // given
            when(profanityWordRepository.findAllByIsActiveTrue()).thenReturn(activeProfanityWords);
            Map<String, String> contents = new HashMap<>();
            contents.put("제목", "안녕하세요");
            contents.put("설명", "이것은 비속어가 포함된 설명입니다");

            // when & then
            assertThatThrownBy(() -> profanityValidationService.validateContents(contents))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("설명에 부적절한 표현이 포함되어 있습니다.");
        }

        @Test
        @DisplayName("null Map은 검증을 통과한다")
        void validateContents_passesWhenMapIsNull() {
            // when & then
            profanityValidationService.validateContents(null);
            // 예외가 발생하지 않으면 성공
        }

        @Test
        @DisplayName("빈 Map은 검증을 통과한다")
        void validateContents_passesWhenMapIsEmpty() {
            // when & then
            profanityValidationService.validateContents(new HashMap<>());
            // 예외가 발생하지 않으면 성공
        }
    }

    @Nested
    @DisplayName("containsProfanity 테스트")
    class ContainsProfanityTest {

        @Test
        @DisplayName("금칙어가 포함되면 true를 반환한다")
        void containsProfanity_returnsTrueWhenProfanityDetected() {
            // given
            when(profanityWordRepository.findAllByIsActiveTrue()).thenReturn(activeProfanityWords);
            String content = "이 문장에는 금칙어1이 있습니다.";

            // when
            boolean result = profanityValidationService.containsProfanity(content);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("금칙어가 없으면 false를 반환한다")
        void containsProfanity_returnsFalseWhenNoProfanity() {
            // given
            when(profanityWordRepository.findAllByIsActiveTrue()).thenReturn(activeProfanityWords);
            String content = "이 문장에는 문제가 없습니다.";

            // when
            boolean result = profanityValidationService.containsProfanity(content);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("null 컨텐츠는 false를 반환한다")
        void containsProfanity_returnsFalseWhenContentIsNull() {
            // when
            boolean result = profanityValidationService.containsProfanity(null);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("빈 문자열은 false를 반환한다")
        void containsProfanity_returnsFalseWhenContentIsEmpty() {
            // when
            boolean result = profanityValidationService.containsProfanity("");

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("findProfanityWords 테스트")
    class FindProfanityWordsTest {

        @Test
        @DisplayName("발견된 금칙어 목록을 반환한다")
        void findProfanityWords_returnsDetectedWords() {
            // given
            when(profanityWordRepository.findAllByIsActiveTrue()).thenReturn(activeProfanityWords);
            String content = "이 문장에는 금칙어1과 비속어가 모두 포함되어 있습니다.";

            // when
            List<String> result = profanityValidationService.findProfanityWords(content);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).contains("금칙어1", "비속어");
        }

        @Test
        @DisplayName("금칙어가 없으면 빈 리스트를 반환한다")
        void findProfanityWords_returnsEmptyListWhenNoProfanity() {
            // given
            when(profanityWordRepository.findAllByIsActiveTrue()).thenReturn(activeProfanityWords);
            String content = "정상적인 문장입니다.";

            // when
            List<String> result = profanityValidationService.findProfanityWords(content);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null 컨텐츠는 빈 리스트를 반환한다")
        void findProfanityWords_returnsEmptyListWhenContentIsNull() {
            // when
            List<String> result = profanityValidationService.findProfanityWords(null);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("빈 문자열은 빈 리스트를 반환한다")
        void findProfanityWords_returnsEmptyListWhenContentIsEmpty() {
            // when
            List<String> result = profanityValidationService.findProfanityWords("");

            // then
            assertThat(result).isEmpty();
        }
    }
}
