package io.pinkspider.leveluptogethermvp.chatservice.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 채팅 실시간 릴레이용 Redis pub/sub 리스너 설정
 *
 * <ul>
 *   <li>DM user destination 릴레이 — {@link DmRealtimeRelay} (LUT-263)
 *   <li>길드 단체채팅 토픽 릴레이 — {@link GuildChatRealtimeRelay} (LUT-549)
 * </ul>
 */
@Configuration
@Profile("!test")
@RequiredArgsConstructor
public class ChatRealtimeConfig {

    private final RedisConnectionFactory redisConnectionFactory;
    private final DmRealtimeRelay dmRealtimeRelay;
    private final GuildChatRealtimeRelay guildChatRealtimeRelay;

    @Bean
    public RedisMessageListenerContainer chatRealtimeListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
                dmRealtimeRelay, new ChannelTopic(DmRealtimePublisher.CHANNEL));
        container.addMessageListener(
                guildChatRealtimeRelay, new ChannelTopic(GuildChatRealtimePublisher.CHANNEL));
        return container;
    }
}
