package io.pinkspider.leveluptogethermvp.chatservice.event.listener;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.pinkspider.global.event.UserProfileChangedEvent;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatMessageRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatParticipantRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildDirectMessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatProfileSnapshotEventListener 테스트")
class ChatProfileSnapshotEventListenerTest {

    @Mock
    private GuildChatMessageRepository guildChatMessageRepository;
    @Mock
    private GuildChatParticipantRepository guildChatParticipantRepository;
    @Mock
    private GuildDirectMessageRepository guildDirectMessageRepository;

    @InjectMocks
    private ChatProfileSnapshotEventListener listener;

    private static final UserProfileChangedEvent TEST_EVENT =
        new UserProfileChangedEvent("user-123", "새닉네임", "https://img.example.com/pic.jpg", 5);

    @Test
    @DisplayName("Chat 스냅샷을 동기화한다")
    void handleUserProfileChanged_syncsChatSnapshots() {
        listener.handleUserProfileChanged(TEST_EVENT);

        verify(guildChatMessageRepository).updateSenderNicknameByUserId("user-123", "새닉네임");
        verify(guildDirectMessageRepository).updateSenderNicknameByUserId("user-123", "새닉네임");
        verify(guildChatParticipantRepository).updateUserNicknameByUserId("user-123", "새닉네임");
    }

    @Test
    @DisplayName("Chat 동기화 실패해도 예외를 전파하지 않는다")
    void handleUserProfileChanged_chatFailure_doesNotPropagate() {
        doThrow(new RuntimeException("Chat DB 오류"))
            .when(guildChatMessageRepository).updateSenderNicknameByUserId("user-123", "새닉네임");

        listener.handleUserProfileChanged(TEST_EVENT);
    }
}
