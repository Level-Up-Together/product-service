package io.pinkspider.leveluptogethermvp.chatservice.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.GuildMemberJoinedChatNotifyEvent;
import io.pinkspider.global.event.GuildMemberKickedChatNotifyEvent;
import io.pinkspider.global.event.GuildMemberLeftChatNotifyEvent;
import io.pinkspider.global.event.GuildMemberRemovedEvent;
import io.pinkspider.global.event.UserWithdrawnEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatEventListenerTest {

    @Mock
    private GuildChatService guildChatService;

    @Mock
    private GuildDirectMessageService guildDirectMessageService;

    @InjectMocks
    private ChatEventListener chatEventListener;

    @Nested
    @DisplayName("handleMemberJoined 테스트")
    class HandleMemberJoinedTest {

        @Test
        @DisplayName("멤버 가입 이벤트 수신 시 notifyMemberJoin을 호출한다")
        void handleMemberJoined_success() {
            // given
            Long guildId = 1L;
            String nickname = "테스트유저";
            GuildMemberJoinedChatNotifyEvent event = new GuildMemberJoinedChatNotifyEvent(guildId, nickname);

            // when
            chatEventListener.handleMemberJoined(event);

            // then
            verify(guildChatService).notifyMemberJoin(guildId, nickname);
        }
    }

    @Nested
    @DisplayName("handleMemberLeft 테스트")
    class HandleMemberLeftTest {

        @Test
        @DisplayName("멤버 탈퇴 이벤트 수신 시 notifyMemberLeave를 호출한다")
        void handleMemberLeft_success() {
            // given
            Long guildId = 2L;
            String nickname = "탈퇴유저";
            GuildMemberLeftChatNotifyEvent event = new GuildMemberLeftChatNotifyEvent(guildId, nickname);

            // when
            chatEventListener.handleMemberLeft(event);

            // then
            verify(guildChatService).notifyMemberLeave(guildId, nickname);
        }
    }

    @Nested
    @DisplayName("handleMemberKicked 테스트")
    class HandleMemberKickedTest {

        @Test
        @DisplayName("멤버 추방 이벤트 수신 시 notifyMemberKick을 호출한다")
        void handleMemberKicked_success() {
            // given
            Long guildId = 3L;
            String nickname = "추방유저";
            GuildMemberKickedChatNotifyEvent event = new GuildMemberKickedChatNotifyEvent(guildId, nickname);

            // when
            chatEventListener.handleMemberKicked(event);

            // then
            verify(guildChatService).notifyMemberKick(guildId, nickname);
        }
    }

    @Nested
    @DisplayName("DM 대화방 정리 테스트 (LUT-287)")
    class DmCleanupTest {

        @Test
        @DisplayName("길드 탈퇴/추방 이벤트 수신 시 해당 길드의 DM 대화방을 비활성화한다")
        void handleGuildMemberRemoved_deactivatesGuildConversations() {
            // given
            GuildMemberRemovedEvent event = new GuildMemberRemovedEvent("user-1", 1L);
            when(guildDirectMessageService.deactivateConversations(1L, "user-1")).thenReturn(2);

            // when
            chatEventListener.handleGuildMemberRemoved(event);

            // then
            verify(guildDirectMessageService).deactivateConversations(1L, "user-1");
        }

        @Test
        @DisplayName("회원 탈퇴 이벤트 수신 시 전 길드의 DM 대화방을 비활성화한다")
        void handleUserWithdrawn_deactivatesAllConversations() {
            // given
            UserWithdrawnEvent event = new UserWithdrawnEvent("user-1");
            when(guildDirectMessageService.deactivateConversationsForUser("user-1")).thenReturn(3);

            // when
            chatEventListener.handleUserWithdrawn(event);

            // then
            verify(guildDirectMessageService).deactivateConversationsForUser("user-1");
        }
    }
}
