package io.pinkspider.leveluptogethermvp.global.event.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import io.pinkspider.global.event.MissionStateChangedEvent;
import io.pinkspider.global.event.listener.MissionStateHistoryEventListener;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionStateHistory;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionStateHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionStateHistoryEventListener 테스트")
class MissionStateHistoryEventListenerTest {

    @Mock
    private MissionStateHistoryRepository stateHistoryRepository;

    @InjectMocks
    private MissionStateHistoryEventListener eventListener;

    @Captor
    private ArgumentCaptor<MissionStateHistory> historyCaptor;

    @Nested
    @DisplayName("미션 상태 변경 이벤트 처리")
    class HandleMissionStateChangedTest {

        @Test
        @DisplayName("미션 생성 이벤트 처리 시 히스토리가 저장된다")
        void shouldSaveHistoryOnCreation() {
            // given
            MissionStateChangedEvent event = MissionStateChangedEvent.ofCreation(
                "user-123", 1L, MissionStatus.DRAFT
            );

            // when
            eventListener.handleMissionStateChanged(event);

            // then
            verify(stateHistoryRepository).save(historyCaptor.capture());
            MissionStateHistory saved = historyCaptor.getValue();

            assertThat(saved.getMissionId()).isEqualTo(1L);
            assertThat(saved.getFromStatus()).isNull();
            assertThat(saved.getToStatus()).isEqualTo(MissionStatus.DRAFT);
            assertThat(saved.getTriggerEvent()).isEqualTo("CREATE");
            assertThat(saved.getTriggeredBy()).isEqualTo("user-123");
        }

        @Test
        @DisplayName("미션 오픈 이벤트 처리 시 히스토리가 저장된다")
        void shouldSaveHistoryOnOpen() {
            // given
            MissionStateChangedEvent event = MissionStateChangedEvent.ofOpen(
                "user-123", 1L, MissionStatus.DRAFT
            );

            // when
            eventListener.handleMissionStateChanged(event);

            // then
            verify(stateHistoryRepository).save(historyCaptor.capture());
            MissionStateHistory saved = historyCaptor.getValue();

            assertThat(saved.getMissionId()).isEqualTo(1L);
            assertThat(saved.getFromStatus()).isEqualTo(MissionStatus.DRAFT);
            assertThat(saved.getToStatus()).isEqualTo(MissionStatus.OPEN);
            assertThat(saved.getTriggerEvent()).isEqualTo("OPEN");
            assertThat(saved.getTriggeredBy()).isEqualTo("user-123");
        }

        @Test
        @DisplayName("미션 시작 이벤트 처리 시 히스토리가 저장된다")
        void shouldSaveHistoryOnStart() {
            // given
            MissionStateChangedEvent event = MissionStateChangedEvent.ofStart(
                "user-123", 1L, MissionStatus.OPEN
            );

            // when
            eventListener.handleMissionStateChanged(event);

            // then
            verify(stateHistoryRepository).save(historyCaptor.capture());
            MissionStateHistory saved = historyCaptor.getValue();

            assertThat(saved.getMissionId()).isEqualTo(1L);
            assertThat(saved.getFromStatus()).isEqualTo(MissionStatus.OPEN);
            assertThat(saved.getToStatus()).isEqualTo(MissionStatus.IN_PROGRESS);
            assertThat(saved.getTriggerEvent()).isEqualTo("START");
        }

        @Test
        @DisplayName("미션 완료 이벤트 처리 시 히스토리가 저장된다")
        void shouldSaveHistoryOnComplete() {
            // given
            MissionStateChangedEvent event = MissionStateChangedEvent.ofComplete(
                "user-123", 1L, MissionStatus.IN_PROGRESS
            );

            // when
            eventListener.handleMissionStateChanged(event);

            // then
            verify(stateHistoryRepository).save(historyCaptor.capture());
            MissionStateHistory saved = historyCaptor.getValue();

            assertThat(saved.getMissionId()).isEqualTo(1L);
            assertThat(saved.getFromStatus()).isEqualTo(MissionStatus.IN_PROGRESS);
            assertThat(saved.getToStatus()).isEqualTo(MissionStatus.COMPLETED);
            assertThat(saved.getTriggerEvent()).isEqualTo("COMPLETE");
        }

        @Test
        @DisplayName("미션 취소 이벤트 처리 시 히스토리가 저장된다")
        void shouldSaveHistoryOnCancel() {
            // given
            MissionStateChangedEvent event = MissionStateChangedEvent.ofCancel(
                "user-123", 1L, MissionStatus.OPEN
            );

            // when
            eventListener.handleMissionStateChanged(event);

            // then
            verify(stateHistoryRepository).save(historyCaptor.capture());
            MissionStateHistory saved = historyCaptor.getValue();

            assertThat(saved.getMissionId()).isEqualTo(1L);
            assertThat(saved.getFromStatus()).isEqualTo(MissionStatus.OPEN);
            assertThat(saved.getToStatus()).isEqualTo(MissionStatus.CANCELLED);
            assertThat(saved.getTriggerEvent()).isEqualTo("CANCEL");
        }

        @Test
        @DisplayName("사유가 있는 취소 이벤트 처리 시 사유도 저장된다")
        void shouldSaveHistoryWithReasonOnCancelWithReason() {
            // given
            MissionStateChangedEvent event = MissionStateChangedEvent.ofCancelWithReason(
                "user-123", 1L, MissionStatus.DRAFT, "참여자 부족으로 취소"
            );

            // when
            eventListener.handleMissionStateChanged(event);

            // then
            verify(stateHistoryRepository).save(historyCaptor.capture());
            MissionStateHistory saved = historyCaptor.getValue();

            assertThat(saved.getMissionId()).isEqualTo(1L);
            assertThat(saved.getToStatus()).isEqualTo(MissionStatus.CANCELLED);
            assertThat(saved.getReason()).isEqualTo("참여자 부족으로 취소");
        }
    }
}
