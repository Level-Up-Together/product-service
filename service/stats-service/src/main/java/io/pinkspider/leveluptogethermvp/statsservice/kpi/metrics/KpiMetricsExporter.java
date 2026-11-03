package io.pinkspider.leveluptogethermvp.statsservice.kpi.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.KpiDates;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.DailyKpiSnapshot;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.DailyKpiSnapshotRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * LUT-605: 최신 {@code daily_kpi_snapshot} 1행을 Micrometer 게이지({@code lut_kpi_*})로 노출한다. Grafana(Alloy
 * 가 {@code /showmethemoney/prometheus} 를 긁어 감)에서 "DAU 급락"·"스냅샷이 안 만들어짐" 알람을 서버 지표와 같은 곳에서 받기 위함.
 *
 * <p>스크레이프마다 DB 를 치지 않도록 {@link #CACHE_TTL} 동안 캐시한다. 조회 실패는 삼키고 NaN 을 내보낸다 — 지표 수집이 스크레이프를 실패시키면 안
 * 되고, NaN 은 Prometheus 에서 "값 없음" 으로 자연스럽게 빠진다. {@code @Scheduled} 를 쓰지 않으므로 인스턴스마다 독립적으로
 * 동작한다(ShedLock 불필요).
 *
 * <p>{@code lut_kpi_snapshot_age_days} 는 KST 오늘 − snapshot_date. 정상이면 1(어제 행). 2 이상이면 00:40 스케줄러가
 * 실패한 것.
 */
@Slf4j
@Component
public class KpiMetricsExporter {

    static final Duration CACHE_TTL = Duration.ofMinutes(5);
    static final String PREFIX = "lut.kpi.";

    private final DailyKpiSnapshotRepository snapshotRepository;
    private final Clock clock;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    record Cached(Instant loadedAt, DailyKpiSnapshot snapshot) {}

    @Autowired
    public KpiMetricsExporter(
            DailyKpiSnapshotRepository snapshotRepository, MeterRegistry registry) {
        this(snapshotRepository, registry, Clock.systemUTC());
    }

    KpiMetricsExporter(
            DailyKpiSnapshotRepository snapshotRepository, MeterRegistry registry, Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.clock = clock;
        register(registry);
    }

    private void register(MeterRegistry registry) {
        gauge(registry, "dau", "요청 기준 DAU(최신 스냅샷)", DailyKpiSnapshot::getDau);
        gauge(registry, "wau", "요청 기준 WAU(최근 7일)", DailyKpiSnapshot::getWau);
        gauge(registry, "mau", "요청 기준 MAU(최근 30일)", DailyKpiSnapshot::getMau);
        gauge(registry, "attendance.dau", "출석(홈 방문) 기준 DAU", DailyKpiSnapshot::getAttendanceDau);
        gauge(registry, "signups", "가입자 수(그날)", DailyKpiSnapshot::getSignups);
        gauge(registry, "withdrawals", "탈퇴자 수(그날)", DailyKpiSnapshot::getWithdrawals);
        gauge(registry, "active.users", "누적 ACTIVE 유저 수", DailyKpiSnapshot::getActiveUsers);
        gauge(
                registry,
                "mission.completions",
                "미션 완료 수(그날)",
                DailyKpiSnapshot::getMissionCompletions);
        gauge(
                registry,
                "mission.active.users",
                "미션 완료 유저 수(그날)",
                DailyKpiSnapshot::getMissionActiveUsers);
        gauge(registry, "paying.users", "결제 유저 수(그날)", DailyKpiSnapshot::getPayingUsers);
        gauge(
                registry,
                "active.subscriptions",
                "활성 구독 수",
                DailyKpiSnapshot::getActiveSubscriptions);
        gauge(registry, "new.subscriptions", "신규 구독(그날)", DailyKpiSnapshot::getNewSubscriptions);
        gauge(
                registry,
                "churned.subscriptions",
                "해지·만료 구독(그날)",
                DailyKpiSnapshot::getChurnedSubscriptions);
        Gauge.builder(
                        PREFIX + "revenue.krw",
                        this,
                        e -> e.decimalValue(DailyKpiSnapshot::getRevenueKrw))
                .description("KRW 환산 매출(그날, 환불 음수 반영)")
                .register(registry);
        Gauge.builder(PREFIX + "snapshot.age.days", this, KpiMetricsExporter::snapshotAgeDays)
                .description("KST 오늘 − 최신 snapshot_date. 정상 1, 2 이상이면 스케줄러 실패")
                .register(registry);
        Gauge.builder(
                        PREFIX + "snapshot.date.epoch.seconds",
                        this,
                        KpiMetricsExporter::snapshotDateEpochSeconds)
                .description("최신 snapshot_date 의 KST 자정 epoch seconds")
                .register(registry);
    }

    private void gauge(
            MeterRegistry registry,
            String name,
            String description,
            Function<DailyKpiSnapshot, Integer> getter) {
        Gauge.builder(PREFIX + name, this, e -> e.intValue(getter))
                .description(description)
                .register(registry);
    }

    double intValue(Function<DailyKpiSnapshot, Integer> getter) {
        DailyKpiSnapshot snapshot = latest();
        if (snapshot == null) {
            return Double.NaN;
        }
        Integer value = getter.apply(snapshot);
        return value == null ? Double.NaN : value.doubleValue();
    }

    double decimalValue(Function<DailyKpiSnapshot, BigDecimal> getter) {
        DailyKpiSnapshot snapshot = latest();
        if (snapshot == null) {
            return Double.NaN;
        }
        BigDecimal value = getter.apply(snapshot);
        return value == null ? Double.NaN : value.doubleValue();
    }

    double snapshotAgeDays() {
        DailyKpiSnapshot snapshot = latest();
        if (snapshot == null) {
            return Double.NaN;
        }
        LocalDate todayKst = LocalDate.now(clock.withZone(KpiDates.KST));
        return ChronoUnit.DAYS.between(snapshot.getSnapshotDate(), todayKst);
    }

    double snapshotDateEpochSeconds() {
        DailyKpiSnapshot snapshot = latest();
        if (snapshot == null) {
            return Double.NaN;
        }
        return snapshot.getSnapshotDate().atStartOfDay(KpiDates.KST).toEpochSecond();
    }

    /** 최신 스냅샷(캐시 TTL 안이면 재조회 없음). 조회 실패는 null(=NaN) 로 삼키고 warn 로그만 남긴다. */
    DailyKpiSnapshot latest() {
        Instant now = clock.instant();
        Cached cached = cache.get();
        if (cached != null && cached.loadedAt().plus(CACHE_TTL).isAfter(now)) {
            return cached.snapshot();
        }
        DailyKpiSnapshot loaded = null;
        try {
            loaded = snapshotRepository.findTopByOrderBySnapshotDateDesc().orElse(null);
        } catch (RuntimeException e) {
            log.warn("KPI 게이지용 스냅샷 조회 실패 — NaN 으로 노출: {}", e.toString());
        }
        cache.set(new Cached(now, loaded));
        return loaded;
    }
}
