package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.AttendanceDay;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.RevenueAggregate;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.SubscriptionDailyCounts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * LUT-601: gamification_db 읽기 전용 집계 — 출석(attendance_record),
 * 구독(user_subscription·subscription_payment_history), 다이아 결제(diamond_bundle_purchase).
 *
 * <p>매출은 <b>금액 출처가 있는 행만</b> 합산한다(`price_source IS NOT NULL`, LUT-600). 금액 미상(Android 테스터 등)은 제외.
 * 환불은 음수.
 */
public class GamificationSourceReader {

    static final String COUNT_ATTENDANCE_USERS =
            "SELECT COUNT(DISTINCT user_id) FROM attendance_record WHERE attendance_date = :date";
    static final String FIND_ATTENDANCE =
            "SELECT user_id, attendance_date FROM attendance_record"
                    + " WHERE attendance_date >= :from AND attendance_date <= :to";

    static final String COUNT_PAYMENT_EVENTS =
            "SELECT event_type, trial, COUNT(*) AS cnt FROM subscription_payment_history WHERE"
                    + " occurred_at >= :from AND occurred_at < :to GROUP BY event_type, trial";
    static final String COUNT_TRIAL_CONVERSIONS =
            "SELECT COUNT(*) FROM subscription_payment_history r WHERE r.event_type = 'RENEWAL' AND"
                + " r.trial = FALSE AND r.occurred_at >= :from AND r.occurred_at < :to AND EXISTS"
                + " (SELECT 1 FROM subscription_payment_history p   WHERE p.user_id = r.user_id AND"
                + " p.event_type = 'PURCHASE' AND p.trial = TRUE   AND p.occurred_at <"
                + " r.occurred_at) AND NOT EXISTS (SELECT 1 FROM subscription_payment_history x  "
                + " WHERE x.user_id = r.user_id AND x.event_type = 'RENEWAL' AND x.trial = FALSE  "
                + " AND x.occurred_at < r.occurred_at)";
    static final String COUNT_ACTIVE_SUBSCRIPTIONS =
            "SELECT COUNT(*) FROM user_subscription WHERE expires_at > :asOf OR"
                    + " (grace_period_expires_at IS NOT NULL AND grace_period_expires_at > :asOf)";
    static final String COUNT_EXPIRED_NOT_RENEWED =
            "SELECT COUNT(*) FROM user_subscription WHERE expires_at >= :from AND expires_at < :to"
                    + " AND (grace_period_expires_at IS NULL OR grace_period_expires_at < :to)";

    static final String REVENUE_FILTER =
            " AND price_amount IS NOT NULL AND price_currency IS NOT NULL AND price_source IS NOT"
                    + " NULL";
    static final String SUBSCRIPTION_REVENUE_BY_CURRENCY =
            "SELECT price_currency AS currency, SUM(CASE WHEN event_type = 'REFUND' THEN"
                + " -price_amount ELSE price_amount END) AS amount FROM"
                + " subscription_payment_history WHERE occurred_at >= :from AND occurred_at < :to"
                    + REVENUE_FILTER
                    + " GROUP BY price_currency";
    static final String SUBSCRIPTION_PAYING_USERS =
            "SELECT DISTINCT user_id FROM subscription_payment_history"
                    + " WHERE occurred_at >= :from AND occurred_at < :to AND event_type <> 'REFUND'"
                    + " AND price_amount > 0"
                    + REVENUE_FILTER;
    static final String DIAMOND_REVENUE_BY_CURRENCY =
            "SELECT price_currency AS currency, SUM(price_amount) AS amount FROM"
                    + " diamond_bundle_purchase WHERE created_at >= :from AND created_at < :to AND"
                    + " status = 'PAID' AND price_amount IS NOT NULL AND price_currency IS NOT NULL"
                    + " GROUP BY price_currency";
    static final String DIAMOND_PAYING_USERS =
            "SELECT DISTINCT user_id FROM diamond_bundle_purchase"
                    + " WHERE created_at >= :from AND created_at < :to AND status = 'PAID'"
                    + " AND price_amount > 0 AND price_currency IS NOT NULL";

    static final String COHORT_SUBSCRIPTION_KRW =
            "SELECT COALESCE(SUM(CASE WHEN event_type = 'REFUND' THEN -price_amount ELSE"
                + " price_amount END), 0) FROM subscription_payment_history WHERE user_id IN (:ids)"
                + " AND occurred_at < :cutoff AND price_currency = 'KRW'"
                    + REVENUE_FILTER;
    static final String COHORT_SUBSCRIPTION_PAYING =
            "SELECT DISTINCT user_id FROM subscription_payment_history WHERE user_id IN (:ids)"
                    + " AND occurred_at < :cutoff AND event_type <> 'REFUND' AND price_amount > 0"
                    + REVENUE_FILTER;
    static final String COHORT_DIAMOND_KRW =
            "SELECT COALESCE(SUM(price_amount), 0) FROM diamond_bundle_purchase WHERE user_id IN"
                + " (:ids) AND created_at < :cutoff AND status = 'PAID' AND price_currency = 'KRW'"
                + " AND price_amount IS NOT NULL";
    static final String COHORT_DIAMOND_PAYING =
            "SELECT DISTINCT user_id FROM diamond_bundle_purchase WHERE user_id IN (:ids)"
                    + " AND created_at < :cutoff AND status = 'PAID' AND price_amount > 0"
                    + " AND price_currency IS NOT NULL";

    private final NamedParameterJdbcTemplate jdbc;

    public GamificationSourceReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int countAttendanceUsers(LocalDate date) {
        return count(COUNT_ATTENDANCE_USERS, Map.of("date", date));
    }

    public List<AttendanceDay> findAttendance(LocalDate from, LocalDate to) {
        return jdbc.query(
                FIND_ATTENDANCE,
                Map.of("from", from, "to", to),
                (rs, i) ->
                        new AttendanceDay(
                                rs.getString("user_id"),
                                rs.getDate("attendance_date").toLocalDate()));
    }

    /**
     * @param fromUtc 그날 시작, @param toUtc 그날 끝(exclusive) — 끝 시각이 "그날 기준 활성 구독" 판정 시점(asOf)
     */
    public SubscriptionDailyCounts countSubscriptionEvents(
            LocalDateTime fromUtc, LocalDateTime toUtc) {
        Map<String, Object> range = Map.of("from", fromUtc, "to", toUtc);
        int purchases = 0;
        int trialStarts = 0;
        int refunds = 0;
        for (Map<String, Object> row : jdbc.queryForList(COUNT_PAYMENT_EVENTS, range)) {
            String eventType = String.valueOf(row.get("event_type"));
            boolean trial = Boolean.TRUE.equals(row.get("trial"));
            int cnt = ((Number) row.get("cnt")).intValue();
            if ("PURCHASE".equals(eventType)) {
                purchases += cnt;
                if (trial) {
                    trialStarts += cnt;
                }
            } else if ("REFUND".equals(eventType)) {
                refunds += cnt;
            }
        }
        int conversions = count(COUNT_TRIAL_CONVERSIONS, range);
        int active = count(COUNT_ACTIVE_SUBSCRIPTIONS, Map.of("asOf", toUtc));
        int expired = count(COUNT_EXPIRED_NOT_RENEWED, range);
        return new SubscriptionDailyCounts(
                purchases, trialStarts, conversions, refunds, active, expired);
    }

    /** 구독 + 다이아 묶음 매출 (통화별 합, 결제 유저 집합) */
    public RevenueAggregate aggregateRevenue(LocalDateTime fromUtc, LocalDateTime toUtc) {
        Map<String, Object> range = Map.of("from", fromUtc, "to", toUtc);
        RevenueAggregate subscription =
                new RevenueAggregate(
                        amountsByCurrency(SUBSCRIPTION_REVENUE_BY_CURRENCY, range),
                        new HashSet<>(
                                jdbc.queryForList(SUBSCRIPTION_PAYING_USERS, range, String.class)));
        RevenueAggregate diamond =
                new RevenueAggregate(
                        amountsByCurrency(DIAMOND_REVENUE_BY_CURRENCY, range),
                        new HashSet<>(
                                jdbc.queryForList(DIAMOND_PAYING_USERS, range, String.class)));
        return subscription.merge(diamond);
    }

    /** 코호트(유저 집합)의 cutoff 이전 누적 KRW 매출 — byCurrency 에는 KRW 하나만 담긴다 */
    public RevenueAggregate aggregateCohortRevenueKrw(
            Collection<String> userIds, LocalDateTime cutoffUtc) {
        if (userIds == null || userIds.isEmpty()) {
            return RevenueAggregate.empty();
        }
        MapSqlParameterSource params =
                new MapSqlParameterSource().addValue("ids", userIds).addValue("cutoff", cutoffUtc);
        BigDecimal subscription = sum(COHORT_SUBSCRIPTION_KRW, params);
        BigDecimal diamond = sum(COHORT_DIAMOND_KRW, params);
        Set<String> paying =
                new HashSet<>(jdbc.queryForList(COHORT_SUBSCRIPTION_PAYING, params, String.class));
        paying.addAll(jdbc.queryForList(COHORT_DIAMOND_PAYING, params, String.class));
        Map<String, BigDecimal> byCurrency = new HashMap<>();
        byCurrency.put("KRW", subscription.add(diamond));
        return new RevenueAggregate(byCurrency, paying);
    }

    private Map<String, BigDecimal> amountsByCurrency(String sql, Map<String, Object> params) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql, params)) {
            Object amount = row.get("amount");
            if (row.get("currency") != null && amount != null) {
                result.merge(
                        String.valueOf(row.get("currency")),
                        new BigDecimal(amount.toString()),
                        BigDecimal::add);
            }
        }
        return result;
    }

    private int count(String sql, Map<String, ?> params) {
        Integer n = jdbc.queryForObject(sql, params, Integer.class);
        return n == null ? 0 : n;
    }

    private BigDecimal sum(String sql, MapSqlParameterSource params) {
        BigDecimal v = jdbc.queryForObject(sql, params, BigDecimal.class);
        return v == null ? BigDecimal.ZERO : v;
    }
}
