package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 1: 고정 미션 인스턴스 데이터 로드 및 검증
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoadPinnedMissionDataStep implements SagaStep<MissionCompletionContext> {

    private final DailyMissionInstanceRepository instanceRepository;

    @Override
    public String getName() {
        return "LoadPinnedMissionData";
    }

    @Override
    public Predicate<MissionCompletionContext> shouldExecute() {
        return MissionCompletionContext::isPinned;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "missionTransactionManager")
    public SagaStepResult execute(MissionCompletionContext context) {
        Long instanceId = context.getInstanceId();
        String userId = context.getUserId();

        log.debug("Loading pinned mission data: instanceId={}, userId={}", instanceId, userId);

        try {
            // 인스턴스 조회 (연관 데이터 포함)
            DailyMissionInstance instance = instanceRepository.findByIdWithParticipantAndMission(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("인스턴스를 찾을 수 없습니다: " + instanceId));

            // 소유자 검증
            MissionParticipant participant = instance.getParticipant();
            if (!participant.getUserId().equals(userId)) {
                return SagaStepResult.failure("인스턴스 소유자가 아닙니다.");
            }

            // 상태 검증
            if (instance.getStatus() != ExecutionStatus.IN_PROGRESS) {
                return SagaStepResult.failure("진행 중인 인스턴스만 완료할 수 있습니다. 현재 상태: " + instance.getStatus());
            }

            Mission mission = participant.getMission();

            // Context에 데이터 설정
            context.setInstance(instance);
            context.setParticipant(participant);
            context.setMission(mission);
            context.setMissionTitle(instance.getMissionTitle());
            context.setCategoryId(instance.getCategoryId());
            context.setCategoryName(instance.getCategoryName());
            context.setInstanceDate(instance.getInstanceDate());

            // 보상용 데이터 저장
            context.addCompensationData(
                MissionCompletionContext.CompensationKeys.INSTANCE_STATUS_BEFORE,
                instance.getStatus());

            log.info("Pinned mission data loaded: instanceId={}, missionTitle={}",
                instanceId, instance.getMissionTitle());

            return SagaStepResult.success("고정 미션 데이터 로드 완료");

        } catch (Exception e) {
            log.error("Failed to load pinned mission data: {}", e.getMessage(), e);
            return SagaStepResult.failure(e);
        }
    }

    @Override
    public SagaStepResult compensate(MissionCompletionContext context) {
        // 데이터 로드는 보상 작업 불필요
        return SagaStepResult.success("보상 작업 불필요");
    }
}
