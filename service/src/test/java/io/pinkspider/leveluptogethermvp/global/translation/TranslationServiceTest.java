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
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("TranslationService 테스트")
class TranslationServiceTest {

    @Mock private GoogleTranslationFeignClient translationClient;

    @Mock private ContentTranslationRepository translationRepository;

    @Mock private RedisTemplate<String, Object> redisTemplate;

    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks private TranslationService translationService;

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
            TranslationInfo result =
                    translationService.translateContent(ContentType.FEED, 1L, content, "en");

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
            TranslationInfo result =
                    translationService.translateContent(
                            ContentType.FEED, 1L, content, "xx"); // 지원하지 않는 언어

            // then
            assertThat(result.isTranslated()).isFalse();
            verify(translationClient, never()).translate(anyString(), any());
        }

        @Test
        @DisplayName("짧은 텍스트는 번역하지 않음")
        void shouldNotTranslateShortText() {
            // given
            String shortContent = "짧은글"; // 10자 미만

            // when
            TranslationInfo result =
                    translationService.translateContent(ContentType.FEED, 1L, shortContent, "en");

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
            TranslationInfo result =
                    translationService.translateContent(ContentType.FEED, 1L, content, "en");

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
            when(valueOperations.get(anyString())).thenReturn(null); // Redis 캐시 미스

            ContentTranslation cachedEntity =
                    ContentTranslation.builder()
                            .contentType(ContentType.FEED)
                            .contentId(1L)
                            .fieldName("content")
                            .sourceLocale("ko")
                            .targetLocale("en")
                            .translatedText(dbTranslation)
                            .build();

            when(translationRepository
                            .findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(cachedEntity));

            // when
            TranslationInfo result =
                    translationService.translateContent(ContentType.FEED, 1L, content, "en");

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
            when(valueOperations.get(anyString())).thenReturn(null); // Redis 캐시 미스
            when(translationRepository
                            .findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty()); // DB 캐시 미스

            GoogleTranslationResponse.Translation translation =
                    new GoogleTranslationResponse.Translation(translatedText, "ko");
            GoogleTranslationResponse.TranslationData data =
                    new GoogleTranslationResponse.TranslationData(List.of(translation));
            GoogleTranslationResponse response = new GoogleTranslationResponse(data);

            when(translationClient.translate(
                            eq("test-api-key"), any(GoogleTranslationRequest.class)))
                    .thenReturn(response);

            when(translationRepository.findByContentTypeAndContentIdAndFieldNameAndTargetLocale(
                            any(), any(), any(), any()))
                    .thenReturn(Optional.empty());

            // when
            TranslationInfo result =
                    translationService.translateContent(ContentType.FEED, 1L, content, "en");

            // then
            assertThat(result.isTranslated()).isTrue();
            assertThat(result.getContent()).isEqualTo(translatedText);
            verify(translationClient)
                    .translate(eq("test-api-key"), any(GoogleTranslationRequest.class));
        }

        @Test
        @DisplayName("내용이 null이면 번역하지 않음")
        void shouldNotTranslateNullContent() {
            TranslationInfo result =
                    translationService.translateContent(ContentType.FEED, 1L, null, "en");

            assertThat(result.isTranslated()).isFalse();
            verify(translationClient, never()).translate(anyString(), any());
        }

        @Test
        @DisplayName("제목이 있으면 제목과 내용을 모두 번역")
        void shouldTranslateTitleAndContent() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationRepository
                            .findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(translationRepository.findByContentTypeAndContentIdAndFieldNameAndTargetLocale(
                            any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(translationClient.translate(
                            eq("test-api-key"), any(GoogleTranslationRequest.class)))
                    .thenAnswer(
                            inv -> {
                                GoogleTranslationRequest req = inv.getArgument(1);
                                String text = req.getQueries().get(0);
                                return new GoogleTranslationResponse(
                                        new GoogleTranslationResponse.TranslationData(
                                                List.of(
                                                        new GoogleTranslationResponse.Translation(
                                                                "T:" + text, "ko"))));
                            });

            // when
            TranslationInfo result =
                    translationService.translateContent(
                            ContentType.GUILD_POST, 1L, "제목입니다", "이것은 테스트 콘텐츠입니다.", "en");

            // then
            assertThat(result.isTranslated()).isTrue();
            assertThat(result.getTitle()).isEqualTo("T:제목입니다");
            assertThat(result.getContent()).isEqualTo("T:이것은 테스트 콘텐츠입니다.");
            verify(translationClient, org.mockito.Mockito.times(2))
                    .translate(eq("test-api-key"), any(GoogleTranslationRequest.class));
        }

        @Test
        @DisplayName("제목이 공백이면 제목 번역을 건너뜀")
        void shouldSkipBlankTitle() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn("Cached content.");

            // when
            TranslationInfo result =
                    translationService.translateContent(
                            ContentType.GUILD_POST, 1L, "   ", "이것은 테스트 콘텐츠입니다.", "en");

            // then
            assertThat(result.isTranslated()).isTrue();
            assertThat(result.getTitle()).isNull();
            verify(valueOperations, org.mockito.Mockito.times(1)).get(anyString());
        }

        @Test
        @DisplayName("번역 결과가 원문과 동일하면 notTranslated 반환")
        void shouldReturnNotTranslatedWhenSameAsOriginal() {
            // given
            String content = "This is already English text.";
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(content);

            // when
            TranslationInfo result =
                    translationService.translateContent(ContentType.FEED, 1L, content, "en");

            // then
            assertThat(result.isTranslated()).isFalse();
        }

        @Test
        @DisplayName("API Key가 비어 있으면 번역 실패로 notTranslated 반환")
        void shouldFallbackWhenApiKeyBlank() {
            // given
            TestReflectionUtils.setField(translationService, "apiKey", "  ");
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationRepository
                            .findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());

            // when
            TranslationInfo result =
                    translationService.translateContent(
                            ContentType.FEED, 1L, "이것은 테스트 콘텐츠입니다.", "en");

            // then
            assertThat(result.isTranslated()).isFalse();
            verify(translationClient, never()).translate(anyString(), any());
        }

        @Test
        @DisplayName("API Key가 null이면 번역 실패로 notTranslated 반환")
        void shouldFallbackWhenApiKeyNull() {
            // given
            TestReflectionUtils.setField(translationService, "apiKey", null);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationRepository
                            .findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());

            // when
            TranslationInfo result =
                    translationService.translateContent(
                            ContentType.FEED, 1L, "이것은 테스트 콘텐츠입니다.", "en");

            // then
            assertThat(result.isTranslated()).isFalse();
            verify(translationClient, never()).translate(anyString(), any());
        }

        @Test
        @DisplayName("Google 응답에 번역 결과가 없으면 notTranslated 반환")
        void shouldFallbackWhenGoogleReturnsNoTranslation() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationRepository
                            .findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(translationClient.translate(
                            eq("test-api-key"), any(GoogleTranslationRequest.class)))
                    .thenReturn(new GoogleTranslationResponse(null));

            // when
            TranslationInfo result =
                    translationService.translateContent(
                            ContentType.FEED, 1L, "이것은 테스트 콘텐츠입니다.", "en");

            // then
            assertThat(result.isTranslated()).isFalse();
            verify(translationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Redis 조회·저장 예외는 삼키고 진행")
        void shouldIgnoreRedisFailures() {
            // given
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));
            ContentTranslation cachedEntity =
                    ContentTranslation.builder()
                            .contentType(ContentType.FEED)
                            .contentId(1L)
                            .fieldName("content")
                            .sourceLocale("ko")
                            .targetLocale("en")
                            .translatedText("From DB.")
                            .build();
            when(translationRepository
                            .findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(cachedEntity));

            // when
            TranslationInfo result =
                    translationService.translateContent(
                            ContentType.FEED, 1L, "이것은 테스트 콘텐츠입니다.", "en");

            // then
            assertThat(result.isTranslated()).isTrue();
            assertThat(result.getContent()).isEqualTo("From DB.");
        }
    }

    @Nested
    @DisplayName("saveTranslationCache / deleteTranslationCache")
    class CacheMaintenanceTest {

        @Test
        @DisplayName("기존 DB 캐시가 있으면 갱신한다")
        void shouldUpdateExistingTranslation() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            ContentTranslation existing =
                    ContentTranslation.builder()
                            .contentType(ContentType.FEED)
                            .contentId(1L)
                            .fieldName("content")
                            .sourceLocale("ko")
                            .targetLocale("en")
                            .originalHash("old")
                            .translatedText("Old.")
                            .build();
            when(translationRepository.findByContentTypeAndContentIdAndFieldNameAndTargetLocale(
                            ContentType.FEED, 1L, "content", "en"))
                    .thenReturn(Optional.of(existing));

            // when
            translationService.saveTranslationCache(
                    ContentType.FEED, 1L, "content", "en", "new", "New.");

            // then
            assertThat(existing.getOriginalHash()).isEqualTo("new");
            assertThat(existing.getTranslatedText()).isEqualTo("New.");
            verify(translationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Redis 저장 실패는 삼키고 DB 저장은 수행한다")
        void shouldSaveDbEvenIfRedisFails() {
            // given
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));
            when(translationRepository.findByContentTypeAndContentIdAndFieldNameAndTargetLocale(
                            any(), any(), any(), any()))
                    .thenReturn(Optional.empty());

            // when
            translationService.saveTranslationCache(
                    ContentType.FEED, 1L, "content", "en", "hash", "Text.");

            // then
            verify(translationRepository).save(any(ContentTranslation.class));
        }

        @Test
        @DisplayName("콘텐츠 삭제 시 DB 와 모든 언어의 Redis 키를 삭제한다")
        void shouldDeleteDbAndRedisKeysForAllLocales() {
            // when
            translationService.deleteTranslationCache(ContentType.FEED, 7L);

            // then
            verify(translationRepository).deleteByContentTypeAndContentId(ContentType.FEED, 7L);
            verify(redisTemplate, org.mockito.Mockito.times(SupportedLocale.values().length * 2))
                    .delete(anyString());
            verify(redisTemplate).delete("translation:FEED:7:title:en");
            verify(redisTemplate).delete("translation:FEED:7:content:ja");
        }

        @Test
        @DisplayName("Redis 삭제 실패는 삼키고 나머지 언어도 계속 진행한다")
        void shouldContinueWhenRedisDeleteFails() {
            // given
            when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("redis down"));

            // when
            translationService.deleteTranslationCache(ContentType.FEED, 7L);

            // then
            verify(redisTemplate, org.mockito.Mockito.times(SupportedLocale.values().length))
                    .delete(anyString());
        }
    }

    @Nested
    @DisplayName("translateContents 배치 메서드")
    class TranslateContentsBatchTest {

        @Test
        @DisplayName("번역이 비활성화되면 빈 Map 반환")
        void shouldReturnEmptyMapWhenDisabled() {
            // given
            TestReflectionUtils.setField(translationService, "translationEnabled", false);

            // when
            Map<Long, TranslationInfo> result =
                    translationService.translateContents(
                            ContentType.FEED,
                            List.of(new TranslationService.BatchItem(1L, null, "이것은 테스트 콘텐츠입니다.")),
                            "en");

            // then
            assertThat(result).isEmpty();
            verify(translationClient, never()).translate(anyString(), any());
        }

        @Test
        @DisplayName("캐시 미스 항목들을 모아 Google API 를 1회만 호출")
        void shouldCallGoogleApiOncePerBatch() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationRepository
                            .findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(translationRepository.findByContentTypeAndContentIdAndFieldNameAndTargetLocale(
                            any(), any(), any(), any()))
                    .thenReturn(Optional.empty());

            GoogleTranslationResponse response =
                    new GoogleTranslationResponse(
                            new GoogleTranslationResponse.TranslationData(
                                    List.of(
                                            new GoogleTranslationResponse.Translation(
                                                    "First translated.", "ko"),
                                            new GoogleTranslationResponse.Translation(
                                                    "Second translated.", "ko"))));
            when(translationClient.translate(
                            eq("test-api-key"), any(GoogleTranslationRequest.class)))
                    .thenReturn(response);

            // when
            Map<Long, TranslationInfo> result =
                    translationService.translateContents(
                            ContentType.FEED,
                            List.of(
                                    new TranslationService.BatchItem(1L, null, "첫 번째 피드 내용입니다."),
                                    new TranslationService.BatchItem(2L, null, "두 번째 피드 내용입니다.")),
                            "en");

            // then
            assertThat(result.get(1L).isTranslated()).isTrue();
            assertThat(result.get(1L).getContent()).isEqualTo("First translated.");
            assertThat(result.get(2L).getContent()).isEqualTo("Second translated.");

            ArgumentCaptor<GoogleTranslationRequest> captor =
                    ArgumentCaptor.forClass(GoogleTranslationRequest.class);
            verify(translationClient).translate(eq("test-api-key"), captor.capture());
            assertThat(captor.getValue().getQueries())
                    .containsExactly("첫 번째 피드 내용입니다.", "두 번째 피드 내용입니다.");
        }

        @Test
        @DisplayName("캐시에 있는 항목은 Google 호출 없이 반환")
        void shouldUseCacheWithoutApiCall() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn("Cached translation.");

            // when
            Map<Long, TranslationInfo> result =
                    translationService.translateContents(
                            ContentType.FEED,
                            List.of(new TranslationService.BatchItem(1L, null, "이것은 테스트 콘텐츠입니다.")),
                            "en");

            // then
            assertThat(result.get(1L).isTranslated()).isTrue();
            assertThat(result.get(1L).getContent()).isEqualTo("Cached translation.");
            verify(translationClient, never()).translate(anyString(), any());
        }

        @Test
        @DisplayName("Google API 실패 시 미스 항목은 notTranslated 로 폴백")
        void shouldFallbackToNotTranslatedOnApiFailure() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationRepository
                            .findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(translationClient.translate(anyString(), any(GoogleTranslationRequest.class)))
                    .thenThrow(new RuntimeException("API 오류"));

            // when
            Map<Long, TranslationInfo> result =
                    translationService.translateContents(
                            ContentType.FEED,
                            List.of(new TranslationService.BatchItem(1L, null, "이것은 테스트 콘텐츠입니다.")),
                            "en");

            // then
            assertThat(result.get(1L).isTranslated()).isFalse();
        }

        @Test
        @DisplayName("짧은 내용은 번역 대상에서 제외되고 notTranslated 로 반환")
        void shouldSkipShortContent() {
            // when
            Map<Long, TranslationInfo> result =
                    translationService.translateContents(
                            ContentType.FEED,
                            List.of(new TranslationService.BatchItem(1L, null, "짧은글")),
                            "en");

            // then
            assertThat(result.get(1L).isTranslated()).isFalse();
            verify(translationClient, never()).translate(anyString(), any());
        }

        @Test
        @DisplayName("지원하지 않는 언어면 빈 Map 반환")
        void shouldReturnEmptyMapForUnsupportedLocale() {
            Map<Long, TranslationInfo> result =
                    translationService.translateContents(
                            ContentType.FEED,
                            List.of(new TranslationService.BatchItem(1L, null, "이것은 테스트 콘텐츠입니다.")),
                            "xx");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("항목이 비어 있으면 빈 Map 반환")
        void shouldReturnEmptyMapForEmptyItems() {
            Map<Long, TranslationInfo> result =
                    translationService.translateContents(ContentType.FEED, List.of(), "en");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("내용이 null 인 항목은 notTranslated 로 반환")
        void shouldSkipNullContent() {
            Map<Long, TranslationInfo> result =
                    translationService.translateContents(
                            ContentType.FEED,
                            List.of(new TranslationService.BatchItem(1L, "제목", null)),
                            "en");

            assertThat(result.get(1L).isTranslated()).isFalse();
            verify(translationClient, never()).translate(anyString(), any());
        }

        @Test
        @DisplayName("제목이 캐시에 있으면 제목은 캐시를 쓰고 내용만 Google 에 요청")
        void shouldUseCachedTitleAndTranslateContent() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("translation:GUILD_POST:1:title:en"))
                    .thenReturn("Cached title");
            when(valueOperations.get("translation:GUILD_POST:1:content:en")).thenReturn(null);
            when(translationRepository
                            .findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(translationRepository.findByContentTypeAndContentIdAndFieldNameAndTargetLocale(
                            any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(translationClient.translate(
                            eq("test-api-key"), any(GoogleTranslationRequest.class)))
                    .thenReturn(
                            new GoogleTranslationResponse(
                                    new GoogleTranslationResponse.TranslationData(
                                            List.of(
                                                    new GoogleTranslationResponse.Translation(
                                                            "Translated content.", "ko")))));

            // when
            Map<Long, TranslationInfo> result =
                    translationService.translateContents(
                            ContentType.GUILD_POST,
                            List.of(
                                    new TranslationService.BatchItem(
                                            1L, "제목입니다", "이것은 테스트 콘텐츠입니다.")),
                            "en");

            // then
            assertThat(result.get(1L).isTranslated()).isTrue();
            assertThat(result.get(1L).getTitle()).isEqualTo("Cached title");
            assertThat(result.get(1L).getContent()).isEqualTo("Translated content.");
            ArgumentCaptor<GoogleTranslationRequest> captor =
                    ArgumentCaptor.forClass(GoogleTranslationRequest.class);
            verify(translationClient).translate(eq("test-api-key"), captor.capture());
            assertThat(captor.getValue().getQueries()).containsExactly("이것은 테스트 콘텐츠입니다.");
        }

        @Test
        @DisplayName("제목 미스는 Google 결과를 제목으로 저장하고, 공백 제목은 무시")
        void shouldTranslateMissedTitleAndIgnoreBlankTitle() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationRepository
                            .findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(translationRepository.findByContentTypeAndContentIdAndFieldNameAndTargetLocale(
                            any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(translationClient.translate(
                            eq("test-api-key"), any(GoogleTranslationRequest.class)))
                    .thenReturn(
                            new GoogleTranslationResponse(
                                    new GoogleTranslationResponse.TranslationData(
                                            List.of(
                                                    new GoogleTranslationResponse.Translation(
                                                            "Title one.", "ko"),
                                                    new GoogleTranslationResponse.Translation(
                                                            "Content one.", "ko"),
                                                    new GoogleTranslationResponse.Translation(
                                                            "Content two.", "ko")))));

            // when
            Map<Long, TranslationInfo> result =
                    translationService.translateContents(
                            ContentType.GUILD_POST,
                            List.of(
                                    new TranslationService.BatchItem(
                                            1L, "제목 하나", "첫 번째 게시글 내용입니다."),
                                    new TranslationService.BatchItem(2L, "   ", "두 번째 게시글 내용입니다.")),
                            "en");

            // then
            assertThat(result.get(1L).getTitle()).isEqualTo("Title one.");
            assertThat(result.get(1L).getContent()).isEqualTo("Content one.");
            assertThat(result.get(2L).getTitle()).isNull();
            assertThat(result.get(2L).getContent()).isEqualTo("Content two.");
            ArgumentCaptor<GoogleTranslationRequest> captor =
                    ArgumentCaptor.forClass(GoogleTranslationRequest.class);
            verify(translationClient).translate(eq("test-api-key"), captor.capture());
            assertThat(captor.getValue().getQueries())
                    .containsExactly("제목 하나", "첫 번째 게시글 내용입니다.", "두 번째 게시글 내용입니다.");
        }

        @Test
        @DisplayName("Google 결과가 null 이거나 개수가 부족하면 해당 항목은 notTranslated")
        void shouldHandleNullAndMissingTranslatedTexts() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationRepository
                            .findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            List<GoogleTranslationResponse.Translation> translations = new java.util.ArrayList<>();
            translations.add(new GoogleTranslationResponse.Translation(null, "ko"));
            when(translationClient.translate(
                            eq("test-api-key"), any(GoogleTranslationRequest.class)))
                    .thenReturn(
                            new GoogleTranslationResponse(
                                    new GoogleTranslationResponse.TranslationData(translations)));

            // when
            Map<Long, TranslationInfo> result =
                    translationService.translateContents(
                            ContentType.FEED,
                            List.of(
                                    new TranslationService.BatchItem(1L, null, "첫 번째 피드 내용입니다."),
                                    new TranslationService.BatchItem(2L, null, "두 번째 피드 내용입니다.")),
                            "en");

            // then
            assertThat(result.get(1L).isTranslated()).isFalse();
            assertThat(result.get(2L).isTranslated()).isFalse();
            verify(translationRepository, never()).save(any());
        }

        @Test
        @DisplayName("번역 결과가 원문과 같은 항목은 notTranslated")
        void shouldReturnNotTranslatedWhenSameAsOriginalInBatch() {
            // given
            String content = "This is already English text.";
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(content);

            // when
            Map<Long, TranslationInfo> result =
                    translationService.translateContents(
                            ContentType.FEED,
                            List.of(new TranslationService.BatchItem(1L, null, content)),
                            "en");

            // then
            assertThat(result.get(1L).isTranslated()).isFalse();
        }

        @Test
        @DisplayName("API Key 가 없으면 Google 호출 없이 notTranslated 로 폴백")
        void shouldFallbackWhenApiKeyMissingInBatch() {
            // given
            TestReflectionUtils.setField(translationService, "apiKey", "");
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationRepository
                            .findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());

            // when
            Map<Long, TranslationInfo> result =
                    translationService.translateContents(
                            ContentType.FEED,
                            List.of(new TranslationService.BatchItem(1L, null, "이것은 테스트 콘텐츠입니다.")),
                            "en");

            // then
            assertThat(result.get(1L).isTranslated()).isFalse();
            verify(translationClient, never()).translate(anyString(), any());
        }

        @Test
        @DisplayName("API Key 가 null 이면 Google 호출 없이 notTranslated 로 폴백")
        void shouldFallbackWhenApiKeyNullInBatch() {
            // given
            TestReflectionUtils.setField(translationService, "apiKey", null);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationRepository
                            .findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());

            // when
            Map<Long, TranslationInfo> result =
                    translationService.translateContents(
                            ContentType.FEED,
                            List.of(new TranslationService.BatchItem(1L, null, "이것은 테스트 콘텐츠입니다.")),
                            "en");

            // then
            assertThat(result.get(1L).isTranslated()).isFalse();
            verify(translationClient, never()).translate(anyString(), any());
        }

        @Test
        @DisplayName("미스가 100건을 넘으면 청크 단위로 Google 을 여러 번 호출")
        void shouldChunkLargeBatches() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationRepository
                            .findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(translationRepository.findByContentTypeAndContentIdAndFieldNameAndTargetLocale(
                            any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(translationClient.translate(
                            eq("test-api-key"), any(GoogleTranslationRequest.class)))
                    .thenAnswer(
                            inv -> {
                                GoogleTranslationRequest req = inv.getArgument(1);
                                List<GoogleTranslationResponse.Translation> list =
                                        req.getQueries().stream()
                                                .map(
                                                        q ->
                                                                new GoogleTranslationResponse
                                                                        .Translation(
                                                                        "T:" + q, "ko"))
                                                .toList();
                                return new GoogleTranslationResponse(
                                        new GoogleTranslationResponse.TranslationData(list));
                            });
            List<TranslationService.BatchItem> items = new java.util.ArrayList<>();
            for (long i = 1; i <= 101; i++) {
                items.add(new TranslationService.BatchItem(i, null, "피드 내용 번호 " + i + " 입니다."));
            }

            // when
            Map<Long, TranslationInfo> result =
                    translationService.translateContents(ContentType.FEED, items, "en");

            // then
            assertThat(result).hasSize(101);
            assertThat(result.get(101L).getContent()).isEqualTo("T:피드 내용 번호 101 입니다.");
            verify(translationClient, org.mockito.Mockito.times(2))
                    .translate(eq("test-api-key"), any(GoogleTranslationRequest.class));
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
            assertThat(SupportedLocale.isSupported("ja")).isTrue();
            assertThat(SupportedLocale.isSupported("zh")).isFalse();
        }

        @Test
        @DisplayName("Accept-Language 헤더에서 언어 코드 추출")
        void shouldExtractLanguageFromHeader() {
            assertThat(SupportedLocale.extractLanguageCode("ko-KR,ko;q=0.9,en;q=0.8"))
                    .isEqualTo("ko");
            assertThat(SupportedLocale.extractLanguageCode("en-US,en;q=0.9")).isEqualTo("en");
            assertThat(SupportedLocale.extractLanguageCode("ar-SA,ar;q=0.9")).isEqualTo("ar");
            assertThat(SupportedLocale.extractLanguageCode("ja-JP,ja;q=0.9")).isEqualTo("ja");
            assertThat(SupportedLocale.extractLanguageCode(null)).isEqualTo("en");
            assertThat(SupportedLocale.extractLanguageCode("")).isEqualTo("en");
        }
    }
}
