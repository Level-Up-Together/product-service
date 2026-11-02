package io.pinkspider.leveluptogethermvp.chatservice.event.listener;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Mock private GuildChatMessageRepository guildChatMessageRepository;
    @Mock private GuildChatParticipantRepository guildChatParticipantRepository;
    @Mock private GuildDirectMessageRepository guildDirectMessageRepository;

    @InjectMocks private ChatProfileSnapshotEventListener listener;

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
                .when(guildChatMessageRepository)
                .updateSenderNicknameByUserId("user-123", "새닉네임");

        listener.handleUserProfileChanged(TEST_EVENT);
    }

    @Test
    @DisplayName("트랜잭션 밖(AFTER_COMMIT 콜백 안)에서 발행된 이벤트도 받는다 — fallbackExecution (LUT-653)")
    void acceptsEventPublishedOutsideTransaction() throws NoSuchMethodException {
        // 레벨업 → UserLevelUpProfileSyncListener(AFTER_COMMIT) 가 재발행하는 UserProfileChangedEvent 는 트랜잭션
        // 동기화가
        // 해제된 뒤에 오므로, 이 옵션이 없으면 버려져 레벨 스냅샷이 갱신되지 않는다
        org.springframework.transaction.event.TransactionalEventListener ann =
                ChatProfileSnapshotEventListener.class
                        .getMethod("handleUserProfileChanged", UserProfileChangedEvent.class)
                        .getAnnotation(
                                org.springframework.transaction.event.TransactionalEventListener
                                        .class);
        assertThat(ann.fallbackExecution()).isTrue();
        assertThat(ann.phase())
                .isEqualTo(org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT);
    }
}
