package io.pinkspider.leveluptogethermvp.statsservice.kpi.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.DailyKpiSnapshot;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.DailyKpiSnapshotRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("KpiMetricsExporter 테스트 (LUT-605)")
class KpiMetricsExporterTest {

    /** 2026-10-24 01:00 KST = 2026-10-23 16:00 UTC */
    private static final Instant NOW = Instant.parse("2026-10-23T16:00:00Z");

    @Mock private DailyKpiSnapshotRepository repository;

    private SimpleMeterRegistry registry;
    private MutableClock clock;
    private KpiMetricsExporter exporter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        clock = new MutableClock(NOW);
        exporter = new KpiMetricsExporter(repository, registry, clock);
    }

    private static DailyKpiSnapshot snapshot(LocalDate date) {
        return DailyKpiSnapshot.builder()
                .snapshotDate(date)
                .signups(3)
                .withdrawals(1)
                .activeUsers(140)
                .dau(12)
                .wau(30)
                .mau(55)
                .attendanceDau(9)
                .missionCompletions(40)
                .missionActiveUsers(11)
                .newSubscriptions(1)
                .activeSubscriptions(2)
                .churnedSubscriptions(0)
                .payingUsers(1)
                .revenueKrw(new BigDecimal("4900.00"))
                .build();
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }

    @Test
    @DisplayName("최신 스냅샷 값이 lut.kpi.* 게이지로 노출된다")
    void gauges_reflectLatestSnapshot() {
        when(repository.findTopByOrderBySnapshotDateDesc())
                .thenReturn(Optional.of(snapshot(LocalDate.of(2026, 10, 23))));

        assertThat(gauge("lut.kpi.dau")).isEqualTo(12);
        assertThat(gauge("lut.kpi.wau")).isEqualTo(30);
        assertThat(gauge("lut.kpi.mau")).isEqualTo(55);
        assertThat(gauge("lut.kpi.attendance.dau")).isEqualTo(9);
        assertThat(gauge("lut.kpi.signups")).isEqualTo(3);
        assertThat(gauge("lut.kpi.withdrawals")).isEqualTo(1);
        assertThat(gauge("lut.kpi.active.users")).isEqualTo(140);
        assertThat(gauge("lut.kpi.mission.completions")).isEqualTo(40);
        assertThat(gauge("lut.kpi.mission.active.users")).isEqualTo(11);
        assertThat(gauge("lut.kpi.paying.users")).isEqualTo(1);
        assertThat(gauge("lut.kpi.active.subscriptions")).isEqualTo(2);
        assertThat(gauge("lut.kpi.new.subscriptions")).isEqualTo(1);
        assertThat(gauge("lut.kpi.churned.subscriptions")).isEqualTo(0);
        assertThat(gauge("lut.kpi.revenue.krw")).isEqualTo(4900.0);
    }

    @Test
    @DisplayName("snapshot_age_days 는 KST 오늘 − snapshot_date (어제 행이면 1), date 는 KST 자정 epoch")
    void ageAndDateGauges() {
        when(repository.findTopByOrderBySnapshotDateDesc())
                .thenReturn(Optional.of(snapshot(LocalDate.of(2026, 10, 23))));

        // NOW 는 KST 10/24 01:00 → 어제(10/23) 행이면 age 1
        assertThat(gauge("lut.kpi.snapshot.age.days")).isEqualTo(1);
        // 2026-10-23 00:00 KST = 2026-10-22 15:00 UTC
        assertThat(gauge("lut.kpi.snapshot.date.epoch.seconds"))
                .isEqualTo(Instant.parse("2026-10-22T15:00:00Z").getEpochSecond());
    }

    @Test
    @DisplayName("스냅샷이 사흘 전이면 age 3 — 스케줄러 실패 알람의 근거")
    void ageGauge_staleSnapshot() {
        when(repository.findTopByOrderBySnapshotDateDesc())
                .thenReturn(Optional.of(snapshot(LocalDate.of(2026, 10, 21))));

        assertThat(gauge("lut.kpi.snapshot.age.days")).isEqualTo(3);
    }

    @Test
    @DisplayName("스냅샷이 없으면 모든 게이지가 NaN")
    void noSnapshot_allNaN() {
        when(repository.findTopByOrderBySnapshotDateDesc()).thenReturn(Optional.empty());

        assertThat(gauge("lut.kpi.dau")).isNaN();
        assertThat(gauge("lut.kpi.revenue.krw")).isNaN();
        assertThat(gauge("lut.kpi.snapshot.age.days")).isNaN();
        assertThat(gauge("lut.kpi.snapshot.date.epoch.seconds")).isNaN();
    }

    @Test
    @DisplayName("값이 null 인 컬럼은 NaN 으로 노출한다")
    void nullColumn_isNaN() {
        DailyKpiSnapshot partial =
                DailyKpiSnapshot.builder().snapshotDate(LocalDate.of(2026, 10, 23)).dau(5).build();
        when(repository.findTopByOrderBySnapshotDateDesc()).thenReturn(Optional.of(partial));

        assertThat(gauge("lut.kpi.dau")).isEqualTo(5);
        assertThat(gauge("lut.kpi.mau")).isNaN();
        assertThat(gauge("lut.kpi.revenue.krw")).isNaN();
    }

    @Test
    @DisplayName("TTL 안의 반복 스크레이프는 DB 를 한 번만 읽고, TTL 이 지나면 다시 읽는다")
    void cache_withinTtl_singleQuery() {
        when(repository.findTopByOrderBySnapshotDateDesc())
                .thenReturn(Optional.of(snapshot(LocalDate.of(2026, 10, 23))));

        gauge("lut.kpi.dau");
        gauge("lut.kpi.mau");
        gauge("lut.kpi.snapshot.age.days");
        verify(repository, times(1)).findTopByOrderBySnapshotDateDesc();

        clock.advance(KpiMetricsExporter.CACHE_TTL.minusSeconds(1));
        gauge("lut.kpi.dau");
        verify(repository, times(1)).findTopByOrderBySnapshotDateDesc();

        clock.advance(Duration.ofSeconds(2));
        gauge("lut.kpi.dau");
        verify(repository, times(2)).findTopByOrderBySnapshotDateDesc();
    }

    @Test
    @DisplayName("DB 조회가 실패하면 예외를 삼키고 NaN 을 내보내며, TTL 동안 재시도하지 않는다")
    void repositoryFailure_isSwallowed() {
        when(repository.findTopByOrderBySnapshotDateDesc())
                .thenThrow(new IllegalStateException("db down"));

        assertThat(gauge("lut.kpi.dau")).isNaN();
        assertThat(gauge("lut.kpi.snapshot.age.days")).isNaN();
        verify(repository, times(1)).findTopByOrderBySnapshotDateDesc();
    }

    @Test
    @DisplayName("Prometheus 이름 규칙으로 변환되는 게이지 16종이 전부 등록된다")
    void allGaugesRegistered() {
        long count =
                registry.getMeters().stream()
                        .filter(m -> m.getId().getName().startsWith(KpiMetricsExporter.PREFIX))
                        .count();
        assertThat(count).isEqualTo(16);
        assertThat(registry.find("lut.kpi.dau").gauge()).isNotNull();
        assertThat(registry.find("lut.kpi.dau").gauge().getId().getDescription()).isNotBlank();
    }

    @Test
    @DisplayName("Spring 용 2-인자 생성자는 시스템 시계를 쓴다")
    void springConstructor() {
        SimpleMeterRegistry other = new SimpleMeterRegistry();
        KpiMetricsExporter viaSpring = new KpiMetricsExporter(repository, other);
        when(repository.findTopByOrderBySnapshotDateDesc()).thenReturn(Optional.empty());

        assertThat(viaSpring.latest()).isNull();
        assertThat(other.find("lut.kpi.dau").gauge()).isNotNull();
    }

    /** 테스트용 가변 시계 */
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
