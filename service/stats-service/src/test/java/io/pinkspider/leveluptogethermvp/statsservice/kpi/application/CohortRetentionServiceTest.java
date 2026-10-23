package io.pinkspider.leveluptogethermvp.statsservice.kpi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.statsservice.activity.config.UserActivityProperties;
import io.pinkspider.leveluptogethermvp.statsservice.activity.domain.entity.UserDailyActivity;
import io.pinkspider.leveluptogethermvp.statsservice.activity.infrastructure.UserDailyActivityRepository;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.KpiDates;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.AttendanceDay;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.CohortRetention;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.CohortRetentionId;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.enums.RetentionMetric;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.enums.RetentionSource;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.CohortRetentionRepository;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.GamificationSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.UserSourceReader;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CohortRetentionService 테스트 (LUT-601)")
class CohortRetentionServiceTest {

    private static final LocalDate COHORT = LocalDate.of(2026, 10, 1);

    @Mock private CohortRetentionRepository retentionRepository;
    @Mock private UserDailyActivityRepository activityRepository;
    @Mock private UserSourceReader userReader;
    @Mock private GamificationSourceReader gamificationReader;

    private UserActivityProperties properties;
    private CohortRetentionService service;

    @BeforeEach
    void setUp() {
        properties = new UserActivityProperties();
        service =
                new CohortRetentionService(
                        retentionRepository,
                        activityRepository,
                        properties,
                        userReader,
                        gamificationReader);
    }

    @Test
    @DisplayName("countRetained: 창 안 어느 날이든 활동한 코호트 멤버 수 (중복 없이), 멤버 없으면 0")
    void countRetained() {
        Map<LocalDate, Set<String>> active = new HashMap<>();
        active.put(COHORT.plusDays(1), Set.of("u1", "x"));
        active.put(COHORT.plusDays(3), Set.of("u1", "u2"));
        List<String> members = List.of("u1", "u2", "u3");

        assertThat(
                        CohortRetentionService.countRetained(
                                members, COHORT.plusDays(1), COHORT.plusDays(7), active))
                .isEqualTo(2);
        assertThat(
                        CohortRetentionService.countRetained(
                                members, COHORT.plusDays(1), COHORT.plusDays(1), active))
                .isEqualTo(1);
        assertThat(
                        CohortRetentionService.countRetained(
                                members, COHORT.plusDays(2), COHORT.plusDays(2), active))
                .isZero();
        assertThat(
                        CohortRetentionService.countRetained(
                                List.of(), COHORT, COHORT.plusDays(30), active))
                .isZero();
    }

    @Test
    @DisplayName("닫힌 지표만 저장: ATTENDANCE 는 항상, REQUEST 는 활동 데이터 시작일 이후 창만. 기존 행은 갱신")
    void rebuild() {
        LocalDate lastComplete = COHORT.plusDays(7); // D1 D3 D7 W1 닫힘, D14 D30 W2_4 열림
        when(userReader.findSignupUserIds(KpiDates.utcStart(COHORT), KpiDates.utcEnd(COHORT)))
                .thenReturn(List.of("u1", "u2"));
        properties.setExcludedUserIds(Set.of("bot"));
        when(activityRepository.findAllByActivityDateBetween(COHORT.plusDays(1), lastComplete))
                .thenReturn(
                        List.of(
                                UserDailyActivity.create("u1", COHORT.plusDays(3), List.of("web")),
                                UserDailyActivity.create("bot", COHORT.plusDays(3), List.of("web")),
                                UserDailyActivity.create(
                                        "u2", COHORT.plusDays(7), List.of("ios"))));
        when(gamificationReader.findAttendance(COHORT.plusDays(1), lastComplete))
                .thenReturn(
                        List.of(
                                new AttendanceDay("u1", COHORT.plusDays(1)),
                                new AttendanceDay("u2", COHORT.plusDays(1))));
        // 요청 데이터는 D3 날부터 존재 → D1 REQUEST 는 계산하지 않는다 (W1 은 창 시작이 D1 이라 역시 제외)
        when(activityRepository.findMinActivityDate()).thenReturn(Optional.of(COHORT.plusDays(3)));
        CohortRetention existing =
                CohortRetention.create(
                        COHORT, RetentionMetric.D1, RetentionSource.ATTENDANCE, 0, 0);
        when(retentionRepository.findById(any(CohortRetentionId.class)))
                .thenReturn(Optional.empty());
        when(retentionRepository.findById(new CohortRetentionId(COHORT, "D1", "ATTENDANCE")))
                .thenReturn(Optional.of(existing));
        when(retentionRepository.save(any(CohortRetention.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        int rows = service.rebuild(COHORT, COHORT, lastComplete);

        // ATTENDANCE: D1 D3 D7 W1 = 4, REQUEST: D3 D7 = 2
        assertThat(rows).isEqualTo(6);
        assertThat(existing.getCohortSize()).isEqualTo(2);
        assertThat(existing.getRetained()).isEqualTo(2);
        ArgumentCaptor<CohortRetention> captor = ArgumentCaptor.forClass(CohortRetention.class);
        verify(retentionRepository, org.mockito.Mockito.times(6)).save(captor.capture());
        Map<String, Integer> retainedByKey = new HashMap<>();
        captor.getAllValues()
                .forEach(
                        r ->
                                retainedByKey.put(
                                        r.getId().getMetric() + "/" + r.getId().getSource(),
                                        r.getRetained()));
        assertThat(retainedByKey)
                .containsEntry("D1/ATTENDANCE", 2)
                .containsEntry("D3/ATTENDANCE", 0)
                .containsEntry("W1/ATTENDANCE", 2)
                .containsEntry("D3/REQUEST", 1)
                .containsEntry("D7/REQUEST", 1)
                .doesNotContainKey("D1/REQUEST")
                .doesNotContainKey("D14/ATTENDANCE");
    }

    @Test
    @DisplayName("from>to 면 0, 창 전체가 미완성이면 원천 조회 없이 0행")
    void edges() {
        assertThat(service.rebuild(COHORT.plusDays(1), COHORT, COHORT.plusDays(30))).isZero();

        when(userReader.findSignupUserIds(any(), any())).thenReturn(List.of("u1"));
        when(activityRepository.findMinActivityDate()).thenReturn(Optional.empty());
        assertThat(service.rebuild(COHORT, COHORT, COHORT)).isZero();
        verify(activityRepository, never()).findAllByActivityDateBetween(any(), any());
        verify(gamificationReader, never()).findAttendance(any(), any());
    }
}
