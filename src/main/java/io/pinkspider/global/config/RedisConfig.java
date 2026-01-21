package io.pinkspider.global.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.pinkspider.global.properties.RedisProperties;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

// redis Lettuce를 이용한다.
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableRedisRepositories
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    private final RedisProperties redisProperties;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setHostName(redisProperties.getHost());
        redisStandaloneConfiguration.setPort(redisProperties.getPort());
        redisStandaloneConfiguration.setPassword(redisProperties.getPassword());
        return new LettuceConnectionFactory(redisStandaloneConfiguration);
    }

    // 기본 redisTemplate (String값만 넣을때 사용)
    @Bean
    @Qualifier("redisTemplateForString")
    public RedisTemplate<String, Object> redisTemplateForString() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory());
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        return redisTemplate;
    }

    // 객체형식처리를 위한 redisTemplate
    @Bean
    @Qualifier("redisTemplateForObject")
    @Primary
    public RedisTemplate<String, Object> redisTemplateForObject() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory());
        redisTemplate.setKeySerializer(new StringRedisSerializer());

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
        return redisTemplate;
    }

    @Bean
    public RedisMessageListenerContainer keyExpirationListenerContainer(RedisConnectionFactory redisConnectionFactory) {
        RedisMessageListenerContainer redisMessageListenerContainer = new RedisMessageListenerContainer();
        redisMessageListenerContainer.setConnectionFactory(redisConnectionFactory());
        return redisMessageListenerContainer;
    }

    @Bean
    public CacheManager redisCacheManager() {
        // JavaTimeModule을 포함한 ObjectMapper 설정 (LocalDateTime 직렬화 지원)
        ObjectMapper cacheObjectMapper = new ObjectMapper();
        cacheObjectMapper.registerModule(new JavaTimeModule());
        cacheObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 타입 정보를 @class 프로퍼티로 저장 (WRAPPER_ARRAY 대신 PROPERTY 사용)
        // 이 방식이 기존 캐시 데이터와 더 호환성이 좋고, 역직렬화 오류가 적음
        cacheObjectMapper.activateDefaultTyping(
                cacheObjectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(cacheObjectMapper)));

        // 캐시별 TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Home 관련 캐시: 5분 TTL
        RedisCacheConfiguration homeConfig = defaultConfig.entryTtl(Duration.ofMinutes(5));
        cacheConfigurations.put("todayPlayers", homeConfig);
        cacheConfigurations.put("todayPlayersByCategory", homeConfig);
        cacheConfigurations.put("mvpGuilds", homeConfig);

        // 시즌 관련 캐시: 10분 TTL (Admin에서 변경 시 즉시 삭제됨)
        RedisCacheConfiguration seasonConfig = defaultConfig.entryTtl(Duration.ofMinutes(10));
        cacheConfigurations.put("currentSeason", seasonConfig);
        cacheConfigurations.put("seasonMvpData", seasonConfig);

        // Mission Category 캐시: 1시간 TTL (카테고리는 자주 변경되지 않음)
        RedisCacheConfiguration categoryConfig = defaultConfig.entryTtl(Duration.ofHours(1));
        cacheConfigurations.put("missionCategories", categoryConfig);
        cacheConfigurations.put("activeMissionCategories", categoryConfig);

        // User Title 캐시: 5분 TTL (칭호 장착 변경 빈도 고려)
        RedisCacheConfiguration titleConfig = defaultConfig.entryTtl(Duration.ofMinutes(5));
        cacheConfigurations.put("userTitleInfo", titleConfig);

        // Friend IDs 캐시: 10분 TTL (친구 관계 변경 빈도 고려)
        RedisCacheConfiguration friendConfig = defaultConfig.entryTtl(Duration.ofMinutes(10));
        cacheConfigurations.put("userFriendIds", friendConfig);

        // User Profile 캐시: 5분 TTL (피드 생성 시 사용)
        RedisCacheConfiguration profileConfig = defaultConfig.entryTtl(Duration.ofMinutes(5));
        cacheConfigurations.put("userProfile", profileConfig);

        // User Exists 캐시: 5분 TTL (JWT 인증 필터에서 사용)
        RedisCacheConfiguration userExistsConfig = defaultConfig.entryTtl(Duration.ofMinutes(5));
        cacheConfigurations.put("userExists", userExistsConfig);

        // Report Under Review 캐시: 1분 TTL (신고 상태는 자주 변경될 수 있음)
        RedisCacheConfiguration reportConfig = defaultConfig.entryTtl(Duration.ofMinutes(1));
        cacheConfigurations.put("reportUnderReview", reportConfig);
        cacheConfigurations.put("reportUnderReviewBatch", reportConfig);

        return RedisCacheManager.RedisCacheManagerBuilder
                .fromConnectionFactory(redisConnectionFactory())
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * 캐시 오류 핸들러 - 역직렬화 오류 등 캐시 관련 예외 발생 시 graceful하게 처리
     * 오류 발생 시 해당 캐시 엔트리를 삭제하고 로그만 남긴 후 원본 메서드를 실행하도록 함
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis 캐시 조회 오류 발생 - cache: {}, key: {}, error: {}",
                        cache.getName(), key, exception.getMessage());
                // 오류가 발생한 캐시 엔트리 삭제 시도
                try {
                    cache.evict(key);
                    log.info("손상된 캐시 엔트리 삭제 완료 - cache: {}, key: {}", cache.getName(), key);
                } catch (Exception e) {
                    log.warn("캐시 엔트리 삭제 실패 - cache: {}, key: {}, error: {}",
                            cache.getName(), key, e.getMessage());
                }
                // 예외를 던지지 않고 null 반환하여 원본 메서드 실행되도록 함
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis 캐시 저장 오류 발생 - cache: {}, key: {}, error: {}",
                        cache.getName(), key, exception.getMessage());
                // 저장 실패 시 예외 무시하고 계속 진행
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis 캐시 삭제 오류 발생 - cache: {}, key: {}, error: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis 캐시 전체 삭제 오류 발생 - cache: {}, error: {}",
                        cache.getName(), exception.getMessage());
            }
        };
    }
}
