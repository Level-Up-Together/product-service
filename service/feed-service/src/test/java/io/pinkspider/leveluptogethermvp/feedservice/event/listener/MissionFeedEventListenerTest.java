package io.pinkspider.leveluptogethermvp.feedservice.event.listener;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyLong;

import io.pinkspider.global.event.MissionDeletedEvent;
import io.pinkspider.global.event.MissionFeedImageChangedEvent;
import io.pinkspider.global.event.MissionFeedUnsharedEvent;
import io.pinkspider.leveluptogethermvp.feedservice.application.FeedCommandService;
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
    private FeedCommandService feedCommandService;

    @InjectMocks
    private MissionFeedEventListener eventListener;

    @Nested
    @DisplayName("피드 이미지 변경 이벤트")
    class HandleFeedImageChangedTest {

        @Test
        @DisplayName("이미지 URL 업데이트를 처리한다")
        void shouldUpdateFeedImageUrl() {
            var event = new MissionFeedImageChangedEvent("user-123", 1L, "https://example.com/image.jpg");
            eventListener.handleFeedImageChanged(event);
            verify(feedCommandService).updateFeedImageUrlByExecutionId(1L, "https://example.com/image.jpg");
        }

        @Test
        @DisplayName("이미지 삭제(null)를 처리한다")
        void shouldDeleteFeedImage() {
            var event = new MissionFeedImageChangedEvent("user-123", 1L, null);
            eventListener.handleFeedImageChanged(event);
            verify(feedCommandService).updateFeedImageUrlByExecutionId(1L, null);
        }

        @Test
        @DisplayName("예외 발생 시 전파하지 않는다")
        void shouldNotPropagateException() {
            var event = new MissionFeedImageChangedEvent("user-123", 1L, "https://example.com/image.jpg");
            doThrow(new RuntimeException("DB error"))
                .when(feedCommandService).updateFeedImageUrlByExecutionId(1L, "https://example.com/image.jpg");
            eventListener.handleFeedImageChanged(event);
        }
    }

    @Nested
    @DisplayName("피드 공유 취소 이벤트")
    class HandleFeedUnsharedTest {

        @Test
        @DisplayName("피드를 삭제한다")
        void shouldDeleteFeedByExecutionId() {
            var event = new MissionFeedUnsharedEvent("user-123", 1L);
            eventListener.handleFeedUnshared(event);
            verify(feedCommandService).deleteFeedByExecutionId(1L);
        }

        @Test
        @DisplayName("예외 발생 시 전파하지 않는다")
        void shouldNotPropagateException() {
            var event = new MissionFeedUnsharedEvent("user-123", 1L);
            doThrow(new RuntimeException("DB error"))
                .when(feedCommandService).deleteFeedByExecutionId(1L);
            eventListener.handleFeedUnshared(event);
        }
    }

    @Nested
    @DisplayName("미션 소프트 삭제 이벤트")
    class HandleMissionDeletedTest {

        @Test
        @DisplayName("소프트 삭제 시 피드를 삭제하지 않는다 (히스토리 보존)")
        void shouldNotDeleteFeedsOnSoftDelete() {
            var event = new MissionDeletedEvent("user-123", 1L);
            eventListener.handleMissionDeleted(event);
            verify(feedCommandService, never()).deleteFeedsByMissionId(anyLong());
        }
    }
}
