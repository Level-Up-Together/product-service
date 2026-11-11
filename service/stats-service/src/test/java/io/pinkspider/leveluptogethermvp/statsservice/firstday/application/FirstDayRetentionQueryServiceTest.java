package io.pinkspider.leveluptogethermvp.statsservice.firstday.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.statsservice.activity.infrastructure.UserDailyActivityRepository;
import io.pinkspider.leveluptogethermvp.statsservice.api.exception.StatsInvalidParamException;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto.FirstDayRetentionResponse;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto.FirstDayRetentionResponse.GroupStat;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.entity.FirstDayRetention;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.FirstDayGroup;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.RetentionVerdictCode;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.SignupPlatform;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.VerdictComparison;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.infrastructure.FirstDayRetentionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FirstDayRetentionQueryService — 첫날 행동별 리텐션 조회 (LUT-779)")
class FirstDayRetentionQueryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    private static final LocalDate END = LocalDate.of(2026, 11, 9);

    @Mock FirstDayRetentionRepository repository;
    @Mock UserDailyActivityRepository activityRepository;
    @InjectMocks FirstDayRetentionQueryService service;

    /** 그룹·플랫폼·D1/D7/D30 이 정해진 행 — lastComplete 까지 닫힌 지표만 채운다 */
    private static FirstDayRetention row(
            String id,
            SignupPlatform platform,
            boolean completed,
            boolean interacted,
            LocalDate signup,
            Set<LocalDate> active,
            LocalDate lastComplete) {
        FirstDayRetention r =
                FirstDayRetention.classify(
                        id,
                        LocalDateTime.of(2026, 10, 1, 0, 0),
                        signup,
                        platform,
                        completed,
                        interacted);
        r.applyRetention(active, lastComplete);
        return r;
    }

    private static List<FirstDayRetention> many(
            String prefix, int n, boolean completed, boolean interacted, int d7Returned) {
        List<FirstDayRetention> rows = new ArrayList<>();
        LocalDate signup = LocalDate.of(2026, 10, 20);
        for (int i = 0; i < n; i++) {
            Set<LocalDate> active = i < d7Returned ? Set.of(signup.plusDays(7)) : Set.of();
            rows.add(
                    row(
                            prefix + i,
                            SignupPlatform.IOS,
                            completed,
                            interacted,
                            signup,
                            active,
                            TODAY));
        }
        return rows;
    }

    @Test
    @DisplayName("기간 끝 = 오늘 − 2일, 4w = 28일. 그룹·완료율·판정을 낸다")
    void summary() {
        List<FirstDayRetention> rows = new ArrayList<>();
        rows.addAll(many("n", 100, false, false, 10)); // 미완료 D7 10%
        rows.addAll(many("s", 100, true, false, 30)); // 혼자 D7 30%
        rows.addAll(many("c", 40, true, true, 20)); // 함께 D7 50%
        when(repository.findAllBySignupDateBetween(END.minusDays(27), END)).thenReturn(rows);
        when(activityRepository.findMinActivityDate())
                .thenReturn(Optional.of(LocalDate.of(2026, 10, 1)));

        FirstDayRetentionResponse response = service.summary("4w", "all", TODAY);

        assertThat(response.range()).isEqualTo("4w");
        assertThat(response.platform()).isEqualTo("ALL");
        assertThat(response.rangeStart()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(response.rangeEnd()).isEqualTo(END);
        assertThat(response.dataStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(response.signups()).isEqualTo(240);
        assertThat(response.firstDayCompletionRate()).isEqualTo(58.3);
        assertThat(response.groups())
                .extracting(GroupStat::group, GroupStat::users, GroupStat::d7, GroupStat::d7Users)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                FirstDayGroup.NOT_COMPLETED, 100, 10.0, 100),
                        org.assertj.core.groups.Tuple.tuple(FirstDayGroup.SOLO, 100, 30.0, 100),
                        org.assertj.core.groups.Tuple.tuple(FirstDayGroup.SOCIAL, 40, 50.0, 40));
        assertThat(response.verdicts())
                .extracting(
                        FirstDayRetentionResponse.Verdict::comparison,
                        FirstDayRetentionResponse.Verdict::code,
                        FirstDayRetentionResponse.Verdict::diffPp)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                VerdictComparison.SOLO_VS_NOT_COMPLETED,
                                RetentionVerdictCode.SOLO_RETAINS,
                                20.0),
                        org.assertj.core.groups.Tuple.tuple(
                                VerdictComparison.SOCIAL_VS_SOLO,
                                RetentionVerdictCode.SOCIAL_BOOSTS,
                                20.0));
    }

    @Test
    @DisplayName("확정된 유저만 센다 — 아직 D30 이 안 된 유저뿐이면 d30 은 null, 인원 0")
    void onlyConfirmedUsers() {
        LocalDate recent = LocalDate.of(2026, 11, 5);
        List<FirstDayRetention> rows =
                List.of(
                        row(
                                "a",
                                SignupPlatform.WEB,
                                false,
                                false,
                                recent,
                                Set.of(recent.plusDays(1)),
                                TODAY.minusDays(1)),
                        row(
                                "b",
                                SignupPlatform.WEB,
                                false,
                                false,
                                recent,
                                Set.of(),
                                TODAY.minusDays(1)));
        when(repository.findAllBySignupDateBetween(END.minusDays(55), END)).thenReturn(rows);
        when(activityRepository.findMinActivityDate()).thenReturn(Optional.empty());

        FirstDayRetentionResponse response = service.summary("8w", "ALL", TODAY);

        GroupStat notCompleted = response.groups().get(0);
        assertThat(notCompleted.d1()).isEqualTo(50.0);
        assertThat(notCompleted.d1Users()).isEqualTo(2);
        assertThat(notCompleted.d7()).isNull();
        assertThat(notCompleted.d7Users()).isZero();
        assertThat(notCompleted.d30()).isNull();
        assertThat(response.groups().get(1).d1()).isNull();
        assertThat(response.dataStartDate()).isNull();
        assertThat(response.firstDayCompletionRate()).isEqualTo(0.0);
        assertThat(response.verdicts())
                .allSatisfy(
                        v -> assertThat(v.code()).isEqualTo(RetentionVerdictCode.NOT_ENOUGH_DATA))
                .allSatisfy(v -> assertThat(v.diffPp()).isNull());
    }

    @Test
    @DisplayName("플랫폼으로 거른다 — UNKNOWN 은 전체에만")
    void platformFilter() {
        LocalDate signup = LocalDate.of(2026, 10, 20);
        List<FirstDayRetention> rows =
                List.of(
                        row("i", SignupPlatform.IOS, true, false, signup, Set.of(), TODAY),
                        row("a", SignupPlatform.ANDROID, true, false, signup, Set.of(), TODAY),
                        row("u", SignupPlatform.UNKNOWN, true, false, signup, Set.of(), TODAY));
        when(repository.findAllBySignupDateBetween(END.minusDays(83), END)).thenReturn(rows);

        assertThat(service.summary("12w", "android", TODAY).signups()).isEqualTo(1);
        assertThat(service.summary("12w", "IOS", TODAY).signups()).isEqualTo(1);
        assertThat(service.summary("12w", "WEB", TODAY).signups()).isZero();
        assertThat(service.summary("12w", null, TODAY).signups()).isEqualTo(3);
        verify(activityRepository, org.mockito.Mockito.times(4)).findMinActivityDate();
    }

    @Test
    @DisplayName("가입자가 없으면 완료율 null")
    void empty() {
        when(repository.findAllBySignupDateBetween(END.minusDays(27), END)).thenReturn(List.of());

        FirstDayRetentionResponse response = service.summary("4w", "ALL", TODAY);

        assertThat(response.signups()).isZero();
        assertThat(response.firstDayCompletionRate()).isNull();
        assertThat(response.groups()).hasSize(3).allSatisfy(g -> assertThat(g.users()).isZero());
    }

    @Test
    @DisplayName("기간·플랫폼이 허용값 밖이면 StatsInvalidParamException")
    void invalidParams() {
        assertThatThrownBy(() -> service.summary("5w", "ALL", TODAY))
                .isInstanceOf(StatsInvalidParamException.class)
                .hasFieldOrPropertyWithValue("name", "range");
        assertThatThrownBy(() -> service.summary("4w", "UNKNOWN", TODAY))
                .isInstanceOf(StatsInvalidParamException.class)
                .hasFieldOrPropertyWithValue("name", "platform");
    }

    @Test
    @DisplayName("% 는 소수 한 자리 반올림, 분모 0 이면 null")
    void percent() {
        assertThat(FirstDayRetentionQueryService.percent(1, 3)).isEqualTo(33.3);
        assertThat(FirstDayRetentionQueryService.percent(2, 3)).isEqualTo(66.7);
        assertThat(FirstDayRetentionQueryService.percent(0, 0)).isNull();
    }
}
