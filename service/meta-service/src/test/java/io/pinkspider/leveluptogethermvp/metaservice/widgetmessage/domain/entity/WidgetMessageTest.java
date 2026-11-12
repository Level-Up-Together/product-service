package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.enums.WidgetMessageCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("WidgetMessage — 위젯 문장 (LUT-775)")
class WidgetMessageTest {

    private static WidgetMessage message(String ko, String en, String ja, String ar) {
        return WidgetMessage.builder()
                .startHour(6)
                .endHour(12)
                .condition(WidgetMessageCondition.NOT_DONE)
                .textKo(ko)
                .textEn(en)
                .textJa(ja)
                .textAr(ar)
                .active(true)
                .build();
    }

    @Nested
    @DisplayName("textFor — 요청 언어, 비면 en, 그다음 ko")
    class TextFor {

        private final WidgetMessage full = message("한국어", "English", "日本語", "عربي");

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({"ko, 한국어", "en, English", "ja, 日本語", "ar, عربي", "fr, English"})
        void requested(String language, String expected) {
            assertThat(full.textFor(language)).isEqualTo(expected);
        }

        @Test
        @DisplayName("언어가 null 이면 en")
        void nullLanguage() {
            assertThat(full.textFor(null)).isEqualTo("English");
        }

        @Test
        @DisplayName("요청 언어가 비면 en")
        void fallbackToEnglish() {
            WidgetMessage m = message("한국어", "English", " ", null);
            assertThat(m.textFor("ja")).isEqualTo("English");
            assertThat(m.textFor("ar")).isEqualTo("English");
        }

        @Test
        @DisplayName("en 도 비면 ko")
        void fallbackToKorean() {
            WidgetMessage m = message("한국어", null, null, "");
            assertThat(m.textFor("ja")).isEqualTo("한국어");
            assertThat(m.textFor("en")).isEqualTo("한국어");
            assertThat(m.textFor("ar")).isEqualTo("한국어");
        }
    }

    @Test
    @DisplayName("toggleActive 는 노출 여부를 뒤집는다")
    void toggleActive() {
        WidgetMessage m = message("한국어", null, null, null);
        m.toggleActive();
        assertThat(m.isActive()).isFalse();
        m.toggleActive();
        assertThat(m.isActive()).isTrue();
    }

    @Nested
    @DisplayName("WidgetMessageCondition.matches")
    class ConditionMatches {

        @ParameterizedTest(name = "{0}, 오늘 완료={1} → {2}")
        @CsvSource({
            "NOT_DONE, false, true",
            "NOT_DONE, true, false",
            "DONE, false, false",
            "DONE, true, true",
            "ALL, false, true",
            "ALL, true, true"
        })
        void matches(WidgetMessageCondition condition, boolean completedToday, boolean expected) {
            assertThat(condition.matches(completedToday)).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("LUT-797: 배경 이미지 — 작성 때 넣고, 수정으로 바꾸거나 지운다(null = 없음)")
    void backgroundImage() {
        WidgetMessage m =
                WidgetMessage.builder()
                        .startHour(6)
                        .endHour(12)
                        .condition(WidgetMessageCondition.ALL)
                        .textKo("문장")
                        .active(true)
                        .backgroundImageUrl("https://cdn.test/widget/a.png")
                        .build();
        assertThat(m.getBackgroundImageUrl()).isEqualTo("https://cdn.test/widget/a.png");

        m.update(6, 12, WidgetMessageCondition.ALL, "문장", null, null, null, true, 0, null);
        assertThat(m.getBackgroundImageUrl()).isNull();
        assertThat(message("가", null, null, null).getBackgroundImageUrl()).isNull();
    }
}
