package io.pinkspider.leveluptogethermvp.chatservice.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** DM 실시간 릴레이용 Redis pub/sub 리스너 설정 (LUT-263) */
@Configuration
@Profile("!test")
@RequiredArgsConstructor
public class DmRealtimeConfig {

    private final RedisConnectionFactory redisConnectionFactory;
    private final DmRealtimeRelay dmRealtimeRelay;

    @Bean
    public RedisMessageListenerContainer dmRealtimeListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
                dmRealtimeRelay, new ChannelTopic(DmRealtimePublisher.CHANNEL));
        return container;
    }
}
