package io.pinkspider.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.pinkspider.global.properties.RedisProperties;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
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
@Configuration
@RequiredArgsConstructor
@EnableRedisRepositories
@EnableCaching
public class RedisConfig {

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
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        // 캐시별 TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Home 관련 캐시: 5분 TTL
        RedisCacheConfiguration homeConfig = defaultConfig.entryTtl(Duration.ofMinutes(5));
        cacheConfigurations.put("todayPlayers", homeConfig);
        cacheConfigurations.put("todayPlayersByCategory", homeConfig);
        cacheConfigurations.put("mvpGuilds", homeConfig);

        // Mission Category 캐시: 1시간 TTL (카테고리는 자주 변경되지 않음)
        RedisCacheConfiguration categoryConfig = defaultConfig.entryTtl(Duration.ofHours(1));
        cacheConfigurations.put("missionCategories", categoryConfig);

        // User Title 캐시: 5분 TTL (칭호 장착 변경 빈도 고려)
        RedisCacheConfiguration titleConfig = defaultConfig.entryTtl(Duration.ofMinutes(5));
        cacheConfigurations.put("userTitleInfo", titleConfig);

        // Friend IDs 캐시: 10분 TTL (친구 관계 변경 빈도 고려)
        RedisCacheConfiguration friendConfig = defaultConfig.entryTtl(Duration.ofMinutes(10));
        cacheConfigurations.put("userFriendIds", friendConfig);

        // User Profile 캐시: 5분 TTL (피드 생성 시 사용)
        RedisCacheConfiguration profileConfig = defaultConfig.entryTtl(Duration.ofMinutes(5));
        cacheConfigurations.put("userProfile", profileConfig);

        return RedisCacheManager.RedisCacheManagerBuilder
                .fromConnectionFactory(redisConnectionFactory())
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
