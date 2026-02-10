package io.pinkspider.global.event.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.UserProfileChangedEvent;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedCommentRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatMessageRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatParticipantRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildDirectMessageRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostCommentRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCommentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileSnapshotEventListenerTest {

    @Mock
    private ActivityFeedRepository activityFeedRepository;
    @Mock
    private FeedCommentRepository feedCommentRepository;
    @Mock
    private GuildChatMessageRepository guildChatMessageRepository;
    @Mock
    private GuildPostRepository guildPostRepository;
    @Mock
    private GuildPostCommentRepository guildPostCommentRepository;
    @Mock
    private GuildDirectMessageRepository guildDirectMessageRepository;
    @Mock
    private GuildChatParticipantRepository guildChatParticipantRepository;
    @Mock
    private MissionCommentRepository missionCommentRepository;

    @InjectMocks
    private UserProfileSnapshotEventListener listener;

    private static final UserProfileChangedEvent TEST_EVENT =
        new UserProfileChangedEvent("user-123", "새닉네임", "https://img.example.com/pic.jpg", 5);

    @Test
    @DisplayName("Feed 스냅샷을 동기화한다")
    void handleUserProfileChanged_syncsFeedSnapshots() {
        // given
        when(activityFeedRepository.updateUserProfileByUserId(anyString(), anyString(), anyString(), anyInt())).thenReturn(3);
        when(feedCommentRepository.updateUserProfileByUserId(anyString(), anyString(), anyString(), anyInt())).thenReturn(2);

        // when
        listener.handleUserProfileChanged(TEST_EVENT);

        // then
        verify(activityFeedRepository).updateUserProfileByUserId("user-123", "새닉네임", "https://img.example.com/pic.jpg", 5);
        verify(feedCommentRepository).updateUserProfileByUserId("user-123", "새닉네임", "https://img.example.com/pic.jpg", 5);
    }

    @Test
    @DisplayName("Guild 스냅샷을 동기화한다")
    void handleUserProfileChanged_syncsGuildSnapshots() {
        // when
        listener.handleUserProfileChanged(TEST_EVENT);

        // then
        verify(guildChatMessageRepository).updateSenderNicknameByUserId("user-123", "새닉네임");
        verify(guildPostRepository).updateAuthorNicknameByUserId("user-123", "새닉네임");
        verify(guildPostCommentRepository).updateAuthorNicknameByUserId("user-123", "새닉네임");
        verify(guildDirectMessageRepository).updateSenderNicknameByUserId("user-123", "새닉네임");
        verify(guildChatParticipantRepository).updateUserNicknameByUserId("user-123", "새닉네임");
    }

    @Test
    @DisplayName("Mission 스냅샷을 동기화한다")
    void handleUserProfileChanged_syncsMissionSnapshots() {
        // given
        when(missionCommentRepository.updateUserProfileByUserId(anyString(), anyString(), anyString(), anyInt())).thenReturn(1);

        // when
        listener.handleUserProfileChanged(TEST_EVENT);

        // then
        verify(missionCommentRepository).updateUserProfileByUserId("user-123", "새닉네임", "https://img.example.com/pic.jpg", 5);
    }

    @Test
    @DisplayName("Feed 동기화 실패해도 Guild/Mission 동기화는 진행된다")
    void handleUserProfileChanged_feedFailure_othersStillRun() {
        // given
        doThrow(new RuntimeException("Feed DB 오류"))
            .when(activityFeedRepository).updateUserProfileByUserId(anyString(), anyString(), anyString(), anyInt());

        // when
        listener.handleUserProfileChanged(TEST_EVENT);

        // then - Guild, Mission 동기화는 정상 실행
        verify(guildChatMessageRepository).updateSenderNicknameByUserId("user-123", "새닉네임");
        verify(missionCommentRepository).updateUserProfileByUserId("user-123", "새닉네임", "https://img.example.com/pic.jpg", 5);
    }

    @Test
    @DisplayName("Guild 동기화 실패해도 Mission 동기화는 진행된다")
    void handleUserProfileChanged_guildFailure_missionStillRuns() {
        // given
        doThrow(new RuntimeException("Guild DB 오류"))
            .when(guildChatMessageRepository).updateSenderNicknameByUserId(anyString(), anyString());

        // when
        listener.handleUserProfileChanged(TEST_EVENT);

        // then - Mission 동기화는 정상 실행
        verify(missionCommentRepository).updateUserProfileByUserId("user-123", "새닉네임", "https://img.example.com/pic.jpg", 5);
    }
}
