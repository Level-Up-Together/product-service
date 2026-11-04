package io.pinkspider.leveluptogethermvp.profanity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.profanity.domain.entity.ProfanityWord;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanitySeverity;
import io.pinkspider.leveluptogethermvp.profanity.infrastructure.ProfanityWordRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfanityValidationService.getActiveProfanityWordsWithSeverity (LUT-633)")
class ProfanityValidationServiceSeverityTest {
    @Mock private ProfanityWordRepository repository;
    @InjectMocks private ProfanityValidationService service;

    private static ProfanityWord word(String text, ProfanitySeverity severity) {
        ProfanityWord w = mock(ProfanityWord.class);
        when(w.getWord()).thenReturn(text);
        if (text != null && !text.isBlank()) {
            when(w.getSeverity()).thenReturn(severity);
        }
        return w;
    }

    @Test
    void mapsWordToSeverityName() {
        List<ProfanityWord> words =
                List.of(
                        word("바보", ProfanitySeverity.LOW),
                        word("x", null),
                        word(" ", ProfanitySeverity.HIGH),
                        word("shit", ProfanitySeverity.HIGH));
        when(repository.findAllByIsActiveTrue()).thenReturn(words);
        Map<String, String> result = service.getActiveProfanityWordsWithSeverity();
        assertThat(result)
                .containsEntry("바보", "LOW")
                .containsEntry("x", "MEDIUM")
                .containsEntry("shit", "HIGH")
                .hasSize(3);
    }
}
