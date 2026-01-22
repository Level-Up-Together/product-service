package io.pinkspider.leveluptogethermvp.missionservice.scheduler;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
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
    private final MissionParticipantRepository participantRepository;

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
            // 1. 지난 날짜의 미완료 인스턴스 MISSED 처리
            int missedCount = markMissedInstances(today);
            log.info("미완료 인스턴스 MISSED 처리 완료: count={}", missedCount);

            // 2. 오늘 인스턴스 생성
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
     * 특정 사용자의 오늘 인스턴스를 수동 생성 (실시간 참여 시 사용)
     *
     * @param participant 참여자
     * @return 생성된 인스턴스 (이미 존재하면 기존 인스턴스 반환)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public DailyMissionInstance createOrGetTodayInstance(MissionParticipant participant) {
        LocalDate today = LocalDate.now();

        // 이미 존재하면 반환
        return instanceRepository.findByParticipantIdAndInstanceDate(participant.getId(), today)
            .orElseGet(() -> {
                DailyMissionInstance instance = DailyMissionInstance.createFrom(participant, today);
                return instanceRepository.save(instance);
            });
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
