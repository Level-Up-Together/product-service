package io.pinkspider.leveluptogethermvp.missionservice.scheduler;

import io.pinkspider.leveluptogethermvp.missionservice.application.DailyMissionInstanceService;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionExecutionService;
import io.pinkspider.leveluptogethermvp.missionservice.config.MissionExecutionProperties;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 고정 미션 일일 인스턴스 생성 스케줄러
 *
 * 매일 새벽에 실행되어:
 * 1. 지난 날짜의 미완료 인스턴스를 MISSED 처리
 * 2. 오늘 날짜의 인스턴스를 생성
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailyMissionInstanceScheduler {

    private final DailyMissionInstanceRepository instanceRepository;
    private final MissionExecutionRepository executionRepository;
    private final MissionParticipantRepository participantRepository;
    private final DailyMissionInstanceService dailyMissionInstanceService;
    private final MissionExecutionService missionExecutionService;
    private final MissionExecutionProperties missionExecutionProperties;

    private static final int BATCH_SIZE = 100;

    /**
     * 매일 새벽 00:05에 실행
     * - 지난 날짜 미완료 인스턴스 MISSED 처리
     * - 오늘 인스턴스 생성
     */
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional(transactionManager = "missionTransactionManager")
    public void generateDailyInstances() {
        log.info("=== 고정 미션 일일 인스턴스 생성 스케줄러 시작 ===");

        LocalDate today = LocalDate.now();

        try {
            // 1. 지난 날짜의 IN_PROGRESS 미션 자동 완료 (Saga 경유 → 경험치 정상 지급)
            int autoCompletedInstances = autoCompletePastDayInProgressInstances(today);
            int autoCompletedExecutions = autoCompletePastDayInProgressExecutions(today);
            if (autoCompletedInstances > 0 || autoCompletedExecutions > 0) {
                log.info("자정 자동 완료 처리: pinnedInstances={}, regularExecutions={}",
                    autoCompletedInstances, autoCompletedExecutions);
            }

            // 2. 지난 날짜의 미완료 인스턴스 MISSED 처리
            int missedCount = markMissedInstances(today);
            log.info("미완료 인스턴스 MISSED 처리 완료: count={}", missedCount);

            // 3. 지난 날짜의 미완료 일반 미션 MISSED 처리
            int missedExecutionCount = missionExecutionService.markMissedExecutions();
            log.info("미완료 일반 미션 MISSED 처리 완료: count={}", missedExecutionCount);

            // 4. 오늘 인스턴스 생성
            int createdCount = createTodayInstances(today);
            log.info("오늘 인스턴스 생성 완료: date={}, count={}", today, createdCount);

        } catch (Exception e) {
            log.error("고정 미션 인스턴스 생성 실패", e);
            // TODO: Slack 알림 추가
        }

        log.info("=== 고정 미션 일일 인스턴스 생성 스케줄러 종료 ===");
    }

    /**
     * 지난 날짜의 미완료 인스턴스를 MISSED 처리
     */
    private int markMissedInstances(LocalDate today) {
        return instanceRepository.markMissedInstances(today);
    }

    /**
     * 오늘 날짜의 인스턴스를 생성
     */
    private int createTodayInstances(LocalDate today) {
        // 모든 활성 고정 미션 참여자 조회
        List<MissionParticipant> participants = participantRepository.findAllActivePinnedMissionParticipants();

        if (participants.isEmpty()) {
            log.info("활성 고정 미션 참여자 없음");
            return 0;
        }

        log.info("활성 고정 미션 참여자 수: {}", participants.size());

        List<DailyMissionInstance> instancesToCreate = new ArrayList<>();
        int skippedCount = 0;

        for (MissionParticipant participant : participants) {
            // 이미 오늘 인스턴스가 있는지 확인
            if (instanceRepository.existsByParticipantIdAndInstanceDate(participant.getId(), today)) {
                skippedCount++;
                continue;
            }

            // 인스턴스 생성
            DailyMissionInstance instance = DailyMissionInstance.createFrom(participant, today);
            instancesToCreate.add(instance);

            // 배치 저장
            if (instancesToCreate.size() >= BATCH_SIZE) {
                instanceRepository.saveAll(instancesToCreate);
                log.debug("배치 저장 완료: count={}", instancesToCreate.size());
                instancesToCreate.clear();
            }
        }

        // 남은 인스턴스 저장
        if (!instancesToCreate.isEmpty()) {
            instanceRepository.saveAll(instancesToCreate);
            log.debug("최종 배치 저장 완료: count={}", instancesToCreate.size());
        }

        int createdCount = participants.size() - skippedCount;
        log.info("인스턴스 생성 결과: created={}, skipped={}", createdCount, skippedCount);

        return createdCount;
    }

    /**
     * 특정 사용자의 오늘 PENDING 인스턴스를 조회하거나 생성 (실시간 참여 시 사용)
     *
     * @param participant 참여자
     * @return PENDING 상태 인스턴스 (없으면 새로 생성)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public DailyMissionInstance createOrGetTodayInstance(MissionParticipant participant) {
        LocalDate today = LocalDate.now();

        // PENDING 상태 인스턴스가 있으면 반환, 없으면 새로 생성
        return instanceRepository.findPendingByParticipantIdAndDate(participant.getId(), today)
            .stream()
            .findFirst()
            .orElseGet(() -> {
                int nextSequence = instanceRepository.findMaxSequenceNumber(participant.getId(), today) + 1;
                DailyMissionInstance instance = DailyMissionInstance.createFrom(participant, today, nextSequence);
                return instanceRepository.save(instance);
            });
    }

    /**
     * 지난 날짜의 IN_PROGRESS 고정 미션 인스턴스 자동 완료
     *
     * 날짜가 바뀌었는데 완료되지 않은 미션을 Saga로 자동 완료하여 경험치를 정상 지급합니다.
     * Saga 실패 시 엔티티 레벨 직접 완료로 폴백합니다.
     */
    private int autoCompletePastDayInProgressInstances(LocalDate today) {
        List<DailyMissionInstance> inProgressInstances = instanceRepository.findInProgressBeforeDate(today);

        int count = 0;
        for (DailyMissionInstance instance : inProgressInstances) {
            String userId = instance.getParticipant().getUserId();
            try {
                dailyMissionInstanceService.completeInstance(instance.getId(), userId, null, false);
                count++;
                log.info("자정 자동 완료 (고정 미션): instanceId={}, userId={}, date={}, title={}",
                    instance.getId(), userId, instance.getInstanceDate(), instance.getMissionTitle());
            } catch (Exception e) {
                // Saga 실패 시 엔티티 레벨 직접 완료 (캘린더에는 표시되지만 gamification XP 미지급)
                log.warn("자정 Saga 자동 완료 실패, 직접 완료 처리: instanceId={}, error={}",
                    instance.getId(), e.getMessage());
                try {
                    if (instance.autoCompleteForDateChange(missionExecutionProperties.getBaseExp())) {
                        instanceRepository.save(instance);
                        count++;
                    }
                } catch (Exception fallbackError) {
                    log.error("자정 자동 완료 폴백 실패: instanceId={}", instance.getId(), fallbackError);
                }
            }
        }
        return count;
    }

    /**
     * 지난 날짜의 IN_PROGRESS 일반 미션 실행 자동 완료
     *
     * 날짜가 바뀌었는데 완료되지 않은 일반 미션을 Saga로 자동 완료하여 경험치를 정상 지급합니다.
     * Saga 실패 시 엔티티 레벨 직접 완료로 폴백합니다.
     */
    private int autoCompletePastDayInProgressExecutions(LocalDate today) {
        List<MissionExecution> inProgressExecutions = executionRepository.findInProgressBeforeDate(today);

        int count = 0;
        for (MissionExecution execution : inProgressExecutions) {
            String userId = execution.getParticipant().getUserId();
            try {
                missionExecutionService.completeExecution(execution.getId(), userId, null, false);
                count++;
                log.info("자정 자동 완료 (일반 미션): executionId={}, userId={}, date={}",
                    execution.getId(), userId, execution.getExecutionDate());
            } catch (Exception e) {
                // Saga 실패 시 엔티티 레벨 직접 완료
                log.warn("자정 Saga 자동 완료 실패, 직접 완료 처리: executionId={}, error={}",
                    execution.getId(), e.getMessage());
                try {
                    if (execution.autoCompleteForDateChange(missionExecutionProperties.getBaseExp())) {
                        executionRepository.save(execution);
                        // 일반 미션 participant를 COMPLETED로 변경하여 미션 목록에서 제외
                        MissionParticipant participant = execution.getParticipant();
                        if (participant != null && !Boolean.TRUE.equals(participant.getMission().getIsPinned())
                            && participant.getStatus() != ParticipantStatus.COMPLETED) {
                            participant.setStatus(ParticipantStatus.COMPLETED);
                            participant.setProgress(100);
                            participant.setCompletedAt(java.time.LocalDateTime.now());
                            participantRepository.save(participant);
                        }
                        count++;
                    }
                } catch (Exception fallbackError) {
                    log.error("자정 자동 완료 폴백 실패: executionId={}", execution.getId(), fallbackError);
                }
            }
        }
        return count;
    }

    /**
     * 수동 실행: 특정 날짜의 인스턴스 생성 (관리자용)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public int generateInstancesForDate(LocalDate date) {
        log.info("수동 인스턴스 생성 시작: date={}", date);

        // 활성 고정 미션 참여자 조회
        List<MissionParticipant> participants = participantRepository.findAllActivePinnedMissionParticipants();

        int createdCount = 0;
        for (MissionParticipant participant : participants) {
            if (!instanceRepository.existsByParticipantIdAndInstanceDate(participant.getId(), date)) {
                DailyMissionInstance instance = DailyMissionInstance.createFrom(participant, date);
                instanceRepository.save(instance);
                createdCount++;
            }
        }

        log.info("수동 인스턴스 생성 완료: date={}, count={}", date, createdCount);
        return createdCount;
    }
}
