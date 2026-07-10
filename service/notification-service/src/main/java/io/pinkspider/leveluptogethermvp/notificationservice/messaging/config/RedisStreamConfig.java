package io.pinkspider.leveluptogethermvp.notificationservice.messaging.config;

import io.pinkspider.leveluptogethermvp.notificationservice.messaging.consumer.AppPushMessageConsumer;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    private static final long WATCHDOG_INTERVAL_SECONDS = 30;

    private final RedisConnectionFactory redisConnectionFactory;
    private final AppPushMessageConsumer appPushMessageConsumer;
    private final StringRedisTemplate stringRedisTemplate;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private Subscription subscription;
    private String consumerName;
    private ScheduledExecutorService watchdog;

    @Bean
    public Subscription appPushStreamSubscription() {
        createConsumerGroupIfNotExists();

        // QA-224: 멀티 인스턴스에서 consumer 이름이 겹치지 않도록 호스트명 기반으로 분리
        consumerName = resolveConsumerName();

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        // QA-224: 폴링 오류로 구독이 조용히 죽는 것을 방지 (로그 + 워치독 복구)
                        .errorHandler(e -> log.warn("Redis Stream 폴링 오류: {}", e.getMessage()))
                        .build();

        container = StreamMessageListenerContainer.create(redisConnectionFactory, options);
        subscription = subscribe();
        container.start();
        startWatchdog();

        log.info("Redis Stream Consumer 시작: stream={}, group={}, consumer={}",
                STREAM_KEY, GROUP_NAME, consumerName);

        return subscription;
    }

    private Subscription subscribe() {
        return container.receiveAutoAck(
                Consumer.from(GROUP_NAME, consumerName),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                appPushMessageConsumer
        );
    }

    /**
     * QA-224: 구독이 죽으면 푸시가 밀렸다가 재시작 시 한꺼번에 늦게 나가는 문제 방지.
     * 주기적으로 구독 상태를 점검하고 비활성 시 재구독한다. (인스턴스 로컬 복구라 분산락 불필요)
     */
    private void startWatchdog() {
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "app-push-stream-watchdog");
            t.setDaemon(true);
            return t;
        });
        watchdog.scheduleWithFixedDelay(() -> {
            try {
                if (subscription == null || !subscription.isActive()) {
                    log.warn("Redis Stream 구독 비활성 감지 - 재구독: consumer={}", consumerName);
                    if (subscription != null) {
                        container.remove(subscription);
                    }
                    subscription = subscribe();
                    if (!container.isRunning()) {
                        container.start();
                    }
                }
            } catch (Exception e) {
                log.error("Redis Stream 구독 복구 실패: {}", e.getMessage(), e);
            }
        }, WATCHDOG_INTERVAL_SECONDS, WATCHDOG_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (watchdog != null) {
            watchdog.shutdownNow();
        }
        if (container != null) {
            container.stop();
        }
    }

    private String resolveConsumerName() {
        try {
            return "consumer-" + InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "consumer-" + UUID.randomUUID();
        }
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
