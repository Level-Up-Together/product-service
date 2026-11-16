package io.pinkspider.leveluptogethermvp.statsservice.firstday.application;

import io.pinkspider.leveluptogethermvp.statsservice.activity.infrastructure.UserDailyActivityRepository;
import io.pinkspider.leveluptogethermvp.statsservice.api.exception.StatsInvalidParamException;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto.FirstDayRetentionResponse;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto.FirstDayRetentionResponse.GroupStat;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto.FirstDayRetentionResponse.Verdict;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.entity.FirstDayRetention;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.FirstDayGroup;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.RetentionRange;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.SignupPlatform;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.policy.RetentionVerdictPolicy;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.policy.RetentionVerdictPolicy.Counts;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.infrastructure.FirstDayRetentionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-779: 첫날 행동별 리텐션 조회 — {@code first_day_retention} 만 읽는다(운영 표를 매번 훑지 않는다).
 *
 * <p>기간 = 최근 4·8·12주 가입일(KST). 끝은 첫날 24시간이 모두 끝난 날(오늘 − 2일)이다. 각 리텐션은 그 지표가 확정된 유저만으로 센다 — 예) D7 은
 * 가입 후 7일이 지난 유저만. 판정은 D7 기준({@link RetentionVerdictPolicy}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "statsTransactionManager")
public class FirstDayRetentionQueryService {

    static final String PLATFORM_ALL = "ALL";

    /** 오늘 − 2일: 그날 23:59 가입자도 어제 23:59 에 첫날이 끝났고, 00:50 집계가 분류를 마쳤다 */
    static final int RANGE_END_OFFSET_DAYS = 2;

    private final FirstDayRetentionRepository repository;
    private final UserDailyActivityRepository activityRepository;

    public FirstDayRetentionResponse summary(
            String rangeValue, String platformValue, LocalDate today) {
        RetentionRange range =
                RetentionRange.parse(rangeValue)
                        .orElseThrow(() -> new StatsInvalidParamException("range", "4w, 8w, 12w"));
        String platform =
                platformValue == null ? PLATFORM_ALL : platformValue.toUpperCase(Locale.ROOT);
        SignupPlatform platformFilter = parsePlatform(platform);

        LocalDate rangeEnd = today.minusDays(RANGE_END_OFFSET_DAYS);
        LocalDate rangeStart = rangeEnd.minusDays(range.days() - 1L);
        List<FirstDayRetention> rows =
                repository.findAllBySignupDateBetween(rangeStart, rangeEnd).stream()
                        .filter(r -> platformFilter == null || r.getPlatform() == platformFilter)
                        .toList();

        List<GroupStat> groups =
                Arrays.stream(FirstDayGroup.values()).map(g -> groupStat(g, rows)).toList();
        long completed = rows.stream().filter(FirstDayRetention::isFirstDayCompleted).count();
        Counts notCompleted = d7Counts(rows, FirstDayGroup.NOT_COMPLETED);
        Counts solo = d7Counts(rows, FirstDayGroup.SOLO);
        Counts social = d7Counts(rows, FirstDayGroup.SOCIAL);

        return new FirstDayRetentionResponse(
                range.value(),
                platform,
                rangeStart,
                rangeEnd,
                activityRepository.findMinActivityDate().orElse(null),
                rows.size(),
                percent(completed, rows.size()),
                groups,
                List.of(
                        verdict(RetentionVerdictPolicy.solo(notCompleted, solo)),
                        verdict(RetentionVerdictPolicy.social(solo, social))));
    }

    /** ALL 이면 null(거르지 않음). UNKNOWN 은 "전체" 에만 들어가므로 직접 고를 수 없다 */
    private static SignupPlatform parsePlatform(String platform) {
        return switch (platform) {
            case PLATFORM_ALL -> null;
            case "IOS" -> SignupPlatform.IOS;
            case "ANDROID" -> SignupPlatform.ANDROID;
            case "WEB" -> SignupPlatform.WEB;
            default -> throw new StatsInvalidParamException("platform", "ALL, IOS, ANDROID, WEB");
        };
    }

    private static GroupStat groupStat(FirstDayGroup group, List<FirstDayRetention> rows) {
        List<FirstDayRetention> members =
                rows.stream().filter(r -> r.getFirstDayGroup() == group).toList();
        Counts d1 = counts(members, FirstDayRetention::getD1);
        Counts d7 = counts(members, FirstDayRetention::getD7);
        Counts d30 = counts(members, FirstDayRetention::getD30);
        return new GroupStat(
                group,
                members.size(),
                percent(d1.retained(), d1.users()),
                d1.users(),
                percent(d7.retained(), d7.users()),
                d7.users(),
                percent(d30.retained(), d30.users()),
                d30.users());
    }

    private static Counts d7Counts(List<FirstDayRetention> rows, FirstDayGroup group) {
        return counts(
                rows.stream().filter(r -> r.getFirstDayGroup() == group).toList(),
                FirstDayRetention::getD7);
    }

    /** 확정된(null 아닌) 값만 — users = 판정 가능 인원, retained = 그중 돌아온 사람 */
    private static Counts counts(
            List<FirstDayRetention> members, Function<FirstDayRetention, Boolean> metric) {
        int users = 0;
        int retained = 0;
        for (FirstDayRetention r : members) {
            Boolean value = metric.apply(r);
            if (value != null) {
                users++;
                if (value) {
                    retained++;
                }
            }
        }
        return new Counts(retained, users);
    }

    private static Verdict verdict(RetentionVerdictPolicy.Result result) {
        return new Verdict(
                result.comparison(),
                result.code(),
                result.diffPp() == null ? null : round1(result.diffPp()),
                result.significant());
    }

    /** % 소수 한 자리 — 분모가 0 이면 null(확정 전 "–", 0% 로 보이지 않게) */
    static Double percent(long numerator, long denominator) {
        return denominator == 0 ? null : round1(numerator * 100.0 / denominator);
    }

    private static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
