package io.pinkspider.leveluptogethermvp.chatservice.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReadStatusUpdate 직렬화 계약 테스트 (LUT-395)")
class ReadStatusUpdateTest {

    // 프론트(chat-websocket.ts)가 message_id/unread_count 로 매칭하므로
    // snake_case 직렬화가 깨지면 읽음 수 실시간 갱신이 조용히 no-op 이 된다.
    @Test
    @DisplayName("WS 브로드캐스트 페이로드는 snake_case 로 직렬화된다")
    void serializesToSnakeCase() throws Exception {
        ReadStatusUpdate update = new ReadStatusUpdate(1L, 42L, "user-1", 3);

        String json = new ObjectMapper().writeValueAsString(update);

        assertThat(json).contains("\"guild_id\":1");
        assertThat(json).contains("\"message_id\":42");
        assertThat(json).contains("\"user_id\":\"user-1\"");
        assertThat(json).contains("\"unread_count\":3");
        assertThat(json).doesNotContain("messageId");
        assertThat(json).doesNotContain("unreadCount");
    }
}
