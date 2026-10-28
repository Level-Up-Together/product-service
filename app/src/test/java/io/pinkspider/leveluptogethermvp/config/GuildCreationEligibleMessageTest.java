package io.pinkspider.leveluptogethermvp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.NumberFormat;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * LUT-626: 길드 창설 가능 알림 본문에 요구 레벨이 리터럴(옛 기준 20)로 박혀 있어, 실제로는 레벨 2 에 도달했는데 "레벨 20에 도달하여" 로 나갔다. 본문은
 * 도달 레벨을 {@code {0}} 으로 받아야 한다 — 4개 언어 번들을 실제 {@code MessageConfig} 와 같은 설정으로 렌더링해 확인한다.
 */
class GuildCreationEligibleMessageTest {

    private static final String KEY = "notification.guild_creation_eligible.message";

    private static ReloadableResourceBundleMessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasenames("classpath:i18n/notifications");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }

    /** MessageFormat 은 숫자 인자를 locale 숫자 표기로 쓴다 — 아랍어는 아랍 숫자(٢). LEVEL_UP 알림과 같은 동작. */
    private static String localized(int number, Locale locale) {
        return NumberFormat.getInstance(locale).format(number);
    }

    @ParameterizedTest(name = "{0}: 도달 레벨 2 가 문구에 들어가고 리터럴 20 은 없다")
    @ValueSource(strings = {"ko", "en", "ja", "ar"})
    @DisplayName("길드 창설 가능 알림 본문은 도달 레벨을 인자로 받는다")
    void message_usesReachedLevel(String language) {
        Locale locale = Locale.forLanguageTag(language);
        String rendered = messageSource().getMessage(KEY, new Object[] {2}, locale);

        assertThat(rendered)
                .isNotEqualTo(KEY)
                .contains(localized(2, locale))
                .doesNotContain("20")
                .doesNotContain(localized(20, locale));
    }

    @ParameterizedTest(name = "{0}: 요구 레벨이 바뀌면(예: 7) 문구도 따라 바뀐다")
    @ValueSource(strings = {"ko", "en", "ja", "ar"})
    @DisplayName("요구 레벨 설정이 바뀌어도 문구에 리터럴이 남지 않는다")
    void message_followsLevelArgument(String language) {
        Locale locale = Locale.forLanguageTag(language);
        String rendered = messageSource().getMessage(KEY, new Object[] {7}, locale);

        assertThat(rendered).contains(localized(7, locale)).doesNotContain("{0}");
    }
}
