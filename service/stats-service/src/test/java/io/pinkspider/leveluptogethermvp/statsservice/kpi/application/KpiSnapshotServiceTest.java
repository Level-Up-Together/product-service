package io.pinkspider.leveluptogethermvp.statsservice.kpi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.statsservice.activity.config.UserActivityProperties;
import io.pinkspider.leveluptogethermvp.statsservice.activity.domain.entity.UserDailyActivity;
import io.pinkspider.leveluptogethermvp.statsservice.activity.infrastructure.UserDailyActivityRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
@DisplayName("KpiSnapshotService 테스트 (LUT-601)")
class KpiSnapshotServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 22);
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 10, 21, 15, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 10, 22, 15, 0);

    @Mock private DailyKpiSnapshotRepository snapshotRepository;
    @Mock private FxRateRepository fxRateRepository;
    @Mock private UserDailyActivityRepository activityRepository;
    @Mock private UserSourceReader userReader;
    @Mock private GamificationSourceReader gamificationReader;
    @Mock private MissionSourceReader missionReader;

    private UserActivityProperties properties;
    private KpiSnapshotService service;

    @BeforeEach
    void setUp() {
        properties = new UserActivityProperties();
        service =
                new KpiSnapshotService(
                        snapshotRepository,
                        fxRateRepository,
                        activityRepository,
                        properties,
                        userReader,
                        gamificationReader,
                        missionReader,
                        new ObjectMapper());
    }

    private static UserDailyActivity activity(String user, String devices) {
        return UserDailyActivity.create(
                user, DATE, devices == null ? List.of() : List.of(devices.split(",")));
    }

    private void stubSources() {
        lenient().when(userReader.countSignups(FROM, TO)).thenReturn(3);
        lenient().when(userReader.countWithdrawals(FROM, TO)).thenReturn(1);
        lenient().when(userReader.countActiveUsersAsOf(TO)).thenReturn(140);
        lenient().when(gamificationReader.countAttendanceUsers(DATE)).thenReturn(9);
        lenient()
                .when(missionReader.countCompletions(FROM, TO))
                .thenReturn(new MissionDailyCounts(12, 5));
        lenient()
                .when(gamificationReader.countSubscriptionEvents(FROM, TO))
                .thenReturn(new SubscriptionDailyCounts(2, 1, 1, 1, 6, 1));
        lenient()
                .when(gamificationReader.aggregateRevenue(FROM, TO))
                .thenReturn(
                        new RevenueAggregate(
                                Map.of(
                                        "KRW",
                                        new BigDecimal("9800.00"),
                                        "USD",
                                        new BigDecimal("2.99")),
                                Set.of("u1", "u2")));
    }

    @Test
    @DisplayName("하루치 지표 계산: 활동(제외 유저 제거·기기 분리·WAU/MAU)·출석·미션·구독·매출(환율 없는 통화는 KRW 합에서 제외)")
    void compute() {
        stubSources();
        properties.setExcludedUserIds(Set.of("bot"));
        when(activityRepository.findAllByActivityDateBetween(DATE, DATE))
                .thenReturn(
                        List.of(
                                activity("u1", "web,ios"),
                                activity("u2", "android"),
                                activity("bot", "web"),
                                activity("u3", null)));
        when(activityRepository.findAllByActivityDateBetween(DATE.minusDays(6), DATE))
                .thenReturn(
                        List.of(
                                activity("u1", "web"),
                                activity("u2", "web"),
                                activity("u5", "ipad"),
                                activity("bot", "web")));
        when(activityRepository.findAllByActivityDateBetween(DATE.minusDays(29), DATE))
                .thenReturn(
                        List.of(
                                activity("u1", "web"),
                                activity("u2", "web"),
                                activity("u5", "web"),
                                activity("u6", "web"),
                                activity("u6", "ios")));
        when(fxRateRepository.findById(new FxRateId("2026-10", "USD")))
                .thenReturn(Optional.empty());

        DailyKpiValues v = service.compute(DATE);

        assertThat(v.signups()).isEqualTo(3);
        assertThat(v.withdrawals()).isEqualTo(1);
        assertThat(v.activeUsers()).isEqualTo(140);
        assertThat(v.dau()).isEqualTo(3);
        assertThat(v.dauWeb()).isEqualTo(1);
        assertThat(v.dauIos()).isEqualTo(1);
        assertThat(v.dauAndroid()).isEqualTo(1);
        assertThat(v.wau()).isEqualTo(3);
        assertThat(v.mau()).isEqualTo(4);
        assertThat(v.attendanceDau()).isEqualTo(9);
        assertThat(v.missionCompletions()).isEqualTo(12);
        assertThat(v.missionActiveUsers()).isEqualTo(5);
        assertThat(v.newSubscriptions()).isEqualTo(2);
        assertThat(v.trialStarts()).isEqualTo(1);
        assertThat(v.trialConversions()).isEqualTo(1);
        assertThat(v.activeSubscriptions()).isEqualTo(6);
        assertThat(v.churnedSubscriptions()).isEqualTo(2);
        assertThat(v.payingUsers()).isEqualTo(2);
        assertThat(v.revenueKrw()).isEqualByComparingTo("9800.00");
        assertThat(v.revenueByCurrencyJson()).isEqualTo("{\"KRW\":9800.00,\"USD\":2.99}");
    }

    @Test
    @DisplayName("환율이 있으면 외화를 KRW 로 환산해 더한다")
    void toKrwWithFx() {
        FxRate rate = FxRate.of("2026-10", "USD", new BigDecimal("1400"));
        when(fxRateRepository.findById(new FxRateId("2026-10", "USD")))
                .thenReturn(Optional.of(rate));

        BigDecimal krw =
                service.toKrw(
                        Map.of("KRW", new BigDecimal("100.00"), "USD", new BigDecimal("2.00")),
                        YearMonth.of(2026, 10));

        assertThat(krw).isEqualByComparingTo("2900.00");
    }

    @Test
    @DisplayName("build: 기존 행이 있으면 갱신, 없으면 생성해 저장한다")
    void buildUpsert() {
        stubSources();
        when(activityRepository.findAllByActivityDateBetween(any(), any())).thenReturn(List.of());
        when(fxRateRepository.findById(any())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(DailyKpiSnapshot.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(snapshotRepository.findById(DATE)).thenReturn(Optional.empty());
        DailyKpiSnapshot created = service.build(DATE);
        assertThat(created.getSnapshotDate()).isEqualTo(DATE);
        assertThat(created.getSignups()).isEqualTo(3);
        assertThat(created.getRevenueKrw()).isEqualByComparingTo("9800.00");

        DailyKpiSnapshot existing = DailyKpiSnapshot.create(DATE, zeroValues());
        when(snapshotRepository.findById(DATE)).thenReturn(Optional.of(existing));
        DailyKpiSnapshot updated = service.build(DATE);
        assertThat(updated).isSameAs(existing);
        assertThat(existing.getMau()).isZero();
        assertThat(existing.getAttendanceDau()).isEqualTo(9);
        ArgumentCaptor<DailyKpiSnapshot> captor = ArgumentCaptor.forClass(DailyKpiSnapshot.class);
        verify(snapshotRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).hasSize(2);
    }

    @Test
    @DisplayName("JSON 직렬화는 통화 키를 정렬하고, 실패해도 {} 를 돌려준다")
    void toJson() {
        assertThat(
                        service.toJson(
                                Map.of("USD", new BigDecimal("1.5"), "KRW", new BigDecimal("100"))))
                .isEqualTo("{\"KRW\":100,\"USD\":1.5}");
        KpiSnapshotService broken =
                new KpiSnapshotService(
                        snapshotRepository,
                        fxRateRepository,
                        activityRepository,
                        properties,
                        userReader,
                        gamificationReader,
                        missionReader,
                        new ObjectMapper() {
                            @Override
                            public String writeValueAsString(Object value)
                                    throws com.fasterxml.jackson.core.JsonProcessingException {
                                throw new com.fasterxml.jackson.core.JsonProcessingException(
                                        "boom") {};
                            }
                        });
        assertThat(broken.toJson(Map.of("KRW", BigDecimal.ONE))).isEqualTo("{}");
    }

    private static DailyKpiValues zeroValues() {
        return new DailyKpiValues(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO, "{}");
    }
}
