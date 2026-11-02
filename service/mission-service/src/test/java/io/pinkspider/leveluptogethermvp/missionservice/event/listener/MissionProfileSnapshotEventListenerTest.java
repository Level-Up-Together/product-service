package io.pinkspider.leveluptogethermvp.missionservice.event.listener;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Mock private MissionCommentRepository missionCommentRepository;

    @InjectMocks private MissionProfileSnapshotEventListener listener;

    private static final UserProfileChangedEvent TEST_EVENT =
            new UserProfileChangedEvent("user-123", "새닉네임", "https://img.example.com/pic.jpg", 5);

    @Test
    @DisplayName("Mission 스냅샷을 동기화한다")
    void handleUserProfileChanged_syncsMissionSnapshots() {
        when(missionCommentRepository.updateUserProfileByUserId(
                        anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(1);

        listener.handleUserProfileChanged(TEST_EVENT);

        verify(missionCommentRepository)
                .updateUserProfileByUserId(
                        "user-123", "새닉네임", "https://img.example.com/pic.jpg", 5);
    }

    @Test
    @DisplayName("Mission 동기화 실패해도 예외를 전파하지 않는다")
    void handleUserProfileChanged_missionFailure_doesNotPropagate() {
        doThrow(new RuntimeException("Mission DB 오류"))
                .when(missionCommentRepository)
                .updateUserProfileByUserId(anyString(), anyString(), anyString(), anyInt());

        listener.handleUserProfileChanged(TEST_EVENT);
    }

    @Test
    @DisplayName("트랜잭션 밖(AFTER_COMMIT 콜백 안)에서 발행된 이벤트도 받는다 — fallbackExecution (LUT-653)")
    void acceptsEventPublishedOutsideTransaction() throws NoSuchMethodException {
        // 레벨업 → UserLevelUpProfileSyncListener(AFTER_COMMIT) 가 재발행하는 UserProfileChangedEvent 는 트랜잭션
        // 동기화가
        // 해제된 뒤에 오므로, 이 옵션이 없으면 버려져 레벨 스냅샷이 갱신되지 않는다
        org.springframework.transaction.event.TransactionalEventListener ann =
                MissionProfileSnapshotEventListener.class
                        .getMethod("handleUserProfileChanged", UserProfileChangedEvent.class)
                        .getAnnotation(
                                org.springframework.transaction.event.TransactionalEventListener
                                        .class);
        assertThat(ann.fallbackExecution()).isTrue();
        assertThat(ann.phase())
                .isEqualTo(org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT);
    }
}
