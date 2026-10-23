package io.pinkspider.leveluptogethermvp.statsservice.kpi.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.statsservice.activity.config.UserActivityProperties;
import io.pinkspider.leveluptogethermvp.statsservice.activity.domain.entity.UserDailyActivity;
import io.pinkspider.leveluptogethermvp.statsservice.activity.infrastructure.UserDailyActivityRepository;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.KpiDates;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.DailyKpiValues;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionDailyCounts;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.RevenueAggregate;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.SubscriptionDailyCounts;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.DailyKpiSnapshot;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.FxRate;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.FxRateId;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.DailyKpiSnapshotRepository;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.FxRateRepository;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.GamificationSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.MissionSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.UserSourceReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-601: KST 하루치 KPI 를 전부 계산해 {@code daily_kpi_snapshot} 에 upsert 한다. 재실행은 같은 결과로 덮어쓴다(멱등).
 *
 * <p>원천 조회는 각 DB 의 읽기 전용 리더. 이 서비스의 트랜잭션은 stats_db 쓰기에만 걸린다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KpiSnapshotService {

    static final String KRW = "KRW";

    private final DailyKpiSnapshotRepository snapshotRepository;
    private final FxRateRepository fxRateRepository;
    private final UserDailyActivityRepository activityRepository;
    private final UserActivityProperties activityProperties;
    private final UserSourceReader userReader;
    private final GamificationSourceReader gamificationReader;
    private final MissionSourceReader missionReader;
    private final ObjectMapper objectMapper;

    @Transactional(transactionManager = "statsTransactionManager")
    public DailyKpiSnapshot build(LocalDate date) {
        DailyKpiValues values = compute(date);
        DailyKpiSnapshot snapshot =
                snapshotRepository
                        .findById(date)
                        .map(
                                existing -> {
                                    existing.apply(values);
                                    return existing;
                                })
                        .orElseGet(() -> DailyKpiSnapshot.create(date, values));
        DailyKpiSnapshot saved = snapshotRepository.save(snapshot);
        log.info(
                "KPI 스냅샷: date={}, signups={}, dau={}, mau={}, attendanceDau={}, revenueKrw={}",
                date,
                values.signups(),
                values.dau(),
                values.mau(),
                values.attendanceDau(),
                values.revenueKrw());
        return saved;
    }

    DailyKpiValues compute(LocalDate date) {
        LocalDateTime from = KpiDates.utcStart(date);
        LocalDateTime to = KpiDates.utcEnd(date);
        Set<String> excluded = activityProperties.getExcludedUserIds();

        // 활동 (요청 기반)
        List<UserDailyActivity> dayRows =
                filtered(activityRepository.findAllByActivityDateBetween(date, date), excluded);
        int dauWeb = 0;
        int dauIos = 0;
        int dauAndroid = 0;
        for (UserDailyActivity row : dayRows) {
            Set<String> devices = row.deviceTypeSet();
            if (devices.contains("web")) dauWeb++;
            if (devices.contains("ios") || devices.contains("ipad")) dauIos++;
            if (devices.contains("android")) dauAndroid++;
        }
        int dau = distinctUsers(dayRows);
        int wau =
                distinctUsers(
                        filtered(
                                activityRepository.findAllByActivityDateBetween(
                                        date.minusDays(6), date),
                                excluded));
        int mau =
                distinctUsers(
                        filtered(
                                activityRepository.findAllByActivityDateBetween(
                                        date.minusDays(29), date),
                                excluded));

        int attendanceDau = gamificationReader.countAttendanceUsers(date);
        MissionDailyCounts mission = missionReader.countCompletions(from, to);
        SubscriptionDailyCounts subs = gamificationReader.countSubscriptionEvents(from, to);
        RevenueAggregate revenue = gamificationReader.aggregateRevenue(from, to);

        return new DailyKpiValues(
                userReader.countSignups(from, to),
                userReader.countWithdrawals(from, to),
                userReader.countActiveUsersAsOf(to),
                dau,
                wau,
                mau,
                dauWeb,
                dauIos,
                dauAndroid,
                attendanceDau,
                mission.completions(),
                mission.activeUsers(),
                subs.newSubscriptions(),
                subs.activeSubscriptions(),
                subs.churned(),
                subs.trialStarts(),
                subs.trialConversions(),
                revenue.payingUserIds().size(),
                toKrw(revenue.byCurrency(), YearMonth.from(date)),
                toJson(revenue.byCurrency()));
    }

    /** KRW 는 그대로, 다른 통화는 그달 환율(fx_rate)이 있을 때만 환산. 없으면 revenue_krw 에서 빠진다(원본은 JSON 에 남음) */
    BigDecimal toKrw(Map<String, BigDecimal> byCurrency, YearMonth month) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : byCurrency.entrySet()) {
            if (KRW.equals(e.getKey())) {
                total = total.add(e.getValue());
                continue;
            }
            FxRate rate =
                    fxRateRepository
                            .findById(new FxRateId(KpiDates.monthKey(month), e.getKey()))
                            .orElse(null);
            if (rate == null) {
                log.warn(
                        "환율 없음 — revenue_krw 에서 제외: month={}, currency={}, amount={}",
                        month,
                        e.getKey(),
                        e.getValue());
                continue;
            }
            total = total.add(e.getValue().multiply(rate.getKrwPerUnit()));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    String toJson(Map<String, BigDecimal> byCurrency) {
        try {
            return objectMapper.writeValueAsString(new TreeMap<>(byCurrency));
        } catch (JsonProcessingException e) {
            log.warn("revenue_by_currency 직렬화 실패: {}", e.getMessage());
            return "{}";
        }
    }

    private static List<UserDailyActivity> filtered(
            List<UserDailyActivity> rows, Set<String> excluded) {
        if (excluded == null || excluded.isEmpty()) {
            return rows;
        }
        return rows.stream().filter(r -> !excluded.contains(r.getUserId())).toList();
    }

    private static int distinctUsers(List<UserDailyActivity> rows) {
        Set<String> users = new HashSet<>();
        for (UserDailyActivity r : rows) {
            users.add(r.getUserId());
        }
        return users.size();
    }
}
