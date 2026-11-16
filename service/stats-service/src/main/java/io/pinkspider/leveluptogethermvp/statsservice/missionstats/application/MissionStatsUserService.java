package io.pinkspider.leveluptogethermvp.statsservice.missionstats.application;

import io.pinkspider.leveluptogethermvp.statsservice.activity.config.UserActivityProperties;
import io.pinkspider.leveluptogethermvp.statsservice.api.exception.StatsInvalidParamException;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.ExcludedUsers;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.KpiDates;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserCompletionStat;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserNickname;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.GamificationSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.MissionSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.UserSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsUsersResponse;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsUsersResponse.Bucket;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsUsersResponse.UserRow;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.enums.CompletionBucket;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.enums.MissionStatsSortField;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.policy.MissionStatsPeriod;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.policy.MissionStatsSort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * LUT-790: 사용자별 수행 횟수 — 기간 완료 유저 전부를 mission_db 에서 한 번에 읽고, 검색·정렬·페이지는 메모리에서 한다(완료 유저 수가 적다).
 *
 * <p>닉네임(user_db)·레벨(gamification_db)은 다른 DB 라 조인할 수 없다. 기본은 그 페이지 유저만 읽고, 닉네임 검색·닉네임 정렬이면 닉네임을, 레벨
 * 정렬이면 레벨을 전원 읽는다({@value #CHUNK_SIZE}명씩). 닉네임 검색은 공용 파사드 대신 직접 읽는다 — 파사드는 결과를 200명에서 자르므로 흔한 글자로
 * 찾으면 완료 유저가 빠진다.
 */
@Service
@RequiredArgsConstructor
public class MissionStatsUserService {

    static final int MAX_SIZE = 100;
    static final int CHUNK_SIZE = 500;

    /** 정렬 값이 같을 때 — 완료 많은 순, 최근 완료 순, ID 순(페이지가 흔들리지 않게) */
    static final Comparator<UserCompletionStat> TIE_BREAK =
            Comparator.comparingInt(UserCompletionStat::completions)
                    .reversed()
                    .thenComparing(
                            UserCompletionStat::lastCompletedAt,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(UserCompletionStat::userId);

    private final MissionSourceReader missionReader;
    private final UserSourceReader userReader;
    private final GamificationSourceReader gamificationReader;
    private final UserActivityProperties activityProperties;

    public MissionStatsUsersResponse users(
            LocalDate from, LocalDate to, int page, int size, String sort, String keyword) {
        MissionStatsPeriod period = MissionStatsPeriod.of(from, to);
        if (page < 0) {
            throw new StatsInvalidParamException("page", "0 이상");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new StatsInvalidParamException("size", "1~" + MAX_SIZE);
        }
        MissionStatsSort order = MissionStatsSort.parse(sort);
        String word = keyword == null || keyword.isBlank() ? null : keyword.trim();

        List<UserCompletionStat> stats =
                missionReader.findUserCompletions(
                        period.utcFrom(),
                        period.utcTo(),
                        ExcludedUsers.of(activityProperties.getExcludedUserIds()));
        List<String> allIds = stats.stream().map(UserCompletionStat::userId).toList();
        Map<String, UserNickname> allNicknames =
                word != null || order.field() == MissionStatsSortField.NICKNAME
                        ? inChunks(allIds, userReader::findNicknames)
                        : null;
        Map<String, Integer> allLevels =
                order.field() == MissionStatsSortField.LEVEL
                        ? inChunks(allIds, gamificationReader::findLevels)
                        : null;

        List<UserCompletionStat> sorted =
                stats.stream()
                        .filter(s -> word == null || matches(s, allNicknames.get(s.userId()), word))
                        .sorted(comparator(order, allNicknames, allLevels))
                        .toList();
        List<UserCompletionStat> pageRows =
                sorted.stream().skip((long) page * size).limit(size).toList();
        List<String> pageIds = pageRows.stream().map(UserCompletionStat::userId).toList();
        Map<String, UserNickname> nicknames =
                allNicknames != null ? allNicknames : inChunks(pageIds, userReader::findNicknames);
        Map<String, Integer> levels =
                allLevels != null ? allLevels : inChunks(pageIds, gamificationReader::findLevels);

        return new MissionStatsUsersResponse(
                period.start(),
                period.end(),
                word,
                order.value(),
                page,
                size,
                sorted.size(),
                (sorted.size() + size - 1) / size,
                distribution(stats),
                pageRows.stream()
                        .map(s -> row(s, nicknames.get(s.userId()), levels.get(s.userId())))
                        .toList());
    }

    /** 닉네임 일부(대소문자 무시) 또는 유저 ID 전체 */
    static boolean matches(UserCompletionStat stat, UserNickname nickname, String word) {
        if (stat.userId().equals(word)) {
            return true;
        }
        return nickname != null
                && nickname.nickname() != null
                && nickname.nickname()
                        .toLowerCase(Locale.ROOT)
                        .contains(word.toLowerCase(Locale.ROOT));
    }

    /** 값이 없는 유저(닉네임·레벨 행 없음)는 방향과 상관없이 맨 뒤 */
    static Comparator<UserCompletionStat> comparator(
            MissionStatsSort sort,
            Map<String, UserNickname> nicknames,
            Map<String, Integer> levels) {
        Comparator<UserCompletionStat> primary =
                switch (sort.field()) {
                    case COMPLETIONS -> by(UserCompletionStat::completions, sort.ascending());
                    case TIMER_MINUTES -> by(UserCompletionStat::timerMinutes, sort.ascending());
                    case LAST_COMPLETED_AT ->
                            by(UserCompletionStat::lastCompletedAt, sort.ascending());
                    case NICKNAME ->
                            by(s -> sortableNickname(nicknames.get(s.userId())), sort.ascending());
                    case LEVEL -> by(s -> levels.get(s.userId()), sort.ascending());
                };
        return primary.thenComparing(TIE_BREAK);
    }

    private static <T extends Comparable<? super T>> Comparator<UserCompletionStat> by(
            Function<UserCompletionStat, T> key, boolean ascending) {
        Comparator<T> order = ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return Comparator.comparing(key, Comparator.nullsLast(order));
    }

    private static String sortableNickname(UserNickname nickname) {
        return nickname == null || nickname.nickname() == null
                ? null
                : nickname.nickname().toLowerCase(Locale.ROOT);
    }

    private static List<Bucket> distribution(List<UserCompletionStat> stats) {
        Map<CompletionBucket, Integer> counts = new EnumMap<>(CompletionBucket.class);
        for (UserCompletionStat s : stats) {
            counts.merge(CompletionBucket.of(s.completions()), 1, Integer::sum);
        }
        return Arrays.stream(CompletionBucket.values())
                .map(b -> new Bucket(b, b.getMin(), b.getMax(), counts.getOrDefault(b, 0)))
                .toList();
    }

    private static UserRow row(UserCompletionStat stat, UserNickname nickname, Integer level) {
        return new UserRow(
                stat.userId(),
                nickname == null ? null : nickname.nickname(),
                level,
                nickname != null && nickname.withdrawn(),
                stat.completions(),
                stat.timerMinutes(),
                kstDate(stat.lastCompletedAt()));
    }

    private static LocalDate kstDate(LocalDateTime utc) {
        return utc == null
                ? null
                : utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KpiDates.KST).toLocalDate();
    }

    private static <V> Map<String, V> inChunks(
            List<String> ids, Function<List<String>, Map<String, V>> lookup) {
        Map<String, V> result = new HashMap<>();
        for (int i = 0; i < ids.size(); i += CHUNK_SIZE) {
            result.putAll(lookup.apply(ids.subList(i, Math.min(ids.size(), i + CHUNK_SIZE))));
        }
        return result;
    }
}
