package io.pinkspider.leveluptogethermvp.towerservice.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.profanity.application.ProfanityValidationService;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanitySeverity;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerChatModerationService.ModerationResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("TowerChatModerationService (LUT-633) — 심각도별 가림/거부, 공백·특수문자 우회 방지")
class TowerChatModerationServiceTest {
    @Mock private ProfanityValidationService profanityValidationService;
    @InjectMocks private TowerChatModerationService service;

    @BeforeEach
    void words() {
        Map<String, String> words = new LinkedHashMap<>();
        words.put("바보", "LOW");
        words.put("멍청이", "MEDIUM");
        words.put("shit", "HIGH");
        words.put("이상한값", "WHATEVER");
        words.put("", "LOW");
        when(profanityValidationService.getActiveProfanityWordsWithSeverity()).thenReturn(words);
    }

    @Test
    @DisplayName("깨끗한 문장은 그대로")
    void clean() {
        ModerationResult r = service.moderate("오늘도 레벨업!");
        assertThat(r.content()).isEqualTo("오늘도 레벨업!");
        assertThat(r.masked()).isFalse();
        assertThat(r.rejected()).isFalse();
        assertThat(r.maxSeverity()).isNull();
        assertThat(r.matchedWords()).isEmpty();
        assertThat(service.moderate("   ").rejected()).isFalse();
        assertThat(service.moderate(null).content()).isNull();
    }

    @Test
    @DisplayName("LOW·MEDIUM 은 해당 단어만 ** 로 가리고, 띄어쓰기·특수문자를 섞어도 걸린다")
    void maskLowMedium() {
        ModerationResult r = service.moderate("야 바보야 멍 청 이 같이 굴지마 m.e.l.o");
        assertThat(r.rejected()).isFalse();
        assertThat(r.masked()).isTrue();
        assertThat(r.content()).isEqualTo("야 **야 ** 같이 굴지마 m.e.l.o");
        assertThat(r.maxSeverity()).isEqualTo(ProfanitySeverity.MEDIUM);
        assertThat(r.matchedWords()).containsExactlyInAnyOrder("바보", "멍청이");
        assertThat(service.moderate("바.보").content()).isEqualTo("**");
        assertThat(service.moderate("BABO").masked()).isFalse();
    }

    @Test
    @DisplayName("HIGH 가 하나라도 있으면 전체 거부(가린 문장 없음), 알 수 없는 심각도는 MEDIUM 으로")
    void rejectHigh() {
        ModerationResult r = service.moderate("this is s.h.i.t and 바보");
        assertThat(r.rejected()).isTrue();
        assertThat(r.content()).isNull();
        assertThat(r.maxSeverity()).isEqualTo(ProfanitySeverity.HIGH);
        assertThat(r.matchedWords()).contains("shit", "바보");

        ModerationResult unknown = service.moderate("이상한값이다");
        assertThat(unknown.maxSeverity()).isEqualTo(ProfanitySeverity.MEDIUM);
        assertThat(unknown.content()).isEqualTo("**이다");
    }

    @Test
    @DisplayName("금칙어 목록이 비면 그대로 통과")
    void noWords() {
        when(profanityValidationService.getActiveProfanityWordsWithSeverity()).thenReturn(Map.of());
        assertThat(service.moderate("바보").masked()).isFalse();
        when(profanityValidationService.getActiveProfanityWordsWithSeverity()).thenReturn(null);
        assertThat(service.moderate("바보").masked()).isFalse();
    }

    @Test
    @DisplayName("normalize/mask 보조 함수")
    void helpers() {
        assertThat(TowerChatModerationService.normalize("S.h-I t 바 보!")).isEqualTo("shit바보");
        assertThat(TowerChatModerationService.mask("ab c d", "abc")).isEqualTo("** d");
    }
}
