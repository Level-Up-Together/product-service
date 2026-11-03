package io.pinkspider.leveluptogethermvp.towerservice.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** LUT-632: 탑 실시간 릴레이용 Redis pub/sub 리스너 컨테이너 (채팅 {@code ChatRealtimeConfig} 와 같은 구조) */
@Configuration
@Profile("!test")
@RequiredArgsConstructor
public class TowerRealtimeConfig {

    private final RedisConnectionFactory redisConnectionFactory;
    private final TowerRealtimeRelay towerRealtimeRelay;

    @Bean
    public RedisMessageListenerContainer towerRealtimeListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
                towerRealtimeRelay, new ChannelTopic(TowerRealtimePublisher.CHANNEL));
        return container;
    }
}
