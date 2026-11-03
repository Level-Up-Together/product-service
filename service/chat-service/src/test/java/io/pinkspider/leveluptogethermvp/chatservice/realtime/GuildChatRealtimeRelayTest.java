package io.pinkspider.leveluptogethermvp.chatservice.realtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class GuildChatRealtimeRelayTest {

    @Mock private SimpMessagingTemplate messagingTemplate;

    private GuildChatRealtimeRelay relay;

    @BeforeEach
    void setUp() {
        relay = new GuildChatRealtimeRelay(messagingTemplate, new ObjectMapper());
    }

    private Message createMessage(String payload) {
        return new DefaultMessage(
                GuildChatRealtimePublisher.CHANNEL.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("수신한 메시지를 토픽 destination으로 브로드캐스트한다")
    void onMessage_relaysToTopic() {
        String payload =
                "{\"destination\":\"/topic/guild/1/chat\",\"body\":{\"id\":1,\"content\":\"hi\"}}";

        relay.onMessage(createMessage(payload), null);

        verify(messagingTemplate)
                .convertAndSend(eq("/topic/guild/1/chat"), eq("{\"id\":1,\"content\":\"hi\"}"));
    }

    @Test
    @DisplayName("destination이 없으면 릴레이하지 않는다")
    void onMessage_missingDestination_skips() {
        relay.onMessage(createMessage("{\"body\":{}}"), null);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("토픽이 아닌 destination(/user, /queue)은 릴레이하지 않는다")
    void onMessage_nonTopicDestination_skips() {
        relay.onMessage(createMessage("{\"destination\":\"/queue/dm\",\"body\":{}}"), null);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("body가 없으면 릴레이하지 않는다")
    void onMessage_missingBody_skips() {
        relay.onMessage(createMessage("{\"destination\":\"/topic/guild/1/chat\"}"), null);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("잘못된 JSON이어도 예외를 던지지 않는다")
    void onMessage_invalidJson_noThrow() {
        relay.onMessage(createMessage("not-json"), null);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("브로커 전송 실패는 예외를 전파하지 않는다 (리스너 컨테이너 보호)")
    void onMessage_brokerFailure_noThrow() {
        doThrow(new RuntimeException("broker"))
                .when(messagingTemplate)
                .convertAndSend(anyString(), any(Object.class));

        relay.onMessage(
                createMessage("{\"destination\":\"/topic/guild/1/chat\",\"body\":{\"id\":1}}"),
                null);

        verify(messagingTemplate).convertAndSend(eq("/topic/guild/1/chat"), eq("{\"id\":1}"));
    }
}
