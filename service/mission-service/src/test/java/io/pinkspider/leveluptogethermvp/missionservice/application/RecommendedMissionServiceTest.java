package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.missionservice.api.exception.RecommendedMissionException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionStartResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.RecommendedMission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.RecommendedMissionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendedMissionService — 추천 미션 목록·원탭 시작 (LUT-782)")
class RecommendedMissionServiceTest {

    @Mock RecommendedMissionRepository repository;
    @Mock MissionService missionService;
    @Mock MissionExecutionService executionService;
    @InjectMocks RecommendedMissionService service;

    private static RecommendedMission mission(long id, MissionExecutionMode mode, Integer minutes) {
        RecommendedMission m =
                RecommendedMission.create(
                        "물 한 잔 마시기",
                        "Drink a glass of water",
                        null,
                        null,
                        10L,
                        mode,
                        minutes,
                        "💧",
                        true,
                        1);
        TestReflectionUtils.setField(m, "id", id);
        return m;
    }

    @Test
    @DisplayName("켜진 추천 미션을 순서대로, 요청 언어 이름으로")
    void getActive() {
        when(repository.findAllByActiveTrueOrderBySortOrderAscIdAsc())
                .thenReturn(
                        List.of(
                                mission(1, MissionExecutionMode.SIMPLE, null),
                                mission(2, MissionExecutionMode.TIMED, 5)));

        List<RecommendedMissionResponse> result = service.getActive("en");

        assertThat(result)
                .extracting(
                        RecommendedMissionResponse::id,
                        RecommendedMissionResponse::title,
                        RecommendedMissionResponse::executionMode,
                        RecommendedMissionResponse::targetMinutes)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                1L, "Drink a glass of water", MissionExecutionMode.SIMPLE, null),
                        org.assertj.core.groups.Tuple.tuple(
                                2L, "Drink a glass of water", MissionExecutionMode.TIMED, 5));
        assertThat(result.get(0).icon()).isEqualTo("💧");
        assertThat(result.get(0).categoryId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("원탭 시작 = 내 미션 생성 + 오늘 수행 시작")
    void start() {
        RecommendedMission rec = mission(2, MissionExecutionMode.TIMED, 5);
        when(repository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(rec));
        MissionResponse created = MissionResponse.builder().id(77L).build();
        when(missionService.createMissionFromRecommended(rec, "u1", "ko")).thenReturn(created);
        MissionExecutionResponse execution = MissionExecutionResponse.builder().id(9L).build();
        when(executionService.startExecutionToday(77L, "u1")).thenReturn(execution);

        RecommendedMissionStartResponse result = service.start("u1", 2L, "ko");

        assertThat(result.mission()).isSameAs(created);
        assertThat(result.execution()).isSameAs(execution);
    }

    @Test
    @DisplayName("없거나 꺼진 추천 미션 → 404(050501), 미션을 만들지 않는다")
    void startNotFound() {
        when(repository.findByIdAndActiveTrue(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start("u1", 9L, null))
                .isInstanceOf(RecommendedMissionException.class)
                .hasFieldOrPropertyWithValue("code", RecommendedMissionException.CODE_NOT_FOUND);
        verify(missionService, never()).createMissionFromRecommended(any(), any(), any());
    }

    @Test
    @DisplayName("LUT-803: 원탭 완료 대상 — 켜진 체크 추천은 통과, 타이머는 400(050503), 없으면 404(050501)")
    void requireActiveSimple() {
        RecommendedMission simple = mission(1, MissionExecutionMode.SIMPLE, null);
        when(repository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(simple));
        when(repository.findByIdAndActiveTrue(2L))
                .thenReturn(Optional.of(mission(2, MissionExecutionMode.TIMED, 5)));
        when(repository.findByIdAndActiveTrue(9L)).thenReturn(Optional.empty());

        assertThat(service.requireActiveSimple(1L)).isSameAs(simple);
        assertThatThrownBy(() -> service.requireActiveSimple(2L))
                .isInstanceOf(RecommendedMissionException.class)
                .hasFieldOrPropertyWithValue("code", RecommendedMissionException.CODE_NOT_SIMPLE)
                .hasFieldOrPropertyWithValue(
                        "status", org.springframework.http.HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.requireActiveSimple(9L))
                .isInstanceOf(RecommendedMissionException.class)
                .hasFieldOrPropertyWithValue("code", RecommendedMissionException.CODE_NOT_FOUND);
        // 확인만 한다 — 미션을 만들지 않는다
        verify(missionService, never()).createMissionFromRecommended(any(), any(), any());
    }
}
