package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.AttendanceDay;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.RevenueAggregate;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.SubscriptionDailyCounts;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@ExtendWith(MockitoExtension.class)
@DisplayName("GamificationSourceReader 테스트 (LUT-601)")
class GamificationSourceReaderTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 10, 22, 15, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 10, 23, 15, 0);

    @Mock private NamedParameterJdbcTemplate jdbc;
    @Mock private ResultSet resultSet;

    private GamificationSourceReader reader() {
        return new GamificationSourceReader(jdbc);
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("출석 유저 수와 출석 행 매핑")
    @SuppressWarnings("unchecked")
    void attendance() throws Exception {
        when(jdbc.queryForObject(
                        eq(GamificationSourceReader.COUNT_ATTENDANCE_USERS),
                        anyMap(),
                        eq(Integer.class)))
                .thenReturn(9);
        when(resultSet.getString("user_id")).thenReturn("u1");
        when(resultSet.getDate("attendance_date")).thenReturn(Date.valueOf("2026-10-22"));
        when(jdbc.query(
                        eq(GamificationSourceReader.FIND_ATTENDANCE),
                        anyMap(),
                        any(RowMapper.class)))
                .thenAnswer(
                        inv -> {
                            RowMapper<AttendanceDay> mapper = inv.getArgument(2);
                            return List.of(mapper.mapRow(resultSet, 0));
                        });

        assertThat(reader().countAttendanceUsers(LocalDate.of(2026, 10, 22))).isEqualTo(9);
        List<AttendanceDay> days =
                reader().findAttendance(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertThat(days).containsExactly(new AttendanceDay("u1", LocalDate.of(2026, 10, 22)));
    }

    @Test
    @DisplayName("구독 이벤트: PURCHASE/trial/REFUND 분류 + 전환·활성·만료 카운트, 이탈 = 만료 + 환불")
    void subscriptionEvents() {
        when(jdbc.queryForList(eq(GamificationSourceReader.COUNT_PAYMENT_EVENTS), anyMap()))
                .thenReturn(
                        List.of(
                                row("event_type", "PURCHASE", "trial", true, "cnt", 2L),
                                row("event_type", "PURCHASE", "trial", false, "cnt", 1L),
                                row("event_type", "RENEWAL", "trial", false, "cnt", 7L),
                                row("event_type", "REFUND", "trial", false, "cnt", 1L)));
        when(jdbc.queryForObject(
                        eq(GamificationSourceReader.COUNT_TRIAL_CONVERSIONS),
                        anyMap(),
                        eq(Integer.class)))
                .thenReturn(1);
        when(jdbc.queryForObject(
                        eq(GamificationSourceReader.COUNT_ACTIVE_SUBSCRIPTIONS),
                        anyMap(),
                        eq(Integer.class)))
                .thenReturn(5);
        when(jdbc.queryForObject(
                        eq(GamificationSourceReader.COUNT_EXPIRED_NOT_RENEWED),
                        anyMap(),
                        eq(Integer.class)))
                .thenReturn(2);

        SubscriptionDailyCounts c = reader().countSubscriptionEvents(FROM, TO);

        assertThat(c.newSubscriptions()).isEqualTo(3);
        assertThat(c.trialStarts()).isEqualTo(2);
        assertThat(c.refunds()).isEqualTo(1);
        assertThat(c.trialConversions()).isEqualTo(1);
        assertThat(c.activeSubscriptions()).isEqualTo(5);
        assertThat(c.expiredNotRenewed()).isEqualTo(2);
        assertThat(c.churned()).isEqualTo(3);
    }

    @Test
    @DisplayName("매출: 구독 + 다이아 통화별 합산, 결제 유저 합집합, 통화/금액 null 행은 건너뜀")
    void revenue() {
        when(jdbc.queryForList(
                        eq(GamificationSourceReader.SUBSCRIPTION_REVENUE_BY_CURRENCY), anyMap()))
                .thenReturn(
                        List.of(
                                row("currency", "KRW", "amount", new BigDecimal("4900.00")),
                                row("currency", null, "amount", new BigDecimal("1.00")),
                                row("currency", "USD", "amount", null)));
        when(jdbc.queryForList(
                        eq(GamificationSourceReader.SUBSCRIPTION_PAYING_USERS),
                        anyMap(),
                        eq(String.class)))
                .thenReturn(List.of("u1", "u2"));
        when(jdbc.queryForList(eq(GamificationSourceReader.DIAMOND_REVENUE_BY_CURRENCY), anyMap()))
                .thenReturn(List.of(row("currency", "KRW", "amount", new BigDecimal("1100.00"))));
        when(jdbc.queryForList(
                        eq(GamificationSourceReader.DIAMOND_PAYING_USERS),
                        anyMap(),
                        eq(String.class)))
                .thenReturn(List.of("u2", "u3"));

        RevenueAggregate r = reader().aggregateRevenue(FROM, TO);

        assertThat(r.amountOf("KRW")).isEqualByComparingTo("6000.00");
        assertThat(r.byCurrency()).containsOnlyKeys("KRW");
        assertThat(r.payingUserIds()).containsExactlyInAnyOrder("u1", "u2", "u3");
        assertThat(GamificationSourceReader.SUBSCRIPTION_REVENUE_BY_CURRENCY)
                .contains("price_source IS NOT NULL");
    }

    @Test
    @DisplayName("코호트 매출: 유저 없으면 조회 없이 빈 결과, 있으면 KRW 합과 결제 유저 합집합")
    void cohortRevenue() {
        assertThat(reader().aggregateCohortRevenueKrw(List.of(), TO).byCurrency()).isEmpty();
        verify(jdbc, never())
                .queryForObject(
                        any(String.class), any(SqlParameterSource.class), eq(BigDecimal.class));

        when(jdbc.queryForObject(
                        eq(GamificationSourceReader.COHORT_SUBSCRIPTION_KRW),
                        any(SqlParameterSource.class),
                        eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("9800.00"));
        when(jdbc.queryForObject(
                        eq(GamificationSourceReader.COHORT_DIAMOND_KRW),
                        any(SqlParameterSource.class),
                        eq(BigDecimal.class)))
                .thenReturn(null);
        when(jdbc.queryForList(
                        eq(GamificationSourceReader.COHORT_SUBSCRIPTION_PAYING),
                        any(SqlParameterSource.class),
                        eq(String.class)))
                .thenReturn(List.of("u1"));
        when(jdbc.queryForList(
                        eq(GamificationSourceReader.COHORT_DIAMOND_PAYING),
                        any(SqlParameterSource.class),
                        eq(String.class)))
                .thenReturn(List.of("u1", "u4"));

        RevenueAggregate r = reader().aggregateCohortRevenueKrw(List.of("u1", "u4", "u5"), TO);

        assertThat(r.amountOf("KRW")).isEqualByComparingTo("9800.00");
        assertThat(r.payingUserIds()).containsExactlyInAnyOrder("u1", "u4");
    }
}
