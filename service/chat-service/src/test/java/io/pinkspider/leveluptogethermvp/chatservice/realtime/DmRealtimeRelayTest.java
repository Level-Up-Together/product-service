package io.pinkspider.leveluptogethermvp.chatservice.realtime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
class DmRealtimeRelayTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private DmRealtimeRelay relay;

    @BeforeEach
    void setUp() {
        relay = new DmRealtimeRelay(messagingTemplate, new ObjectMapper());
    }

    private Message createMessage(String payload) {
        return new DefaultMessage(
            DmRealtimePublisher.CHANNEL.getBytes(StandardCharsets.UTF_8),
            payload.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("수신한 메시지를 지정된 destination으로 릴레이한다")
    void onMessage_relaysToDestination() {
        // given
        String payload =
            "{\"user_id\":\"user-1\",\"destination\":\"/queue/dm\",\"body\":{\"id\":1,\"content\":\"hi\"}}";

        // when
        relay.onMessage(createMessage(payload), null);

        // then
        verify(messagingTemplate)
            .convertAndSendToUser(eq("user-1"), eq("/queue/dm"), eq("{\"id\":1,\"content\":\"hi\"}"));
    }

    @Test
    @DisplayName("user_id가 없으면 릴레이하지 않는다")
    void onMessage_missingUserId_skips() {
        relay.onMessage(createMessage("{\"destination\":\"/queue/dm\",\"body\":{}}"), null);

        verify(messagingTemplate, never())
            .convertAndSendToUser(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("destination이 없으면 릴레이하지 않는다")
    void onMessage_missingDestination_skips() {
        relay.onMessage(createMessage("{\"user_id\":\"user-1\",\"body\":{}}"), null);

        verify(messagingTemplate, never())
            .convertAndSendToUser(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("잘못된 JSON이어도 예외를 던지지 않는다")
    void onMessage_invalidJson_noThrow() {
        relay.onMessage(createMessage("not-json"), null);

        verify(messagingTemplate, never())
            .convertAndSendToUser(anyString(), anyString(), anyString());
    }
}
