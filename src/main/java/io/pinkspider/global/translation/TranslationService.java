package io.pinkspider.global.translation;

import io.pinkspider.global.translation.GoogleTranslationConfig.GoogleTranslationException;
import io.pinkspider.global.translation.dto.GoogleTranslationRequest;
import io.pinkspider.global.translation.dto.GoogleTranslationResponse;
import io.pinkspider.global.translation.dto.TranslationInfo;
import io.pinkspider.global.translation.entity.ContentTranslation;
import io.pinkspider.global.translation.enums.ContentType;
import io.pinkspider.global.translation.enums.SupportedLocale;
import io.pinkspider.global.translation.repository.ContentTranslationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 번역 서비스
 * Google Cloud Translation API를 통한 번역 및 캐싱 처리
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TranslationService {

    private final GoogleTranslationFeignClient translationClient;
    private final ContentTranslationRepository translationRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${google.translation.api.key:}")
    private String apiKey;

    @Value("${google.translation.enabled:false}")
    private boolean translationEnabled;

    /**
     * 최소 번역 대상 텍스트 길이
     */
    private static final int MIN_TEXT_LENGTH = 10;

    /**
     * Redis 캐시 TTL (7일)
     */
    private static final Duration REDIS_CACHE_TTL = Duration.ofDays(7);

    /**
     * Redis 캐시 키 프리픽스
     */
    private static final String CACHE_KEY_PREFIX = "translation:";

    /**
     * 콘텐츠 번역 (제목과 내용 모두)
     *
     * @param contentType 콘텐츠 타입
     * @param contentId 콘텐츠 ID
     * @param title 제목 (nullable)
     * @param content 내용
     * @param targetLocale 대상 언어 코드
     * @return 번역 결과 정보
     */
    public TranslationInfo translateContent(
        ContentType contentType,
        Long contentId,
        String title,
        String content,
        String targetLocale
    ) {
        if (!translationEnabled || !SupportedLocale.isSupported(targetLocale)) {
            return TranslationInfo.notTranslated(SupportedLocale.DEFAULT.getCode());
        }

        // 번역할 내용이 없거나 너무 짧은 경우
        if (content == null || content.length() < MIN_TEXT_LENGTH) {
            return TranslationInfo.notTranslated(SupportedLocale.DEFAULT.getCode());
        }

        try {
            String translatedTitle = null;
            String translatedContent;
            String sourceLocale = SupportedLocale.DEFAULT.getCode();

            // 제목 번역 (있는 경우)
            if (title != null && !title.isBlank()) {
                translatedTitle = translateField(contentType, contentId, "title", title, targetLocale);
            }

            // 내용 번역
            translatedContent = translateField(contentType, contentId, "content", content, targetLocale);

            // 번역이 원문과 동일한 경우 (같은 언어)
            if (translatedContent.equals(content)) {
                return TranslationInfo.notTranslated(sourceLocale);
            }

            return TranslationInfo.translated(translatedTitle, translatedContent, sourceLocale, targetLocale);

        } catch (Exception e) {
            log.error("번역 실패: contentType={}, contentId={}, targetLocale={}, error={}",
                contentType, contentId, targetLocale, e.getMessage());
            return TranslationInfo.notTranslated(SupportedLocale.DEFAULT.getCode());
        }
    }

    /**
     * 단일 내용만 번역 (댓글 등)
     */
    public TranslationInfo translateContent(
        ContentType contentType,
        Long contentId,
        String content,
        String targetLocale
    ) {
        return translateContent(contentType, contentId, null, content, targetLocale);
    }

    /**
     * 개별 필드 번역 (캐시 우선)
     */
    private String translateField(
        ContentType contentType,
        Long contentId,
        String fieldName,
        String originalText,
        String targetLocale
    ) {
        String originalHash = computeHash(originalText);

        // 1. Redis 캐시 확인
        String cachedTranslation = getCachedTranslation(contentType, contentId, fieldName, targetLocale);
        if (cachedTranslation != null) {
            return cachedTranslation;
        }

        // 2. DB 캐시 확인
        Optional<ContentTranslation> dbCache = translationRepository
            .findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
                contentType, contentId, fieldName, targetLocale, originalHash);

        if (dbCache.isPresent()) {
            String translatedText = dbCache.get().getTranslatedText();
            // Redis에 캐시 저장
            cacheTranslation(contentType, contentId, fieldName, targetLocale, translatedText);
            return translatedText;
        }

        // 3. Google API 호출
        String translatedText = callGoogleTranslateApi(originalText, targetLocale);

        // 4. 캐시 저장 (Redis + DB)
        saveTranslationCache(contentType, contentId, fieldName, targetLocale, originalHash, translatedText);

        return translatedText;
    }

    /**
     * Google Translation API 호출
     */
    private String callGoogleTranslateApi(String text, String targetLocale) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GoogleTranslationException("Google Translation API Key가 설정되지 않았습니다.");
        }

        GoogleTranslationRequest request = GoogleTranslationRequest.of(text, targetLocale);
        GoogleTranslationResponse response = translationClient.translate(apiKey, request);

        String translatedText = response.getFirstTranslatedText();
        if (translatedText == null) {
            throw new GoogleTranslationException("번역 결과가 없습니다.");
        }

        log.debug("Google Translation 호출: target={}, originalLength={}, translatedLength={}",
            targetLocale, text.length(), translatedText.length());

        return translatedText;
    }

    /**
     * Redis 캐시에서 번역 조회
     */
    private String getCachedTranslation(ContentType contentType, Long contentId, String fieldName, String targetLocale) {
        String cacheKey = buildCacheKey(contentType, contentId, fieldName, targetLocale);
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Redis 캐시 히트: key={}", cacheKey);
                return cached.toString();
            }
        } catch (Exception e) {
            log.warn("Redis 캐시 조회 실패: key={}, error={}", cacheKey, e.getMessage());
        }
        return null;
    }

    /**
     * Redis 캐시에 번역 저장
     */
    private void cacheTranslation(ContentType contentType, Long contentId, String fieldName, String targetLocale, String translatedText) {
        String cacheKey = buildCacheKey(contentType, contentId, fieldName, targetLocale);
        try {
            redisTemplate.opsForValue().set(cacheKey, translatedText, REDIS_CACHE_TTL);
            log.debug("Redis 캐시 저장: key={}", cacheKey);
        } catch (Exception e) {
            log.warn("Redis 캐시 저장 실패: key={}, error={}", cacheKey, e.getMessage());
        }
    }

    /**
     * 번역 캐시 저장 (Redis + DB)
     */
    @Transactional(transactionManager = "metaTransactionManager")
    public void saveTranslationCache(
        ContentType contentType,
        Long contentId,
        String fieldName,
        String targetLocale,
        String originalHash,
        String translatedText
    ) {
        // Redis 저장
        cacheTranslation(contentType, contentId, fieldName, targetLocale, translatedText);

        // DB 저장 (upsert)
        Optional<ContentTranslation> existing = translationRepository
            .findByContentTypeAndContentIdAndFieldNameAndTargetLocale(
                contentType, contentId, fieldName, targetLocale);

        if (existing.isPresent()) {
            existing.get().updateTranslation(originalHash, translatedText);
        } else {
            ContentTranslation translation = ContentTranslation.builder()
                .contentType(contentType)
                .contentId(contentId)
                .fieldName(fieldName)
                .sourceLocale(SupportedLocale.DEFAULT.getCode())
                .targetLocale(targetLocale)
                .originalHash(originalHash)
                .translatedText(translatedText)
                .build();
            translationRepository.save(translation);
        }
    }

    /**
     * 콘텐츠 삭제 시 번역 캐시 삭제
     */
    @Transactional(transactionManager = "metaTransactionManager")
    public void deleteTranslationCache(ContentType contentType, Long contentId) {
        // DB 삭제
        translationRepository.deleteByContentTypeAndContentId(contentType, contentId);

        // Redis 캐시 삭제 (모든 언어)
        for (SupportedLocale locale : SupportedLocale.values()) {
            String cacheKeyPattern = buildCacheKey(contentType, contentId, "*", locale.getCode());
            try {
                // 패턴 매칭으로 관련 키 삭제
                redisTemplate.delete(cacheKeyPattern.replace("*", "title"));
                redisTemplate.delete(cacheKeyPattern.replace("*", "content"));
            } catch (Exception e) {
                log.warn("Redis 캐시 삭제 실패: pattern={}, error={}", cacheKeyPattern, e.getMessage());
            }
        }
    }

    /**
     * Redis 캐시 키 생성
     * 형식: translation:{contentType}:{contentId}:{fieldName}:{targetLocale}
     */
    private String buildCacheKey(ContentType contentType, Long contentId, String fieldName, String targetLocale) {
        return CACHE_KEY_PREFIX + contentType.name() + ":" + contentId + ":" + fieldName + ":" + targetLocale;
    }

    /**
     * SHA-256 해시 계산
     */
    private String computeHash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
