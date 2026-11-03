package io.pinkspider.leveluptogethermvp.chatservice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.chatservice.application.GuildChatService;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ChatMessageRequest;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ChatMessageResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ReadStatusUpdate;
import io.pinkspider.leveluptogethermvp.chatservice.domain.enums.ChatMessageType;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatParticipantRepository;
import io.pinkspider.leveluptogethermvp.chatservice.realtime.GuildChatRealtimePublisher;
import java.security.Principal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * LUT-547/548 회귀 가드: 길드 단체채팅 브로드캐스트는 반드시 Redis 릴레이 퍼블리셔를 경유해야 한다 (SimpleBroker 직접 전송은 발신 인스턴스
 * 구독자에게만 간다).
 */
@ExtendWith(MockitoExtension.class)
class GuildChatWebSocketControllerTest {

    @Mock private GuildChatService chatService;
    @Mock private GuildChatParticipantRepository participantRepository;
    @Mock private UserQueryFacade userQueryFacade;
    @Mock private GuildChatRealtimePublisher realtimePublisher;
    @Mock private Principal principal;

    @InjectMocks private GuildChatWebSocketController controller;

    private ChatMessageResponse savedMessage() {
        return ChatMessageResponse.builder()
                .id(10L)
                .guildId(1L)
                .senderId("user-a")
                .senderNickname("A")
                .messageType(ChatMessageType.TEXT)
                .content("hello")
                .isSystemMessage(false)
                .createdAt(LocalDateTime.of(2026, 10, 12, 5, 0))
                .build();
    }

    @Test
    @DisplayName("메시지 전송 시 참여자-1 안읽음 수를 담아 /topic/guild/{id}/chat 으로 릴레이 발행한다")
    void sendMessage_publishesToTopicWithUnreadCount() {
        when(principal.getName()).thenReturn("user-a");
        when(userQueryFacade.getUserNickname("user-a")).thenReturn("A");
        when(chatService.sendMessage(eq(1L), eq("user-a"), eq("A"), any()))
                .thenReturn(savedMessage());
        when(participantRepository.countActiveParticipants(1L)).thenReturn(4L);

        controller.sendMessage(1L, new ChatMessageRequest(), principal);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(realtimePublisher).publishToTopic(eq("/topic/guild/1/chat"), captor.capture());
        ChatMessageResponse sent = (ChatMessageResponse) captor.getValue();
        assertThat(sent.getId()).isEqualTo(10L);
        assertThat(sent.getContent()).isEqualTo("hello");
        assertThat(sent.getUnreadCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("참여자가 1명(본인)이면 안읽음 수는 0으로 내려간다")
    void sendMessage_singleParticipant_unreadZero() {
        when(principal.getName()).thenReturn("user-a");
        when(userQueryFacade.getUserNickname("user-a")).thenReturn("A");
        when(chatService.sendMessage(eq(1L), eq("user-a"), eq("A"), any()))
                .thenReturn(savedMessage());
        when(participantRepository.countActiveParticipants(1L)).thenReturn(1L);

        controller.sendMessage(1L, new ChatMessageRequest(), principal);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(realtimePublisher).publishToTopic(eq("/topic/guild/1/chat"), captor.capture());
        assertThat(((ChatMessageResponse) captor.getValue()).getUnreadCount()).isZero();
    }

    @Test
    @DisplayName("인증 정보가 없으면 저장도 발행도 하지 않는다")
    void sendMessage_nullPrincipal_skips() {
        controller.sendMessage(1L, new ChatMessageRequest(), null);

        verify(chatService, never()).sendMessage(any(), any(), any(), any());
        verify(realtimePublisher, never()).publishToTopic(anyString(), any());
    }

    @Test
    @DisplayName("저장 실패 시 예외를 전파하지 않고 발행도 하지 않는다")
    void sendMessage_serviceFails_noPublish() {
        when(principal.getName()).thenReturn("user-a");
        when(userQueryFacade.getUserNickname("user-a")).thenReturn("A");
        when(chatService.sendMessage(eq(1L), eq("user-a"), eq("A"), any()))
                .thenThrow(new RuntimeException("db"));

        controller.sendMessage(1L, new ChatMessageRequest(), principal);

        verify(realtimePublisher, never()).publishToTopic(anyString(), any());
    }

    @Test
    @DisplayName("읽음 처리 시 /topic/guild/{id}/read 로 안읽음 수를 릴레이 발행한다")
    void markAsRead_publishesReadStatus() {
        when(principal.getName()).thenReturn("user-a");
        when(chatService.getUnreadCount(1L, 100L)).thenReturn(2);

        controller.markAsRead(1L, new GuildChatWebSocketController.ReadRequest(100L), principal);

        verify(chatService).markAsRead(1L, "user-a", 100L);
        verify(realtimePublisher)
                .publishToTopic(
                        eq("/topic/guild/1/read"), eq(new ReadStatusUpdate(1L, 100L, "user-a", 2)));
    }

    @Test
    @DisplayName("읽음 처리: 인증 정보가 없으면 스킵한다")
    void markAsRead_nullPrincipal_skips() {
        controller.markAsRead(1L, new GuildChatWebSocketController.ReadRequest(100L), null);

        verify(chatService, never()).markAsRead(any(), any(), any());
        verify(realtimePublisher, never()).publishToTopic(anyString(), any());
    }

    @Test
    @DisplayName("읽음 처리 실패 시 예외를 전파하지 않는다")
    void markAsRead_serviceFails_noThrow() {
        when(principal.getName()).thenReturn("user-a");
        org.mockito.Mockito.doThrow(new RuntimeException("db"))
                .when(chatService)
                .markAsRead(1L, "user-a", 100L);

        controller.markAsRead(1L, new GuildChatWebSocketController.ReadRequest(100L), principal);

        verify(realtimePublisher, never()).publishToTopic(anyString(), any());
    }
}
