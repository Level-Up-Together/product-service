package io.pinkspider.leveluptogethermvp.notificationservice.messaging.config;

import io.pinkspider.leveluptogethermvp.notificationservice.messaging.consumer.AppPushMessageConsumer;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    private static final String STREAM_KEY = "stream:app-push";
    private static final String GROUP_NAME = "app-push-group";
    private static final String CONSUMER_NAME = "consumer-1";

    private final RedisConnectionFactory redisConnectionFactory;
    private final AppPushMessageConsumer appPushMessageConsumer;
    private final StringRedisTemplate stringRedisTemplate;

    @Bean
    public Subscription appPushStreamSubscription() {
        createConsumerGroupIfNotExists();

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(redisConnectionFactory, options);

        Subscription subscription = container.receiveAutoAck(
                Consumer.from(GROUP_NAME, CONSUMER_NAME),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                appPushMessageConsumer
        );

        container.start();
        log.info("Redis Stream Consumer 시작: stream={}, group={}, consumer={}",
                STREAM_KEY, GROUP_NAME, CONSUMER_NAME);

        return subscription;
    }

    private void createConsumerGroupIfNotExists() {
        try {
            stringRedisTemplate.opsForStream().createGroup(STREAM_KEY, GROUP_NAME);
            log.info("Redis Stream Consumer Group 생성: stream={}, group={}", STREAM_KEY, GROUP_NAME);
        } catch (Exception e) {
            // BUSYGROUP: Consumer Group already exists → 정상
            // Stream이 없는 경우에도 MKSTREAM으로 자동 생성하려면 아래 방식 사용
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.debug("Consumer Group 이미 존재: {}", GROUP_NAME);
            } else {
                // Stream이 아직 없는 경우 → 빈 Stream 생성 후 Group 생성
                try {
                    stringRedisTemplate.opsForStream()
                            .add(STREAM_KEY, java.util.Map.of("_init", "true"));
                    stringRedisTemplate.opsForStream().createGroup(STREAM_KEY, GROUP_NAME);
                    log.info("Redis Stream + Consumer Group 생성: stream={}, group={}",
                            STREAM_KEY, GROUP_NAME);
                } catch (Exception ex) {
                    if (ex.getMessage() != null && ex.getMessage().contains("BUSYGROUP")) {
                        log.debug("Consumer Group 이미 존재: {}", GROUP_NAME);
                    } else {
                        log.warn("Consumer Group 생성 실패: {}", ex.getMessage());
                    }
                }
            }
        }
    }
}
