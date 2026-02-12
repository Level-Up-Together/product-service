package io.pinkspider.leveluptogethermvp.missionservice.event.listener;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.UserProfileChangedEvent;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCommentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionProfileSnapshotEventListener 테스트")
class MissionProfileSnapshotEventListenerTest {

    @Mock
    private MissionCommentRepository missionCommentRepository;

    @InjectMocks
    private MissionProfileSnapshotEventListener listener;

    private static final UserProfileChangedEvent TEST_EVENT =
        new UserProfileChangedEvent("user-123", "새닉네임", "https://img.example.com/pic.jpg", 5);

    @Test
    @DisplayName("Mission 스냅샷을 동기화한다")
    void handleUserProfileChanged_syncsMissionSnapshots() {
        when(missionCommentRepository.updateUserProfileByUserId(anyString(), anyString(), anyString(), anyInt())).thenReturn(1);

        listener.handleUserProfileChanged(TEST_EVENT);

        verify(missionCommentRepository).updateUserProfileByUserId("user-123", "새닉네임", "https://img.example.com/pic.jpg", 5);
    }

    @Test
    @DisplayName("Mission 동기화 실패해도 예외를 전파하지 않는다")
    void handleUserProfileChanged_missionFailure_doesNotPropagate() {
        doThrow(new RuntimeException("Mission DB 오류"))
            .when(missionCommentRepository).updateUserProfileByUserId(anyString(), anyString(), anyString(), anyInt());

        listener.handleUserProfileChanged(TEST_EVENT);
    }
}
