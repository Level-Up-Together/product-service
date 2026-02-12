package io.pinkspider.leveluptogethermvp.guildservice.event.listener;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.pinkspider.global.event.UserProfileChangedEvent;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostCommentRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuildProfileSnapshotEventListener 테스트")
class GuildProfileSnapshotEventListenerTest {

    @Mock
    private GuildPostRepository guildPostRepository;
    @Mock
    private GuildPostCommentRepository guildPostCommentRepository;

    @InjectMocks
    private GuildProfileSnapshotEventListener listener;

    private static final UserProfileChangedEvent TEST_EVENT =
        new UserProfileChangedEvent("user-123", "새닉네임", "https://img.example.com/pic.jpg", 5);

    @Test
    @DisplayName("Guild 스냅샷을 동기화한다")
    void handleUserProfileChanged_syncsGuildSnapshots() {
        listener.handleUserProfileChanged(TEST_EVENT);

        verify(guildPostRepository).updateAuthorNicknameByUserId("user-123", "새닉네임");
        verify(guildPostCommentRepository).updateAuthorNicknameByUserId("user-123", "새닉네임");
    }

    @Test
    @DisplayName("Guild 동기화 실패해도 예외를 전파하지 않는다")
    void handleUserProfileChanged_guildFailure_doesNotPropagate() {
        doThrow(new RuntimeException("Guild DB 오류"))
            .when(guildPostRepository).updateAuthorNicknameByUserId("user-123", "새닉네임");

        listener.handleUserProfileChanged(TEST_EVENT);
    }
}
