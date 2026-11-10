package io.pinkspider.leveluptogethermvp.towerservice.realtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class TowerRealtimeRelayTest {

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final TowerRealtimeRelay relay = new TowerRealtimeRelay(template, new ObjectMapper());

    private static Message message(String json) {
        Message m = mock(Message.class);
        when(m.getBody()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        return m;
    }

    @Test
    @DisplayName("탑 토픽 메시지는 body JSON 문자열 그대로 STOMP 로 보낸다")
    void relays() {
        relay.onMessage(
                message(
                        "{\"destination\":\"/topic/tower/instances/9\",\"body\":{\"type\":\"USER_STATE\",\"user_id\":\"u1\"}}"),
                null);
        verify(template)
                .convertAndSend(
                        eq("/topic/tower/instances/9"),
                        eq("{\"type\":\"USER_STATE\",\"user_id\":\"u1\"}"));
    }

    @Test
    @DisplayName("탑 토픽이 아니거나 필드가 빠지거나 JSON 이 깨지면 보내지 않고 예외도 내지 않는다")
    void ignoresInvalid() {
        relay.onMessage(message("{\"destination\":\"/topic/guild/1/chat\",\"body\":{}}"), null);
        relay.onMessage(message("{\"destination\":\"/topic/tower/instances/9\"}"), null);
        assertThatCode(() -> relay.onMessage(message("not json"), null)).doesNotThrowAnyException();
        verify(template, never()).convertAndSend(anyString(), (Object) anyString());
    }
}
