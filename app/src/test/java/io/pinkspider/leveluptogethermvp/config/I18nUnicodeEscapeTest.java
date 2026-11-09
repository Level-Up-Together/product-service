package io.pinkspider.leveluptogethermvp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * LUT-637: 탑 오류 문구(ja·ar) 23줄이 유니코드 이스케이프 앞에 백슬래시가 하나 더 붙은 채 저장돼, 일본어·아랍어 유저에게 글자 대신 이스케이프 코드가 그대로
 * 보였다(LUT-629·633·635 에서 생긴 줄). properties 로 읽은 값에 "백슬래시 + u + 16진 4자리" 가 남으면 같은 실수다 — 모든 번들을 실제로
 * 읽어 막는다.
 */
class I18nUnicodeEscapeTest {

    @ParameterizedTest(name = "{0}_*.properties 값에 유니코드 이스케이프 리터럴이 남지 않는다")
    @ValueSource(strings = {"errors", "notifications", "messages"})
    @DisplayName("i18n 번들 값에 이중 이스케이프가 없다")
    void noLiteralUnicodeEscapes(String bundle) throws IOException {
        Resource[] files =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath:i18n/" + bundle + "_*.properties");
        assertThat(files).isNotEmpty();
        List<String> broken = new ArrayList<>();
        for (Resource file : files) {
            Properties properties = new Properties();
            try (InputStream in = file.getInputStream()) {
                properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            for (String key : properties.stringPropertyNames()) {
                if (properties.getProperty(key).matches("(?s).*\\\\u[0-9a-fA-F]{4}.*")) {
                    broken.add(file.getFilename() + " " + key);
                }
            }
        }
        assertThat(broken).isEmpty();
    }
}
