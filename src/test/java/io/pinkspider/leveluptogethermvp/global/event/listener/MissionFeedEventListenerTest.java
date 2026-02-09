package io.pinkspider.leveluptogethermvp.global.event.listener;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.pinkspider.global.event.MissionDeletedEvent;
import io.pinkspider.global.event.MissionFeedImageChangedEvent;
import io.pinkspider.global.event.MissionFeedUnsharedEvent;
import io.pinkspider.global.event.listener.MissionFeedEventListener;
import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionFeedEventListener 테스트")
class MissionFeedEventListenerTest {

    @Mock
    private ActivityFeedService activityFeedService;

    @InjectMocks
    private MissionFeedEventListener eventListener;

    @Nested
    @DisplayName("피드 이미지 변경 이벤트")
    class HandleFeedImageChangedTest {

        @Test
        @DisplayName("이미지 URL 업데이트를 처리한다")
        void shouldUpdateFeedImageUrl() {
            // given
            var event = new MissionFeedImageChangedEvent("user-123", 1L, "https://example.com/image.jpg");

            // when
            eventListener.handleFeedImageChanged(event);

            // then
            verify(activityFeedService).updateFeedImageUrlByExecutionId(1L, "https://example.com/image.jpg");
        }

        @Test
        @DisplayName("이미지 삭제(null)를 처리한다")
        void shouldDeleteFeedImage() {
            // given
            var event = new MissionFeedImageChangedEvent("user-123", 1L, null);

            // when
            eventListener.handleFeedImageChanged(event);

            // then
            verify(activityFeedService).updateFeedImageUrlByExecutionId(1L, null);
        }

        @Test
        @DisplayName("예외 발생 시 전파하지 않는다")
        void shouldNotPropagateException() {
            // given
            var event = new MissionFeedImageChangedEvent("user-123", 1L, "https://example.com/image.jpg");
            doThrow(new RuntimeException("DB error"))
                .when(activityFeedService).updateFeedImageUrlByExecutionId(1L, "https://example.com/image.jpg");

            // when - should not throw
            eventListener.handleFeedImageChanged(event);
        }
    }

    @Nested
    @DisplayName("피드 공유 취소 이벤트")
    class HandleFeedUnsharedTest {

        @Test
        @DisplayName("피드를 삭제한다")
        void shouldDeleteFeedByExecutionId() {
            // given
            var event = new MissionFeedUnsharedEvent("user-123", 1L);

            // when
            eventListener.handleFeedUnshared(event);

            // then
            verify(activityFeedService).deleteFeedByExecutionId(1L);
        }

        @Test
        @DisplayName("예외 발생 시 전파하지 않는다")
        void shouldNotPropagateException() {
            // given
            var event = new MissionFeedUnsharedEvent("user-123", 1L);
            doThrow(new RuntimeException("DB error"))
                .when(activityFeedService).deleteFeedByExecutionId(1L);

            // when - should not throw
            eventListener.handleFeedUnshared(event);
        }
    }

    @Nested
    @DisplayName("미션 삭제 이벤트")
    class HandleMissionDeletedTest {

        @Test
        @DisplayName("미션 관련 피드를 모두 삭제한다")
        void shouldDeleteFeedsByMissionId() {
            // given
            var event = new MissionDeletedEvent("user-123", 1L);

            // when
            eventListener.handleMissionDeleted(event);

            // then
            verify(activityFeedService).deleteFeedsByMissionId(1L);
        }

        @Test
        @DisplayName("예외 발생 시 전파하지 않는다")
        void shouldNotPropagateException() {
            // given
            var event = new MissionDeletedEvent("user-123", 1L);
            doThrow(new RuntimeException("DB error"))
                .when(activityFeedService).deleteFeedsByMissionId(1L);

            // when - should not throw
            eventListener.handleMissionDeleted(event);
        }
    }
}
