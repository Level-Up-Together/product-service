package io.pinkspider.leveluptogethermvp.notificationservice.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 알림 실시간 릴레이용 Redis pub/sub 리스너 설정 (QA-224)
 */
@Configuration
@Profile("!test")
@RequiredArgsConstructor
public class NotificationRealtimeConfig {

    private final RedisConnectionFactory redisConnectionFactory;
    private final NotificationRealtimeRelay notificationRealtimeRelay;

    @Bean
    public RedisMessageListenerContainer notificationRealtimeListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
            notificationRealtimeRelay, new ChannelTopic(NotificationRealtimePublisher.CHANNEL));
        return container;
    }
}
