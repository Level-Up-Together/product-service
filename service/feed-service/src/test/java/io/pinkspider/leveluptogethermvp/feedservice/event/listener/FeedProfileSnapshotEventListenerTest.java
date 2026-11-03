package io.pinkspider.leveluptogethermvp.feedservice.event.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.UserProfileChangedEvent;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedCommentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedProfileSnapshotEventListener 테스트")
class FeedProfileSnapshotEventListenerTest {

    @Mock private ActivityFeedRepository activityFeedRepository;
    @Mock private FeedCommentRepository feedCommentRepository;

    @InjectMocks private FeedProfileSnapshotEventListener listener;

    private static final UserProfileChangedEvent TEST_EVENT =
            new UserProfileChangedEvent("user-123", "새닉네임", "https://img.example.com/pic.jpg", 5);

    // LUT-276: 레벨은 작성 당시 스냅샷 유지 — 동기화 대상은 닉네임/프로필 사진뿐이다.
    // (이벤트의 level 값이 피드/댓글 UPDATE 에 전달되지 않는 것을 시그니처로 보장)
    @Test
    @DisplayName("Feed 스냅샷을 동기화한다 (닉네임/사진만, 레벨 제외)")
    void handleUserProfileChanged_syncsFeedSnapshots() {
        when(activityFeedRepository.updateUserProfileByUserId(
                        anyString(), anyString(), anyString()))
                .thenReturn(3);
        when(feedCommentRepository.updateUserProfileByUserId(anyString(), anyString(), anyString()))
                .thenReturn(2);

        listener.handleUserProfileChanged(TEST_EVENT);

        verify(activityFeedRepository)
                .updateUserProfileByUserId("user-123", "새닉네임", "https://img.example.com/pic.jpg");
        verify(feedCommentRepository)
                .updateUserProfileByUserId("user-123", "새닉네임", "https://img.example.com/pic.jpg");
    }

    @Test
    @DisplayName("Feed 동기화 실패해도 예외를 전파하지 않는다")
    void handleUserProfileChanged_feedFailure_doesNotPropagate() {
        doThrow(new RuntimeException("Feed DB 오류"))
                .when(activityFeedRepository)
                .updateUserProfileByUserId(anyString(), anyString(), anyString());

        listener.handleUserProfileChanged(TEST_EVENT);
    }

    @Test
    @DisplayName("트랜잭션 밖(AFTER_COMMIT 콜백 안)에서 발행된 이벤트도 받는다 — fallbackExecution (LUT-653)")
    void acceptsEventPublishedOutsideTransaction() throws NoSuchMethodException {
        // 레벨업 → UserLevelUpProfileSyncListener(AFTER_COMMIT) 가 재발행하는 UserProfileChangedEvent 는 트랜잭션
        // 동기화가
        // 해제된 뒤에 오므로, 이 옵션이 없으면 버려져 레벨 스냅샷이 갱신되지 않는다
        org.springframework.transaction.event.TransactionalEventListener ann =
                FeedProfileSnapshotEventListener.class
                        .getMethod("handleUserProfileChanged", UserProfileChangedEvent.class)
                        .getAnnotation(
                                org.springframework.transaction.event.TransactionalEventListener
                                        .class);
        assertThat(ann.fallbackExecution()).isTrue();
        assertThat(ann.phase())
                .isEqualTo(org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT);
    }
}
