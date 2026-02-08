package io.pinkspider.leveluptogethermvp.global.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.test.TestReflectionUtils;

import io.pinkspider.global.translation.GoogleTranslationFeignClient;
import io.pinkspider.global.translation.TranslationService;
import io.pinkspider.global.translation.dto.GoogleTranslationRequest;
import io.pinkspider.global.translation.dto.GoogleTranslationResponse;
import io.pinkspider.global.translation.dto.TranslationInfo;
import io.pinkspider.global.translation.entity.ContentTranslation;
import io.pinkspider.global.translation.enums.ContentType;
import io.pinkspider.global.translation.enums.SupportedLocale;
import io.pinkspider.global.translation.repository.ContentTranslationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("TranslationService 테스트")
class TranslationServiceTest {

    @Mock
    private GoogleTranslationFeignClient translationClient;

    @Mock
    private ContentTranslationRepository translationRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        // translationEnabled 필드를 true로 설정
        TestReflectionUtils.setField(translationService, "translationEnabled", true);
        TestReflectionUtils.setField(translationService, "apiKey", "test-api-key");
    }

    @Nested
    @DisplayName("translateContent 메서드")
    class TranslateContentTest {

        @Test
        @DisplayName("번역이 비활성화되면 번역하지 않음")
        void shouldNotTranslateWhenDisabled() {
            // given
            TestReflectionUtils.setField(translationService, "translationEnabled", false);
            String content = "이것은 테스트 콘텐츠입니다.";

            // when
            TranslationInfo result = translationService.translateContent(
                ContentType.FEED, 1L, content, "en");

            // then
            assertThat(result.isTranslated()).isFalse();
            verify(translationClient, never()).translate(anyString(), any());
        }

        @Test
        @DisplayName("지원하지 않는 언어는 번역하지 않음")
        void shouldNotTranslateUnsupportedLocale() {
            // given
            String content = "이것은 테스트 콘텐츠입니다.";

            // when
            TranslationInfo result = translationService.translateContent(
                ContentType.FEED, 1L, content, "xx");  // 지원하지 않는 언어

            // then
            assertThat(result.isTranslated()).isFalse();
            verify(translationClient, never()).translate(anyString(), any());
        }

        @Test
        @DisplayName("짧은 텍스트는 번역하지 않음")
        void shouldNotTranslateShortText() {
            // given
            String shortContent = "짧은글";  // 10자 미만

            // when
            TranslationInfo result = translationService.translateContent(
                ContentType.FEED, 1L, shortContent, "en");

            // then
            assertThat(result.isTranslated()).isFalse();
            verify(translationClient, never()).translate(anyString(), any());
        }

        @Test
        @DisplayName("Redis 캐시에서 번역 결과를 가져옴")
        void shouldGetTranslationFromRedisCache() {
            // given
            String content = "이것은 테스트 콘텐츠입니다.";
            String cachedTranslation = "This is a test content.";

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(cachedTranslation);

            // when
            TranslationInfo result = translationService.translateContent(
                ContentType.FEED, 1L, content, "en");

            // then
            assertThat(result.isTranslated()).isTrue();
            assertThat(result.getContent()).isEqualTo(cachedTranslation);
            verify(translationClient, never()).translate(anyString(), any());
        }

        @Test
        @DisplayName("DB 캐시에서 번역 결과를 가져옴")
        void shouldGetTranslationFromDbCache() {
            // given
            String content = "이것은 테스트 콘텐츠입니다.";
            String dbTranslation = "This is a test content.";

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);  // Redis 캐시 미스

            ContentTranslation cachedEntity = ContentTranslation.builder()
                .contentType(ContentType.FEED)
                .contentId(1L)
                .fieldName("content")
                .sourceLocale("ko")
                .targetLocale("en")
                .translatedText(dbTranslation)
                .build();

            when(translationRepository.findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                any(), any(), any(), any(), any()
            )).thenReturn(Optional.of(cachedEntity));

            // when
            TranslationInfo result = translationService.translateContent(
                ContentType.FEED, 1L, content, "en");

            // then
            assertThat(result.isTranslated()).isTrue();
            assertThat(result.getContent()).isEqualTo(dbTranslation);
            verify(translationClient, never()).translate(anyString(), any());
        }

        @Test
        @DisplayName("Google API를 호출하여 번역 수행")
        void shouldCallGoogleApiForTranslation() {
            // given
            String content = "이것은 테스트 콘텐츠입니다.";
            String translatedText = "This is a test content.";

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);  // Redis 캐시 미스
            when(translationRepository.findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                any(), any(), any(), any(), any()
            )).thenReturn(Optional.empty());  // DB 캐시 미스

            GoogleTranslationResponse.Translation translation =
                new GoogleTranslationResponse.Translation(translatedText, "ko");
            GoogleTranslationResponse.TranslationData data =
                new GoogleTranslationResponse.TranslationData(List.of(translation));
            GoogleTranslationResponse response = new GoogleTranslationResponse(data);

            when(translationClient.translate(eq("test-api-key"), any(GoogleTranslationRequest.class)))
                .thenReturn(response);

            when(translationRepository.findByContentTypeAndContentIdAndFieldNameAndTargetLocale(
                any(), any(), any(), any()
            )).thenReturn(Optional.empty());

            // when
            TranslationInfo result = translationService.translateContent(
                ContentType.FEED, 1L, content, "en");

            // then
            assertThat(result.isTranslated()).isTrue();
            assertThat(result.getContent()).isEqualTo(translatedText);
            verify(translationClient).translate(eq("test-api-key"), any(GoogleTranslationRequest.class));
        }
    }

    @Nested
    @DisplayName("SupportedLocale 테스트")
    class SupportedLocaleTest {

        @Test
        @DisplayName("지원하는 언어 코드를 올바르게 확인")
        void shouldCheckSupportedLocale() {
            assertThat(SupportedLocale.isSupported("ko")).isTrue();
            assertThat(SupportedLocale.isSupported("en")).isTrue();
            assertThat(SupportedLocale.isSupported("ar")).isTrue();
            assertThat(SupportedLocale.isSupported("ja")).isFalse();
            assertThat(SupportedLocale.isSupported("zh")).isFalse();
        }

        @Test
        @DisplayName("Accept-Language 헤더에서 언어 코드 추출")
        void shouldExtractLanguageFromHeader() {
            assertThat(SupportedLocale.extractLanguageCode("ko-KR,ko;q=0.9,en;q=0.8")).isEqualTo("ko");
            assertThat(SupportedLocale.extractLanguageCode("en-US,en;q=0.9")).isEqualTo("en");
            assertThat(SupportedLocale.extractLanguageCode("ar-SA,ar;q=0.9")).isEqualTo("ar");
            assertThat(SupportedLocale.extractLanguageCode("ja-JP,ja;q=0.9")).isEqualTo("ko");  // 지원하지 않으면 기본값
            assertThat(SupportedLocale.extractLanguageCode(null)).isEqualTo("ko");
            assertThat(SupportedLocale.extractLanguageCode("")).isEqualTo("ko");
        }
    }
}
