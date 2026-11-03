package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.pinkspider.global.event.MissionExecutionEndedEvent;
import io.pinkspider.global.event.MissionExecutionStartedEvent;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerMissionInfo;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TowerMissionEventListenerTest {

    private final TowerSeatService seatService = mock(TowerSeatService.class);
    private final TowerMissionEventListener listener = new TowerMissionEventListener(seatService);

    @Test
    @DisplayName("수행 시작 이벤트 → 미션 정보와 함께 자리 서비스 호출")
    void started() {
        listener.onMissionStarted(
                new MissionExecutionStartedEvent(
                        "u1", 10L, 20L, "REGULAR", "달리기", 3L, "PUBLIC", LocalDateTime.now()));

        ArgumentCaptor<TowerMissionInfo> captor = ArgumentCaptor.forClass(TowerMissionInfo.class);
        verify(seatService).onMissionStarted(eq("u1"), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new TowerMissionInfo(10L, 20L, "REGULAR", "달리기", 3L, "PUBLIC"));
    }

    @Test
    @DisplayName("수행 종료 이벤트 → 완료 여부(취소는 false)와 함께 호출 · 서비스 예외는 삼킨다")
    void ended() {
        listener.onMissionEnded(
                new MissionExecutionEndedEvent(
                        "u1", 10L, 20L, "REGULAR", MissionExecutionEndedEvent.EndReason.COMPLETED));
        verify(seatService).onMissionEnded("u1", true);

        listener.onMissionEnded(
                new MissionExecutionEndedEvent(
                        "u1", 10L, 20L, "PINNED", MissionExecutionEndedEvent.EndReason.SKIPPED));
        verify(seatService).onMissionEnded("u1", false);

        doThrow(new RuntimeException("tower down"))
                .when(seatService)
                .onMissionEnded(any(), eq(true));
        doThrow(new RuntimeException("tower down"))
                .when(seatService)
                .onMissionStarted(any(), any());
        assertThatCode(
                        () ->
                                listener.onMissionEnded(
                                        new MissionExecutionEndedEvent(
                                                "u2",
                                                1L,
                                                2L,
                                                "REGULAR",
                                                MissionExecutionEndedEvent.EndReason
                                                        .AUTO_COMPLETED)))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                listener.onMissionStarted(
                                        new MissionExecutionStartedEvent(
                                                "u2",
                                                1L,
                                                2L,
                                                "REGULAR",
                                                "t",
                                                null,
                                                null,
                                                LocalDateTime.now())))
                .doesNotThrowAnyException();
    }
}
