package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.missionservice.api.exception.RecommendedMissionException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionAdminResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.RecommendedMission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.RecommendedMissionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendedMissionAdminService — 어드민 추천 미션 관리 (LUT-782)")
class RecommendedMissionAdminServiceTest {

    @Mock RecommendedMissionRepository repository;
    @Mock MissionRepository missionRepository;
    @Mock MissionCategoryService missionCategoryService;
    @InjectMocks RecommendedMissionAdminService service;

    private static RecommendedMission mission(long id, int order, boolean active) {
        RecommendedMission m =
                RecommendedMission.create(
                        "5분 독서",
                        null,
                        null,
                        null,
                        null,
                        MissionExecutionMode.TIMED,
                        5,
                        "📖",
                        active,
                        order);
        TestReflectionUtils.setField(m, "id", id);
        return m;
    }

    private static RecommendedMissionRequest request(
            MissionExecutionMode mode, Integer minutes, Long categoryId, Boolean active) {
        return new RecommendedMissionRequest(
                " 10분 산책 ", "Take a walk", "", null, categoryId, mode, minutes, " 🚶 ", active);
    }

    private static List<Object[]> stats(Object[]... rows) {
        return new ArrayList<>(List.of(rows));
    }

    @Test
    @DisplayName("목록 — 순서대로 전부, 항목별 시작·완료 수(없으면 0)")
    void getAll() {
        when(repository.findAllByOrderBySortOrderAscIdAsc())
                .thenReturn(List.of(mission(1, 1, true), mission(2, 2, false)));
        when(missionRepository.countRecommendedMissionStats())
                .thenReturn(stats(new Object[] {1L, 12L, 7L}));

        List<RecommendedMissionAdminResponse> result = service.getAll();

        assertThat(result)
                .extracting(
                        RecommendedMissionAdminResponse::id,
                        RecommendedMissionAdminResponse::startedCount,
                        RecommendedMissionAdminResponse::completedCount,
                        RecommendedMissionAdminResponse::active)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, 12L, 7L, true),
                        org.assertj.core.groups.Tuple.tuple(2L, 0L, 0L, false));
    }

    @Test
    @DisplayName("단건 조회, 없으면 404")
    void get() {
        when(repository.findById(1L)).thenReturn(Optional.of(mission(1, 1, true)));
        when(missionRepository.countRecommendedMissionStats()).thenReturn(stats());
        assertThat(service.get(1L).titleKo()).isEqualTo("5분 독서");

        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(9L))
                .isInstanceOf(RecommendedMissionException.class)
                .hasFieldOrPropertyWithValue("code", RecommendedMissionException.CODE_NOT_FOUND);
    }

    @Nested
    @DisplayName("작성·수정")
    class Save {

        @Test
        @DisplayName("작성 — 맨 뒤 순서, 활성 미지정은 켬, 앞뒤 공백 제거")
        void create() {
            when(repository.findMaxSortOrder()).thenReturn(6);
            when(repository.save(any(RecommendedMission.class))).thenAnswer(i -> i.getArgument(0));

            RecommendedMissionAdminResponse result =
                    service.create(request(MissionExecutionMode.TIMED, 10, null, null));

            ArgumentCaptor<RecommendedMission> captor =
                    ArgumentCaptor.forClass(RecommendedMission.class);
            verify(repository).save(captor.capture());
            RecommendedMission saved = captor.getValue();
            assertThat(saved.getSortOrder()).isEqualTo(7);
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getTitleKo()).isEqualTo("10분 산책");
            assertThat(saved.getIcon()).isEqualTo("🚶");
            assertThat(saved.getTitleJa()).isNull();
            assertThat(result.startedCount()).isZero();
        }

        @Test
        @DisplayName("작성 — 활성 false 를 그대로")
        void createInactive() {
            when(repository.findMaxSortOrder()).thenReturn(0);
            when(repository.save(any(RecommendedMission.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(
                            service.create(request(MissionExecutionMode.SIMPLE, null, null, false))
                                    .active())
                    .isFalse();
        }

        @Test
        @DisplayName("타이머인데 목표 시간 없음 → 400")
        void timerWithoutMinutes() {
            assertThatThrownBy(
                            () ->
                                    service.create(
                                            request(MissionExecutionMode.TIMED, null, null, null)))
                    .isInstanceOf(RecommendedMissionException.class)
                    .hasFieldOrPropertyWithValue("code", RecommendedMissionException.CODE_INVALID)
                    .hasMessage("error.mission.recommended.target_minutes_required");
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("카테고리 — 켜진 것만, 꺼짐·없음 → 400")
        void category() {
            when(missionCategoryService.getCategory(1L))
                    .thenReturn(MissionCategoryResponse.builder().id(1L).isActive(true).build());
            when(missionCategoryService.getCategory(2L))
                    .thenReturn(MissionCategoryResponse.builder().id(2L).isActive(false).build());
            when(missionCategoryService.getCategory(3L)).thenThrow(new RuntimeException("없음"));
            when(repository.findMaxSortOrder()).thenReturn(0);
            when(repository.save(any(RecommendedMission.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(
                            service.create(request(MissionExecutionMode.SIMPLE, null, 1L, null))
                                    .categoryId())
                    .isEqualTo(1L);
            for (Long id : List.of(2L, 3L)) {
                assertThatThrownBy(
                                () ->
                                        service.create(
                                                request(
                                                        MissionExecutionMode.SIMPLE,
                                                        null,
                                                        id,
                                                        null)))
                        .isInstanceOf(RecommendedMissionException.class)
                        .hasMessage("error.mission.recommended.invalid_category");
            }
        }

        @Test
        @DisplayName("수정 — 활성 미지정은 그대로, 체크로 바꾸면 목표 시간을 버린다")
        void update() {
            RecommendedMission existing = mission(1, 1, false);
            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(missionRepository.countRecommendedMissionStats()).thenReturn(stats());

            RecommendedMissionAdminResponse result =
                    service.update(1L, request(MissionExecutionMode.SIMPLE, 10, null, null));

            assertThat(result.active()).isFalse();
            assertThat(result.targetMinutes()).isNull();
            assertThat(existing.getTitleKo()).isEqualTo("10분 산책");

            service.update(1L, request(MissionExecutionMode.TIMED, 5, null, true));
            assertThat(existing.isActive()).isTrue();
        }
    }

    @Test
    @DisplayName("삭제·활성 토글")
    void deleteAndToggle() {
        RecommendedMission m = mission(1, 1, true);
        when(repository.findById(1L)).thenReturn(Optional.of(m));
        when(missionRepository.countRecommendedMissionStats()).thenReturn(stats());

        assertThat(service.toggleActive(1L).active()).isFalse();
        service.delete(1L);
        verify(repository).delete(m);
    }

    @Nested
    @DisplayName("순서 바꾸기")
    class Reorder {

        @Test
        @DisplayName("보낸 순서대로 1부터 다시 매긴다")
        void reorder() {
            RecommendedMission a = mission(1, 1, true);
            RecommendedMission b = mission(2, 2, true);
            RecommendedMission c = mission(3, 3, false);
            when(repository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of(a, b, c));
            when(missionRepository.countRecommendedMissionStats()).thenReturn(stats());

            List<RecommendedMissionAdminResponse> result = service.reorder(List.of(3L, 1L, 2L));

            assertThat(result)
                    .extracting(RecommendedMissionAdminResponse::id)
                    .containsExactly(3L, 1L, 2L);
            assertThat(c.getSortOrder()).isEqualTo(1);
            assertThat(a.getSortOrder()).isEqualTo(2);
            assertThat(b.getSortOrder()).isEqualTo(3);
        }

        @Test
        @DisplayName("빠진 ID·없는 ID·중복 → 400")
        void mismatch() {
            when(repository.findAllByOrderBySortOrderAscIdAsc())
                    .thenReturn(List.of(mission(1, 1, true), mission(2, 2, true)));

            for (List<Long> ids : List.of(List.of(1L), List.of(1L, 9L), List.of(1L, 1L, 2L))) {
                assertThatThrownBy(() -> service.reorder(ids))
                        .isInstanceOf(RecommendedMissionException.class)
                        .hasMessage("error.mission.recommended.invalid_reorder");
            }
        }
    }
}
