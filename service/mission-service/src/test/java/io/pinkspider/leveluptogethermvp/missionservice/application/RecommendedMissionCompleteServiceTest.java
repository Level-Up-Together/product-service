package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.api.exception.RecommendedMissionException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionCompleteResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionStartResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendedMissionCompleteService — 체크 추천 미션 원탭 완료 (LUT-803)")
class RecommendedMissionCompleteServiceTest {

    private static final String USER = "u1";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    @Mock RecommendedMissionService recommendedMissionService;
    @Mock MissionExecutionService executionService;
    @Mock MissionService missionService;
    @InjectMocks RecommendedMissionCompleteService service;

    private RecommendedMissionStartResponse started() {
        return new RecommendedMissionStartResponse(
                MissionResponse.builder().id(77L).title("물 한 잔 마시기").build(),
                MissionExecutionResponse.builder()
                        .id(900L)
                        .missionId(77L)
                        .executionDate(TODAY)
                        .status(ExecutionStatus.IN_PROGRESS)
                        .build());
    }

    private MissionExecutionResponse completed() {
        return MissionExecutionResponse.builder()
                .id(900L)
                .missionId(77L)
                .executionDate(TODAY)
                .status(ExecutionStatus.COMPLETED)
                .expEarned(10)
                .dailySimpleExpCapped(false)
                .build();
    }

    @Test
    @DisplayName("체크 추천 → 생성 + 시작(먼저 커밋) 뒤 같은 날짜로 완료하고, 만든 미션과 완료 결과를 돌려준다")
    void complete() {
        when(recommendedMissionService.start(USER, 1L, "ko")).thenReturn(started());
        when(executionService.completeExecution(77L, USER, TODAY, null, (FeedVisibility) null))
                .thenReturn(completed());

        RecommendedMissionCompleteResponse result = service.complete(USER, 1L, "ko");

        assertThat(result.mission().getId()).isEqualTo(77L);
        assertThat(result.execution().getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(result.execution().getExpEarned()).isEqualTo(10);
        // 체크인지 확인 → 생성·시작 → 완료 순서
        InOrder order = inOrder(recommendedMissionService, executionService);
        order.verify(recommendedMissionService).requireActiveSimple(1L);
        order.verify(recommendedMissionService).start(USER, 1L, "ko");
        order.verify(executionService)
                .completeExecution(77L, USER, TODAY, null, (FeedVisibility) null);
        // 성공하면 되돌리지 않는다
        verify(executionService, never()).skipExecution(anyLong(), anyString(), any());
        verifyNoInteractions(missionService);
    }

    @Test
    @DisplayName("타이머 추천 → 400(050503), 미션을 만들지 않는다")
    void notSimple() {
        doThrow(RecommendedMissionException.notSimple())
                .when(recommendedMissionService)
                .requireActiveSimple(2L);

        assertThatThrownBy(() -> service.complete(USER, 2L, null))
                .isInstanceOf(RecommendedMissionException.class)
                .hasFieldOrPropertyWithValue("code", RecommendedMissionException.CODE_NOT_SIMPLE);
        verify(recommendedMissionService, never()).start(any(), any(), any());
        verifyNoInteractions(executionService, missionService);
    }

    @Test
    @DisplayName("없거나 꺼진 추천 → 404(050501), 미션을 만들지 않는다")
    void notFound() {
        doThrow(RecommendedMissionException.notFound())
                .when(recommendedMissionService)
                .requireActiveSimple(9L);

        assertThatThrownBy(() -> service.complete(USER, 9L, null))
                .isInstanceOf(RecommendedMissionException.class)
                .hasFieldOrPropertyWithValue("code", RecommendedMissionException.CODE_NOT_FOUND);
        verify(recommendedMissionService, never()).start(any(), any(), any());
    }

    @Test
    @DisplayName("생성·시작이 실패하면(진행 중인 미션 있음 등) 그 오류 그대로 — 되돌릴 것이 없다")
    void startFails() {
        when(recommendedMissionService.start(USER, 1L, null))
                .thenThrow(new IllegalStateException("이미 진행 중인 미션이 있습니다"));

        assertThatThrownBy(() -> service.complete(USER, 1L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("진행 중");
        verifyNoInteractions(executionService, missionService);
    }

    @Test
    @DisplayName("완료가 실패하면 수행을 취소하고 만든 미션을 지운 뒤 원래 오류를 던진다 (수행 중으로 남지 않는다)")
    void completeFails_discardsMission() {
        when(recommendedMissionService.start(USER, 1L, null)).thenReturn(started());
        when(executionService.completeExecution(77L, USER, TODAY, null, (FeedVisibility) null))
                .thenThrow(new IllegalStateException("경험치 지급 실패"));

        assertThatThrownBy(() -> service.complete(USER, 1L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("경험치 지급 실패");

        InOrder order = inOrder(executionService, missionService);
        order.verify(executionService).skipExecution(77L, USER, TODAY);
        order.verify(missionService).deleteMission(77L, USER);
    }

    @Test
    @DisplayName("되돌리기가 실패해도 원래 오류를 던지고, 수행 취소가 실패해도 미션 삭제는 시도한다")
    void discardFails_keepsOriginalError() {
        when(recommendedMissionService.start(USER, 1L, null)).thenReturn(started());
        when(executionService.completeExecution(77L, USER, TODAY, null, (FeedVisibility) null))
                .thenThrow(new IllegalStateException("경험치 지급 실패"));
        when(executionService.skipExecution(77L, USER, TODAY))
                .thenThrow(new IllegalArgumentException("수행 기록 없음"));
        doThrow(new IllegalStateException("삭제 실패")).when(missionService).deleteMission(77L, USER);

        assertThatThrownBy(() -> service.complete(USER, 1L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("경험치 지급 실패");
        verify(missionService).deleteMission(77L, USER);
    }
}
