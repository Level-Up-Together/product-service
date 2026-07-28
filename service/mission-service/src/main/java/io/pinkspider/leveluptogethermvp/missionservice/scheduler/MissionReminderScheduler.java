package io.pinkspider.leveluptogethermvp.missionservice.scheduler;

import io.pinkspider.global.event.MissionReminderEvent;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-282: 미션 푸시 리마인더 스케줄러.
 *
 * <p>유저가 미션 생성/수정 시 설정한 리마인더(요일 + 시각, 유저 로컬 기준)에 맞춰
 * {@link MissionReminderEvent}를 발행한다. 이벤트는 NotificationEventListener 가 소비해
 * 알림 row 생성 + 유저 locale 다국어 푸시 발송으로 이어진다.
 *
 * <p>동작 방식: UTC 기준 매시 정각/30분에 실행되어, 리마인더가 설정된 활성 개인 미션 각각에 대해
 * 생성자의 선호 타임존(preferred_timezone) 로컬 시각을 계산한다. 로컬 시(hour)가 설정 시각과
 * 일치하고 로컬 분(minute)이 0-29 구간일 때만 발송하므로, 정수/30분 오프셋 타임존 모두
 * 시간당 정확히 1회 매칭된다 (예: Asia/Seoul은 UTC 정각 실행에서, Asia/Kolkata(+5:30)는
 * UTC 30분 실행에서 로컬 정각이 된다).
 *
 * <p>당일(유저 로컬 날짜) 이미 완료한 미션은 리마인더를 보내지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MissionReminderScheduler {

    private final MissionRepository missionRepository;
    private final MissionParticipantRepository participantRepository;
    private final MissionExecutionRepository executionRepository;
    private final DailyMissionInstanceRepository instanceRepository;
    private final UserQueryFacade userQueryFacadeService;
    private final ApplicationEventPublisher eventPublisher;

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    // 테스트에서 고정 시각 주입용 (package-private setter)
    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Scheduled(cron = "0 0,30 * * * *")
    @SchedulerLock(name = "MissionReminderScheduler_sendReminders",
        lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    @Transactional(transactionManager = "missionTransactionManager", readOnly = true)
    public void sendReminders() {
        List<Mission> missions = missionRepository.findActiveReminderMissions();
        if (missions.isEmpty()) {
            return;
        }

        int sent = 0;
        for (Mission mission : missions) {
            try {
                if (sendReminderIfDue(mission)) {
                    sent++;
                }
            } catch (Exception e) {
                log.error("미션 리마인더 처리 실패: missionId={}, error={}",
                    mission.getId(), e.getMessage(), e);
            }
        }

        if (sent > 0) {
            log.info("미션 리마인더 발송: candidates={}, sent={}", missions.size(), sent);
        }
    }

    private boolean sendReminderIfDue(Mission mission) {
        String userId = mission.getCreatorId();
        ZonedDateTime userNow = ZonedDateTime.now(clock.withZone(resolveUserZone(userId)));

        // 유저 로컬 시각 매칭 — 30분 격자에서 시간당 1회만 (minute 0-29 구간)
        if (userNow.getHour() != mission.getReminderHour() || userNow.getMinute() >= 30) {
            return false;
        }
        if (!mission.getReminderDaysOfWeekList().contains(userNow.getDayOfWeek())) {
            return false;
        }
        // 오늘(유저 로컬 날짜) 이미 완료했으면 리마인더 불필요
        if (isCompletedToday(mission, userId, userNow)) {
            return false;
        }

        eventPublisher.publishEvent(
            new MissionReminderEvent(userId, mission.getId(), mission.getTitle()));
        return true;
    }

    private boolean isCompletedToday(Mission mission, String userId, ZonedDateTime userNow) {
        MissionParticipant participant = participantRepository
            .findByMissionIdAndUserId(mission.getId(), userId)
            .orElse(null);
        if (participant == null) {
            return false;
        }

        if (Boolean.TRUE.equals(mission.getIsPinned())) {
            return instanceRepository.countCompletedByParticipantIdAndDate(
                participant.getId(), userNow.toLocalDate()) > 0;
        }
        return executionRepository
            .findByParticipantIdAndExecutionDate(participant.getId(), userNow.toLocalDate())
            .map(execution -> execution.getStatus() == ExecutionStatus.COMPLETED)
            .orElse(false);
    }

    private ZoneId resolveUserZone(String userId) {
        try {
            return ZoneId.of(userQueryFacadeService.getPreferredTimezone(userId));
        } catch (Exception e) {
            return DEFAULT_ZONE;
        }
    }
}
