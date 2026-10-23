package io.pinkspider.leveluptogethermvp.statsservice.kpi.application;

import io.pinkspider.leveluptogethermvp.statsservice.activity.config.UserActivityProperties;
import io.pinkspider.leveluptogethermvp.statsservice.activity.domain.entity.UserDailyActivity;
import io.pinkspider.leveluptogethermvp.statsservice.activity.infrastructure.UserDailyActivityRepository;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.KpiDates;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.AttendanceDay;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.CohortRetention;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.CohortRetentionId;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.enums.RetentionMetric;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.enums.RetentionSource;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.CohortRetentionRepository;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.GamificationSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.UserSourceReader;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-601: 가입 코호트 리텐션. 코호트 = 그 KST 날짜 가입자(테스트 계정 제외). 지표 창이 닫힌(lastCompleteDate 이내) 것만 저장한다.
 *
 * <ul>
 *   <li>REQUEST — {@code user_daily_activity}. 데이터 시작일(min activity_date) 이전 창은 계산하지 않는다(0 으로 오염
 *       방지)
 *   <li>ATTENDANCE — {@code attendance_record}. 전 기간 소급 가능
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CohortRetentionService {

    private final CohortRetentionRepository retentionRepository;
    private final UserDailyActivityRepository activityRepository;
    private final UserActivityProperties activityProperties;
    private final UserSourceReader userReader;
    private final GamificationSourceReader gamificationReader;

    /**
     * @param lastCompleteDate 데이터가 완성된 마지막 날 (보통 어제)
     * @return 저장(신규+갱신)한 행 수
     */
    @Transactional(transactionManager = "statsTransactionManager")
    public int rebuild(LocalDate cohortFrom, LocalDate cohortTo, LocalDate lastCompleteDate) {
        if (cohortFrom.isAfter(cohortTo)) {
            return 0;
        }
        LocalDate windowEnd =
                min(cohortTo.plusDays(RetentionMetric.MAX_OFFSET_DAYS), lastCompleteDate);
        LocalDate windowStart = cohortFrom.plusDays(1);
        Map<LocalDate, Set<String>> requestActive = new HashMap<>();
        Map<LocalDate, Set<String>> attendanceActive = new HashMap<>();
        if (!windowStart.isAfter(windowEnd)) {
            Set<String> excluded = activityProperties.getExcludedUserIds();
            for (UserDailyActivity a :
                    activityRepository.findAllByActivityDateBetween(windowStart, windowEnd)) {
                if (excluded == null || !excluded.contains(a.getUserId())) {
                    requestActive
                            .computeIfAbsent(a.getActivityDate(), d -> new HashSet<>())
                            .add(a.getUserId());
                }
            }
            for (AttendanceDay a : gamificationReader.findAttendance(windowStart, windowEnd)) {
                attendanceActive.computeIfAbsent(a.date(), d -> new HashSet<>()).add(a.userId());
            }
        }
        Optional<LocalDate> requestDataStart = activityRepository.findMinActivityDate();

        int written = 0;
        for (LocalDate cohort = cohortFrom;
                !cohort.isAfter(cohortTo);
                cohort = cohort.plusDays(1)) {
            List<String> members =
                    userReader.findSignupUserIds(
                            KpiDates.utcStart(cohort), KpiDates.utcEnd(cohort));
            for (RetentionMetric metric : RetentionMetric.values()) {
                if (!metric.isClosed(cohort, lastCompleteDate)) {
                    continue;
                }
                written +=
                        upsert(
                                cohort,
                                metric,
                                RetentionSource.ATTENDANCE,
                                members,
                                attendanceActive);
                boolean requestWindowCovered =
                        requestDataStart.isPresent()
                                && !metric.windowStart(cohort).isBefore(requestDataStart.get());
                if (requestWindowCovered) {
                    written +=
                            upsert(cohort, metric, RetentionSource.REQUEST, members, requestActive);
                }
            }
        }
        log.info(
                "코호트 리텐션 재계산: cohort {}~{}, lastComplete={}, rows={}",
                cohortFrom,
                cohortTo,
                lastCompleteDate,
                written);
        return written;
    }

    private int upsert(
            LocalDate cohort,
            RetentionMetric metric,
            RetentionSource source,
            List<String> members,
            Map<LocalDate, Set<String>> activeByDate) {
        int retained =
                countRetained(
                        members,
                        metric.windowStart(cohort),
                        metric.windowEnd(cohort),
                        activeByDate);
        CohortRetentionId id = new CohortRetentionId(cohort, metric.name(), source.name());
        CohortRetention row =
                retentionRepository
                        .findById(id)
                        .map(
                                existing -> {
                                    existing.update(members.size(), retained);
                                    return existing;
                                })
                        .orElseGet(
                                () ->
                                        CohortRetention.create(
                                                cohort, metric, source, members.size(), retained));
        retentionRepository.save(row);
        return 1;
    }

    static int countRetained(
            List<String> members,
            LocalDate from,
            LocalDate to,
            Map<LocalDate, Set<String>> activeByDate) {
        if (members.isEmpty()) {
            return 0;
        }
        Set<String> seen = new HashSet<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Set<String> active = activeByDate.get(d);
            if (active == null) {
                continue;
            }
            for (String m : members) {
                if (active.contains(m)) {
                    seen.add(m);
                }
            }
        }
        return seen.size();
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
