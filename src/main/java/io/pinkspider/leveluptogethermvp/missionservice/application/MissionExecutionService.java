package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.userservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.ExperienceHistory.ExpSourceType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionExecutionService {

    private final MissionExecutionRepository executionRepository;
    private final MissionParticipantRepository participantRepository;
    private final UserExperienceService userExperienceService;

    @Transactional
    public void generateExecutionsForParticipant(MissionParticipant participant) {
        Mission mission = participant.getMission();
        LocalDate startDate = mission.getStartDate() != null
            ? mission.getStartDate().toLocalDate()
            : LocalDate.now();
        LocalDate endDate = mission.getEndDate() != null
            ? mission.getEndDate().toLocalDate()
            : (mission.getDurationDays() != null
                ? startDate.plusDays(mission.getDurationDays())
                : startDate.plusDays(30));

        MissionInterval interval = mission.getMissionInterval() != null
            ? mission.getMissionInterval()
            : MissionInterval.DAILY;

        List<MissionExecution> executions = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            MissionExecution execution = MissionExecution.builder()
                .participant(participant)
                .executionDate(currentDate)
                .status(ExecutionStatus.PENDING)
                .build();
            executions.add(execution);
            currentDate = currentDate.plusDays(interval.getDays());
        }

        executionRepository.saveAll(executions);
        log.info("미션 수행 일정 생성: participantId={}, 총 {}개", participant.getId(), executions.size());
    }

    @Transactional
    public MissionExecutionResponse completeExecution(Long executionId, String userId, String note) {
        MissionExecution execution = findExecutionById(executionId);
        validateExecutionOwner(execution, userId);

        execution.complete();
        if (note != null) {
            execution.setNote(note);
        }

        Mission mission = execution.getParticipant().getMission();
        int expEarned = mission.getExpPerCompletion() != null ? mission.getExpPerCompletion() : 10;
        execution.setExpEarned(expEarned);

        userExperienceService.addExperience(
            userId,
            expEarned,
            ExpSourceType.MISSION_EXECUTION,
            mission.getId(),
            "미션 수행 완료: " + mission.getTitle()
        );

        updateParticipantProgress(execution.getParticipant());

        checkAndGrantFullCompletionBonus(execution.getParticipant());

        log.info("미션 수행 완료: executionId={}, userId={}, exp={}", executionId, userId, expEarned);
        return MissionExecutionResponse.from(execution);
    }

    @Transactional
    public MissionExecutionResponse completeExecutionByDate(Long missionId, String userId, LocalDate date, String note) {
        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), date)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + date));

        return completeExecution(execution.getId(), userId, note);
    }

    @Transactional
    public int markMissedExecutions() {
        LocalDate today = LocalDate.now();
        int count = executionRepository.markMissedExecutions(today);
        log.info("미실행 처리된 수행 기록: {}개", count);
        return count;
    }

    public List<MissionExecutionResponse> getExecutionsByParticipant(Long participantId) {
        return executionRepository.findByParticipantId(participantId).stream()
            .map(MissionExecutionResponse::from)
            .toList();
    }

    public List<MissionExecutionResponse> getExecutionsByMissionAndUser(Long missionId, String userId) {
        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));

        return executionRepository.findByParticipantId(participant.getId()).stream()
            .map(MissionExecutionResponse::from)
            .toList();
    }

    public List<MissionExecutionResponse> getTodayExecutions(String userId) {
        return executionRepository.findByUserIdAndExecutionDate(userId, LocalDate.now()).stream()
            .map(MissionExecutionResponse::from)
            .toList();
    }

    public MissionExecutionResponse getExecutionByDate(Long missionId, String userId, LocalDate date) {
        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), date)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + date));

        return MissionExecutionResponse.from(execution);
    }

    @Transactional
    public MissionExecutionResponse completeExecution(Long missionId, String userId, LocalDate executionDate, String note) {
        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        return completeExecution(execution.getId(), userId, note);
    }

    public List<MissionExecutionResponse> getExecutionsForMission(Long missionId, String userId) {
        return getExecutionsByMissionAndUser(missionId, userId);
    }

    public List<MissionExecutionResponse> getExecutionsByDateRange(Long missionId, String userId,
                                                                    LocalDate startDate, LocalDate endDate) {
        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));

        return executionRepository.findByParticipantIdAndExecutionDateBetween(
                participant.getId(), startDate, endDate).stream()
            .map(MissionExecutionResponse::from)
            .toList();
    }

    public double getCompletionRate(Long missionId, String userId) {
        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));

        long totalExecutions = executionRepository.findByParticipantId(participant.getId()).size();
        if (totalExecutions == 0) {
            return 0.0;
        }

        long completedExecutions = executionRepository.countByParticipantIdAndStatus(
            participant.getId(), ExecutionStatus.COMPLETED);

        return (double) completedExecutions / totalExecutions * 100;
    }

    private void updateParticipantProgress(MissionParticipant participant) {
        long totalExecutions = executionRepository.findByParticipantId(participant.getId()).size();
        long completedExecutions = executionRepository.countByParticipantIdAndStatus(
            participant.getId(), ExecutionStatus.COMPLETED);

        int progress = totalExecutions > 0
            ? (int) ((completedExecutions * 100) / totalExecutions)
            : 0;
        participant.updateProgress(progress);
    }

    private void checkAndGrantFullCompletionBonus(MissionParticipant participant) {
        long totalExecutions = executionRepository.findByParticipantId(participant.getId()).size();
        long completedExecutions = executionRepository.countByParticipantIdAndStatus(
            participant.getId(), ExecutionStatus.COMPLETED);

        if (totalExecutions > 0 && completedExecutions == totalExecutions) {
            Mission mission = participant.getMission();
            int bonusExp = mission.getBonusExpOnFullCompletion() != null
                ? mission.getBonusExpOnFullCompletion() : 50;

            userExperienceService.addExperience(
                participant.getUserId(),
                bonusExp,
                ExpSourceType.MISSION_FULL_COMPLETION,
                mission.getId(),
                "미션 전체 완료 보너스: " + mission.getTitle()
            );

            participant.complete();
            log.info("미션 전체 완료 보너스 지급: userId={}, missionId={}, bonusExp={}",
                participant.getUserId(), mission.getId(), bonusExp);
        }
    }

    private MissionExecution findExecutionById(Long executionId) {
        return executionRepository.findById(executionId)
            .orElseThrow(() -> new IllegalArgumentException("수행 기록을 찾을 수 없습니다: " + executionId));
    }

    private void validateExecutionOwner(MissionExecution execution, String userId) {
        if (!execution.getParticipant().getUserId().equals(userId)) {
            throw new IllegalStateException("본인의 수행 기록만 완료할 수 있습니다.");
        }
    }
}
