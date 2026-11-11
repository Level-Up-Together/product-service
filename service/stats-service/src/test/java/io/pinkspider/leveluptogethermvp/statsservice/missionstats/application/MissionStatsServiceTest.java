package io.pinkspider.leveluptogethermvp.statsservice.missionstats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.statsservice.activity.config.UserActivityProperties;
import io.pinkspider.leveluptogethermvp.statsservice.api.exception.StatsInvalidParamException;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.ExcludedUsers;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionCategoryName;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionCompletedGroup;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionCreatedGroup;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionDailyCounts;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionScheduledGroup;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.MetaSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.MissionSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsSummaryResponse;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsSummaryResponse.Breakdown;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsSummaryResponse.Daily;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionStatsService 테스트 (LUT-790)")
class MissionStatsServiceTest {

    private static final LocalDate D1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 11, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 11, 3);
    private static final LocalDateTime FROM_UTC = LocalDateTime.of(2026, 10, 31, 15, 0);
    private static final LocalDateTime TO_UTC = LocalDateTime.of(2026, 11, 3, 15, 0);

    @Mock private MissionSourceReader missionReader;
    @Mock private MetaSourceReader metaReader;

    private MissionStatsService service;

    @BeforeEach
    void setUp() {
        UserActivityProperties properties = new UserActivityProperties();
        properties.setExcludedUserIds(Set.of("staff"));
        service = new MissionStatsService(missionReader, metaReader, properties);
    }

    private void stubGroups(
            List<MissionCreatedGroup> created,
            List<MissionCompletedGroup> completed,
            List<MissionScheduledGroup> scheduled,
            MissionDailyCounts kpi) {
        when(missionReader.findCreatedGroups(eq(FROM_UTC), eq(TO_UTC), any())).thenReturn(created);
        when(missionReader.findCompletedGroups(eq(FROM_UTC), eq(TO_UTC), any()))
                .thenReturn(completed);
        when(missionReader.findScheduledGroups(eq(D1), eq(D3), any())).thenReturn(scheduled);
        when(missionReader.countCompletions(eq(FROM_UTC), eq(TO_UTC), any())).thenReturn(kpi);
    }

    @Test
    @DisplayName("요약 — 생성(삭제·고정 포함)·완료(KPI 같은 SQL)·예정일 기준 완료율")
    void summaryTotals() {
        stubGroups(
                List.of(
                        new MissionCreatedGroup(D1, "PERSONAL", "TIMED", 3L, false, false, 4),
                        new MissionCreatedGroup(D1, "MISSION_BOOK", "SIMPLE", 3L, true, true, 2),
                        new MissionCreatedGroup(D3, "GUILD", "TIMED", null, false, false, 1)),
                List.of(
                        new MissionCompletedGroup(D1, "PERSONAL", "TIMED", 3L, 5, 1),
                        new MissionCompletedGroup(D2, "MISSION_BOOK", "SIMPLE", 3L, 3, 0),
                        new MissionCompletedGroup(D2, "GUILD", "TIMED", null, 2, 2)),
                List.of(
                        new MissionScheduledGroup(D1, "PERSONAL", "TIMED", 3L, 8, 5),
                        new MissionScheduledGroup(D2, "MISSION_BOOK", "SIMPLE", 3L, 4, 3)),
                new MissionDailyCounts(10, 4));
        when(metaReader.findCategoryNames(anyCollection()))
                .thenReturn(Map.of(3L, new MissionCategoryName("운동", "Workout")));

        MissionStatsSummaryResponse r = service.summary(D3, D1);

        assertThat(r.from()).isEqualTo(D1);
        assertThat(r.to()).isEqualTo(D3);
        assertThat(r.created()).isEqualTo(new MissionStatsSummaryResponse.Created(7, 2, 2, 5));
        assertThat(r.completions())
                .isEqualTo(new MissionStatsSummaryResponse.Completions(10, 3, 4, 2.5));
        assertThat(r.completionRate())
                .isEqualTo(new MissionStatsSummaryResponse.CompletionRate(12, 8, 66.7));
    }

    @Test
    @DisplayName("일별 — 기간의 모든 날짜(0 포함), 완료는 완료 시각·완료율은 예정일 기준")
    void daily() {
        stubGroups(
                List.of(new MissionCreatedGroup(D1, "PERSONAL", "TIMED", 3L, false, false, 4)),
                List.of(new MissionCompletedGroup(D2, "PERSONAL", "TIMED", 3L, 5, 0)),
                List.of(new MissionScheduledGroup(D1, "PERSONAL", "TIMED", 3L, 8, 5)),
                new MissionDailyCounts(5, 1));
        when(metaReader.findCategoryNames(anyCollection())).thenReturn(Map.of());

        List<Daily> daily = service.summary(D1, D3).daily();

        assertThat(daily)
                .containsExactly(
                        new Daily(D1, 4, 0, 8, 5, 62.5),
                        new Daily(D2, 0, 5, 0, 0, null),
                        new Daily(D3, 0, 0, 0, 0, null));
    }

    @Test
    @DisplayName("종류·수행 방식 — 정해진 행을 0 이어도 항상 내고, 모르는 값은 뒤에 붙인다")
    void fixedBreakdowns() {
        stubGroups(
                List.of(new MissionCreatedGroup(D1, "GUILD", "TIMED", null, false, false, 2)),
                List.of(new MissionCompletedGroup(D1, "GUILD", "LEGACY", null, 1, 0)),
                List.of(new MissionScheduledGroup(D1, "GUILD", "TIMED", null, 4, 1)),
                new MissionDailyCounts(1, 1));
        when(metaReader.findCategoryNames(anyCollection())).thenReturn(Map.of());

        MissionStatsSummaryResponse r = service.summary(D1, D3);

        assertThat(r.byType())
                .containsExactly(
                        new Breakdown("PERSONAL", null, null, 0, 0, 0, 0, null),
                        new Breakdown("GUILD", null, null, 2, 1, 4, 1, 25.0),
                        new Breakdown("MISSION_BOOK", null, null, 0, 0, 0, 0, null));
        assertThat(r.byMode())
                .containsExactly(
                        new Breakdown("TIMED", null, null, 2, 0, 4, 1, 25.0),
                        new Breakdown("SIMPLE", null, null, 0, 0, 0, 0, null),
                        new Breakdown("LEGACY", null, null, 0, 1, 0, 0, null));
    }

    @Test
    @DisplayName("카테고리 — 지금 이름(meta), 완료 많은 순 → 생성 많은 순 → ID 순(없음 = null 은 뒤)")
    @SuppressWarnings("unchecked")
    void categoryBreakdown() {
        stubGroups(
                List.of(
                        new MissionCreatedGroup(D1, "PERSONAL", "TIMED", null, false, false, 9),
                        new MissionCreatedGroup(D1, "PERSONAL", "TIMED", 3L, false, false, 1),
                        new MissionCreatedGroup(D1, "PERSONAL", "TIMED", 5L, false, false, 6),
                        new MissionCreatedGroup(D1, "PERSONAL", "TIMED", 7L, false, false, 2)),
                List.of(
                        new MissionCompletedGroup(D1, "PERSONAL", "TIMED", 3L, 4, 0),
                        new MissionCompletedGroup(D1, "PERSONAL", "TIMED", 5L, 4, 0),
                        new MissionCompletedGroup(D1, "PERSONAL", "TIMED", 7L, 9, 0)),
                List.of(),
                new MissionDailyCounts(17, 3));
        when(metaReader.findCategoryNames(anyCollection()))
                .thenReturn(
                        Map.of(
                                3L, new MissionCategoryName("운동", "Workout"),
                                5L, new MissionCategoryName("공부", null)));

        List<Breakdown> rows = service.summary(D1, D3).byCategory();

        assertThat(rows)
                .extracting(Breakdown::key, Breakdown::name, Breakdown::nameEn)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("7", null, null),
                        org.assertj.core.groups.Tuple.tuple("5", "공부", null),
                        org.assertj.core.groups.Tuple.tuple("3", "운동", "Workout"),
                        org.assertj.core.groups.Tuple.tuple(null, null, null));
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(metaReader).findCategoryNames(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(3L, 5L, 7L);
    }

    @Test
    @DisplayName("제외 유저는 config 목록을 그대로 넘긴다 (KPI 와 같은 규칙)")
    void passesExcludedUsers() {
        stubGroups(List.of(), List.of(), List.of(), MissionDailyCounts.ZERO);
        when(metaReader.findCategoryNames(anyCollection())).thenReturn(Map.of());

        MissionStatsSummaryResponse r = service.summary(D1, D3);

        ArgumentCaptor<ExcludedUsers> excluded = ArgumentCaptor.forClass(ExcludedUsers.class);
        verify(missionReader).countCompletions(eq(FROM_UTC), eq(TO_UTC), excluded.capture());
        assertThat(excluded.getValue().ids()).containsExactly("staff");
        assertThat(r.completions().avgPerUser()).isNull();
        assertThat(r.completionRate().rate()).isNull();
        assertThat(r.byCategory()).isEmpty();
    }

    @Test
    @DisplayName("기간이 400일을 넘으면 읽지 않고 400 100101")
    void rejectsLongPeriod() {
        assertThatThrownBy(() -> service.summary(D1, D1.plusDays(400)))
                .isInstanceOf(StatsInvalidParamException.class);
        verifyNoInteractions(missionReader, metaReader);
    }
}
