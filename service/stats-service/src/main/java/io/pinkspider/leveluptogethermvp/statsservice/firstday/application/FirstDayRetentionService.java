package io.pinkspider.leveluptogethermvp.statsservice.firstday.application;

import io.pinkspider.leveluptogethermvp.statsservice.activity.config.UserActivityProperties;
import io.pinkspider.leveluptogethermvp.statsservice.activity.domain.entity.UserDailyActivity;
import io.pinkspider.leveluptogethermvp.statsservice.activity.infrastructure.UserDailyActivityRepository;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto.FirstDayRetentionRunResponse;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.entity.FirstDayRetention;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.SignupPlatform;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.policy.FirstDayWindow;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.infrastructure.FirstDayRetentionRepository;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.ExcludedUsers;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.KpiDates;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.SignupRecord;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserEvent;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.ChatSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.FeedSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.GuildSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.MissionSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.TowerSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.UserSourceReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-779: 첫날 행동별 리텐션 집계 — 매일 새벽(00:50 KST, 활동 flush 00:20 뒤) 두 단계로 {@code first_day_retention} 을
 * 채운다.
 *
 * <ol>
 *   <li><b>분류</b>: 첫날(가입 + 24시간)이 끝난 새 가입자의 그룹을 정한다 — 첫날 미션 완료(mission_db)와 다른 사람과의 상호작용(친구 요청·수락
 *       user_db · 길드 가입 guild_db · 피드 좋아요·댓글 주고받음 feed_db · DM chat_db · 탑 채팅·받은 응원 tower_db). 대상은
 *       접속 기록 시작일(min activity_date) 이후 가입자, 테스트 계정·제외 유저 빼고 <b>탈퇴자 포함</b>(분모에 남겨 "돌아오지 않음")
 *   <li><b>재방문</b>: D1·D7·D30 이 아직 비어 있는 유저만, 그날(KST 가입일 + N)이 닫혔으면 접속 기록으로 채운다
 * </ol>
 *
 * 멱등이다 — 같은 날 다시 돌려도 이미 분류한 유저·채운 값은 건드리지 않는다. 정의를 바꿨으면 {@link #rebuildAll} 로 처음부터 다시 만든다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FirstDayRetentionService {

    /** 원천 SQL 의 IN 목록 한 번에 넣는 유저 수 */
    static final int CHUNK_SIZE = 500;

    /** 이미 분류한 마지막 가입일보다 이만큼 앞부터 다시 읽는다(늦게 24시간이 찬 가입자) */
    static final int RESCAN_DAYS = 2;

    private final FirstDayRetentionRepository repository;
    private final UserDailyActivityRepository activityRepository;
    private final UserActivityProperties activityProperties;
    private final UserSourceReader userReader;
    private final MissionSourceReader missionReader;
    private final GuildSourceReader guildReader;
    private final FeedSourceReader feedReader;
    private final ChatSourceReader chatReader;
    private final TowerSourceReader towerReader;

    /**
     * @param today KST 오늘 — 재방문은 어제까지(활동 flush 가 끝난 날)만 확정한다
     * @param nowUtc 지금(UTC) — 가입 후 24시간이 지난 유저만 분류한다
     */
    @Transactional(transactionManager = "statsTransactionManager")
    public FirstDayRetentionRunResponse runDaily(LocalDate today, LocalDateTime nowUtc) {
        Optional<LocalDate> dataStart = activityRepository.findMinActivityDate();
        if (dataStart.isEmpty()) {
            log.info("첫날 리텐션: 접속 기록이 없어 건너뜀");
            return new FirstDayRetentionRunResponse(null, 0, 0);
        }
        int classified = classifyNewSignups(dataStart.get(), nowUtc);
        int updated = fillRetention(today.minusDays(1));
        log.info(
                "첫날 리텐션 집계: dataStart={}, classified={}, retentionUpdated={}",
                dataStart.get(),
                classified,
                updated);
        return new FirstDayRetentionRunResponse(dataStart.get(), classified, updated);
    }

    /** 정의를 바꿨을 때 — 표를 비우고 접속 기록 시작일부터 다시 만든다 */
    @Transactional(transactionManager = "statsTransactionManager")
    public FirstDayRetentionRunResponse rebuildAll(LocalDate today, LocalDateTime nowUtc) {
        repository.deleteAllInBatch();
        return runDaily(today, nowUtc);
    }

    int classifyNewSignups(LocalDate dataStart, LocalDateTime nowUtc) {
        LocalDate from =
                repository
                        .findMaxSignupDate()
                        .map(d -> d.minusDays(RESCAN_DAYS))
                        .filter(d -> d.isAfter(dataStart))
                        .orElse(dataStart);
        LocalDateTime fromUtc = KpiDates.utcStart(from);
        LocalDateTime toUtc = nowUtc.minus(FirstDayWindow.LENGTH);
        if (!toUtc.isAfter(fromUtc)) {
            return 0;
        }
        ExcludedUsers excluded = ExcludedUsers.of(activityProperties.getExcludedUserIds());
        List<SignupRecord> signups = userReader.findSignups(fromUtc, toUtc, excluded);
        int classified = 0;
        for (List<SignupRecord> chunk : chunks(signups)) {
            Set<String> existing =
                    new HashSet<>(
                            repository.findExistingUserIds(
                                    chunk.stream().map(SignupRecord::userId).toList()));
            List<SignupRecord> fresh =
                    chunk.stream().filter(s -> !existing.contains(s.userId())).toList();
            if (!fresh.isEmpty()) {
                repository.saveAll(classify(fresh));
                classified += fresh.size();
            }
        }
        return classified;
    }

    /** 가입자 묶음의 첫날 완료·상호작용을 원천 DB 에서 읽어 행을 만든다 */
    List<FirstDayRetention> classify(List<SignupRecord> signups) {
        Map<String, LocalDateTime> signupAt = new LinkedHashMap<>();
        signups.forEach(s -> signupAt.put(s.userId(), s.createdAt()));
        List<String> ids = List.copyOf(signupAt.keySet());
        LocalDateTime from =
                signups.stream().map(SignupRecord::createdAt).min(Comparator.naturalOrder()).get();
        LocalDateTime to =
                signups.stream()
                        .map(SignupRecord::createdAt)
                        .max(Comparator.naturalOrder())
                        .get()
                        .plus(FirstDayWindow.LENGTH);

        Set<String> completed =
                FirstDayWindow.usersWithin(signupAt, missionReader.findCompletions(ids, from, to));
        List<UserEvent> interactions = new ArrayList<>();
        interactions.addAll(userReader.findFriendshipEvents(ids, from, to));
        interactions.addAll(guildReader.findJoins(ids, from, to));
        interactions.addAll(feedReader.findLikesAndComments(ids, from, to));
        interactions.addAll(chatReader.findDirectMessages(ids, from, to));
        interactions.addAll(towerReader.findChatsAndCheers(ids, from, to));
        Set<String> interacted = FirstDayWindow.usersWithin(signupAt, interactions);

        return signups.stream()
                .map(
                        s ->
                                FirstDayRetention.classify(
                                        s.userId(),
                                        s.createdAt(),
                                        toKstDate(s.createdAt()),
                                        SignupPlatform.fromDeviceType(s.signupDeviceType()),
                                        completed.contains(s.userId()),
                                        interacted.contains(s.userId())))
                .toList();
    }

    int fillRetention(LocalDate lastCompleteDate) {
        List<FirstDayRetention> open = repository.findOpen(lastCompleteDate.minusDays(1));
        if (open.isEmpty()) {
            return 0;
        }
        LocalDate firstDay =
                open.stream()
                        .map(FirstDayRetention::getSignupDate)
                        .min(Comparator.naturalOrder())
                        .get()
                        .plusDays(1);
        Set<String> users = new HashSet<>();
        open.forEach(r -> users.add(r.getUserId()));
        Map<String, Set<LocalDate>> activeDates = new HashMap<>();
        for (UserDailyActivity a :
                activityRepository.findAllByActivityDateBetween(firstDay, lastCompleteDate)) {
            if (users.contains(a.getUserId())) {
                activeDates
                        .computeIfAbsent(a.getUserId(), k -> new HashSet<>())
                        .add(a.getActivityDate());
            }
        }
        int updated = 0;
        for (FirstDayRetention row : open) {
            if (row.applyRetention(
                    activeDates.getOrDefault(row.getUserId(), Set.of()), lastCompleteDate)) {
                updated++;
            }
        }
        return updated;
    }

    static LocalDate toKstDate(LocalDateTime utc) {
        return utc.atZone(ZoneOffset.UTC).withZoneSameInstant(KpiDates.KST).toLocalDate();
    }

    private static <T> List<List<T>> chunks(List<T> items) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i += CHUNK_SIZE) {
            chunks.add(items.subList(i, Math.min(items.size(), i + CHUNK_SIZE)));
        }
        return chunks;
    }
}
