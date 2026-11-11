package io.pinkspider.leveluptogethermvp.statsservice.missionstats.application;

import io.pinkspider.leveluptogethermvp.statsservice.activity.config.UserActivityProperties;
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
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsSummaryResponse.CompletionRate;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsSummaryResponse.Completions;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsSummaryResponse.Created;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsSummaryResponse.Daily;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.policy.MissionStatsPeriod;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.policy.MissionStatsRates;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * LUT-790: 미션 통계 요약 — 스냅샷 없이 요청 때 mission_db 를 읽어 계산한다(건수가 적다). 느려지면 그때 캐시.
 *
 * <p>정의는 {@link MissionStatsSummaryResponse} 와 {@code docs/KPI_STATS.md} "미션 통계" 절. 완료 총수·완료 유저 수는
 * KPI 일별 스냅샷과 <b>같은 SQL</b>({@link MissionSourceReader#countCompletions})로 읽어 KPI 화면 숫자와 맞춘다.
 */
@Service
@RequiredArgsConstructor
public class MissionStatsService {

    /** 분류 표에 항상 나오는 행(0 포함) — 화면 탭이 같은 순서로 그린다 */
    static final List<String> KINDS = List.of("PERSONAL", "GUILD", "MISSION_BOOK");

    static final List<String> MODES = List.of("TIMED", "SIMPLE");

    private final MissionSourceReader missionReader;
    private final MetaSourceReader metaReader;
    private final UserActivityProperties activityProperties;

    public MissionStatsSummaryResponse summary(LocalDate from, LocalDate to) {
        MissionStatsPeriod period = MissionStatsPeriod.of(from, to);
        ExcludedUsers excluded = ExcludedUsers.of(activityProperties.getExcludedUserIds());
        Groups groups =
                new Groups(
                        missionReader.findCreatedGroups(period.utcFrom(), period.utcTo(), excluded),
                        missionReader.findCompletedGroups(
                                period.utcFrom(), period.utcTo(), excluded),
                        missionReader.findScheduledGroups(period.start(), period.end(), excluded));
        MissionDailyCounts kpi =
                missionReader.countCompletions(period.utcFrom(), period.utcTo(), excluded);

        return new MissionStatsSummaryResponse(
                period.start(),
                period.end(),
                created(groups.created()),
                completions(kpi, groups.completed()),
                completionRate(groups.scheduled()),
                daily(period, groups),
                fixedBreakdown(
                        KINDS,
                        groups,
                        MissionCreatedGroup::kind,
                        MissionCompletedGroup::kind,
                        MissionScheduledGroup::kind),
                fixedBreakdown(
                        MODES,
                        groups,
                        MissionCreatedGroup::mode,
                        MissionCompletedGroup::mode,
                        MissionScheduledGroup::mode),
                categoryBreakdown(groups));
    }

    private static Created created(List<MissionCreatedGroup> groups) {
        int total = 0;
        int deleted = 0;
        int pinned = 0;
        for (MissionCreatedGroup g : groups) {
            total += g.count();
            deleted += g.deleted() ? g.count() : 0;
            pinned += g.pinned() ? g.count() : 0;
        }
        return new Created(total, deleted, pinned, total - pinned);
    }

    private static Completions completions(
            MissionDailyCounts kpi, List<MissionCompletedGroup> groups) {
        int auto = groups.stream().mapToInt(MissionCompletedGroup::autoCompleted).sum();
        return new Completions(
                kpi.completions(),
                auto,
                kpi.activeUsers(),
                MissionStatsRates.average(kpi.completions(), kpi.activeUsers()));
    }

    private static CompletionRate completionRate(List<MissionScheduledGroup> groups) {
        int scheduled = groups.stream().mapToInt(MissionScheduledGroup::scheduled).sum();
        int completed = groups.stream().mapToInt(MissionScheduledGroup::completed).sum();
        return new CompletionRate(
                scheduled, completed, MissionStatsRates.percent(completed, scheduled));
    }

    private static List<Daily> daily(MissionStatsPeriod period, Groups groups) {
        return tally(
                        period.days(),
                        groups,
                        MissionCreatedGroup::day,
                        MissionCompletedGroup::day,
                        MissionScheduledGroup::day)
                .entrySet()
                .stream()
                .map(
                        e ->
                                new Daily(
                                        e.getKey(),
                                        e.getValue().created,
                                        e.getValue().completed,
                                        e.getValue().scheduled,
                                        e.getValue().scheduledCompleted,
                                        e.getValue().rate()))
                .toList();
    }

    /** 종류·수행 방식 — 정해진 순서로 항상 내고, 모르는 값이 오면 뒤에 붙인다(합이 총수와 맞게) */
    private static List<Breakdown> fixedBreakdown(
            List<String> keys,
            Groups groups,
            Function<MissionCreatedGroup, String> createdKey,
            Function<MissionCompletedGroup, String> completedKey,
            Function<MissionScheduledGroup, String> scheduledKey) {
        return tally(keys, groups, createdKey, completedKey, scheduledKey).entrySet().stream()
                .map(e -> e.getValue().toBreakdown(e.getKey(), null, null))
                .toList();
    }

    /** 카테고리 — 완료 많은 순, 같으면 생성 많은 순, 그것도 같으면 ID 순(카테고리 없음 = null 은 뒤) */
    private List<Breakdown> categoryBreakdown(Groups groups) {
        Map<Long, Tally> byCategory =
                tally(
                        List.of(),
                        groups,
                        MissionCreatedGroup::categoryId,
                        MissionCompletedGroup::categoryId,
                        MissionScheduledGroup::categoryId);
        Map<Long, MissionCategoryName> names =
                metaReader.findCategoryNames(
                        byCategory.keySet().stream().filter(Objects::nonNull).toList());
        return byCategory.entrySet().stream()
                .sorted(
                        Comparator.comparing(
                                        (Map.Entry<Long, Tally> e) -> e.getValue().completed,
                                        Comparator.reverseOrder())
                                .thenComparing(e -> e.getValue().created, Comparator.reverseOrder())
                                .thenComparing(
                                        Map.Entry::getKey,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(
                        e -> {
                            Long id = e.getKey();
                            // 카테고리 없음(null)은 조회하지 않는다 — 불변 Map 은 null 키 조회에서 예외
                            MissionCategoryName name = id == null ? null : names.get(id);
                            return e.getValue()
                                    .toBreakdown(
                                            id == null ? null : id.toString(),
                                            name == null ? null : name.name(),
                                            name == null ? null : name.nameEn());
                        })
                .toList();
    }

    /** 세 묶음을 같은 키로 모은다 — {@code seedKeys} 는 0 이어도 먼저 넣는다(순서 유지) */
    private static <K> Map<K, Tally> tally(
            Collection<K> seedKeys,
            Groups groups,
            Function<MissionCreatedGroup, K> createdKey,
            Function<MissionCompletedGroup, K> completedKey,
            Function<MissionScheduledGroup, K> scheduledKey) {
        Map<K, Tally> tallies = new LinkedHashMap<>();
        seedKeys.forEach(k -> tallies.put(k, new Tally()));
        for (MissionCreatedGroup g : groups.created()) {
            tallies.computeIfAbsent(createdKey.apply(g), k -> new Tally()).created += g.count();
        }
        for (MissionCompletedGroup g : groups.completed()) {
            tallies.computeIfAbsent(completedKey.apply(g), k -> new Tally()).completed +=
                    g.completed();
        }
        for (MissionScheduledGroup g : groups.scheduled()) {
            Tally t = tallies.computeIfAbsent(scheduledKey.apply(g), k -> new Tally());
            t.scheduled += g.scheduled();
            t.scheduledCompleted += g.completed();
        }
        return tallies;
    }

    private record Groups(
            List<MissionCreatedGroup> created,
            List<MissionCompletedGroup> completed,
            List<MissionScheduledGroup> scheduled) {}

    private static final class Tally {
        private int created;
        private int completed;
        private int scheduled;
        private int scheduledCompleted;

        private Double rate() {
            return MissionStatsRates.percent(scheduledCompleted, scheduled);
        }

        private Breakdown toBreakdown(String key, String name, String nameEn) {
            return new Breakdown(
                    key, name, nameEn, created, completed, scheduled, scheduledCompleted, rate());
        }
    }
}
