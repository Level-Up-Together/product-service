package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.global.saga.SagaStatus;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildExperienceService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildExperienceHistory.GuildExpSourceType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyCalendarResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyCalendarResponse.DailyMission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionSaga;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.UserStatsService;
import io.pinkspider.leveluptogethermvp.userservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.userservice.notification.application.NotificationService;
import io.pinkspider.leveluptogethermvp.userservice.quest.application.QuestService;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestActionType;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.ExperienceHistory.ExpSourceType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final GuildExperienceService guildExperienceService;
    private final UserStatsService userStatsService;
    private final AchievementService achievementService;
    private final QuestService questService;
    private final NotificationService notificationService;
    private final MissionCompletionSaga missionCompletionSaga;

    @Transactional
    public void generateExecutionsForParticipant(MissionParticipant participant) {
        Mission mission = participant.getMission();
        LocalDate startDate = mission.getStartAt() != null
            ? mission.getStartAt().toLocalDate()
            : LocalDate.now();
        LocalDate endDate = mission.getEndAt() != null
            ? mission.getEndAt().toLocalDate()
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

    /**
     * Saga 패턴을 사용한 미션 수행 완료 처리
     *
     * 여러 서비스에 걸친 트랜잭션을 안전하게 처리하고,
     * 실패 시 자동으로 보상 트랜잭션을 실행하여 데이터 일관성 보장
     *
     * 각 Saga step은 REQUIRES_NEW 트랜잭션을 사용하므로
     * 외부 메소드에서 별도의 트랜잭션 관리 불필요
     *
     * @param executionId 수행 기록 ID
     * @param userId 사용자 ID
     * @param note 메모
     * @return 미션 수행 응답
     */
    public MissionExecutionResponse completeExecution(Long executionId, String userId, String note) {
        log.info("미션 수행 완료 요청 (Saga): executionId={}, userId={}", executionId, userId);

        // Saga 실행
        SagaResult<MissionCompletionContext> result = missionCompletionSaga.execute(executionId, userId, note);

        if (result.isSuccess()) {
            return missionCompletionSaga.toResponse(result);
        } else {
            // Saga 실패 시 예외 발생
            String errorMessage = String.format(
                "미션 완료 처리 실패 (sagaId=%s, status=%s): %s",
                result.getSagaId(),
                result.getStatus(),
                result.getMessage()
            );

            if (result.isCompensated()) {
                log.info("미션 완료 실패 - 보상 트랜잭션 완료: sagaId={}", result.getSagaId());
            }

            throw new IllegalStateException(errorMessage, result.getException());
        }
    }

    /**
     * 레거시 방식의 미션 수행 완료 처리 (Saga 미적용)
     *
     * @deprecated Saga 패턴 적용된 completeExecution 사용 권장
     */
    @Deprecated
    @Transactional
    public MissionExecutionResponse completeExecutionLegacy(Long executionId, String userId, String note) {
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
            "미션 수행 완료: " + mission.getTitle(),
            mission.getCategoryName()
        );

        // 길드 미션인 경우 길드 경험치 지급
        if (mission.isGuildMission() && mission.getGuildId() != null) {
            int guildExpEarned = mission.getGuildExpPerCompletion() != null ? mission.getGuildExpPerCompletion() : 5;
            try {
                guildExperienceService.addExperience(
                    Long.parseLong(mission.getGuildId()),
                    guildExpEarned,
                    GuildExpSourceType.GUILD_MISSION_EXECUTION,
                    mission.getId(),
                    userId,
                    "길드 미션 수행: " + mission.getTitle()
                );
                log.info("길드 경험치 지급: guildId={}, userId={}, exp={}", mission.getGuildId(), userId, guildExpEarned);
            } catch (Exception e) {
                log.warn("길드 경험치 지급 실패: guildId={}, error={}", mission.getGuildId(), e.getMessage());
            }
        }

        updateParticipantProgress(execution.getParticipant());

        checkAndGrantFullCompletionBonus(execution.getParticipant());

        // 업적 및 통계 업데이트
        boolean isGuildMission = mission.isGuildMission();
        try {
            userStatsService.recordMissionCompletion(userId, isGuildMission);
            var userStats = userStatsService.getOrCreateUserStats(userId);
            achievementService.checkMissionAchievements(userId, userStats.getTotalMissionCompletions(), isGuildMission);
            achievementService.checkStreakAchievements(userId, userStats.getCurrentStreak());
        } catch (Exception e) {
            log.warn("업적 업데이트 실패: userId={}, error={}", userId, e.getMessage());
        }

        // 퀘스트 진행도 업데이트
        try {
            questService.incrementQuestProgress(userId, QuestActionType.COMPLETE_MISSION);
            questService.updateQuestProgress(userId, QuestActionType.COMPLETE_MISSIONS,
                userStatsService.getOrCreateUserStats(userId).getTotalMissionCompletions());
            if (isGuildMission) {
                questService.incrementQuestProgress(userId, QuestActionType.COMPLETE_GUILD_MISSION);
            }
            questService.updateQuestProgress(userId, QuestActionType.GAIN_EXP, expEarned);
        } catch (Exception e) {
            log.warn("퀘스트 업데이트 실패: userId={}, error={}", userId, e.getMessage());
        }

        // 미션 완료 알림
        try {
            notificationService.notifyMissionCompleted(userId, mission.getTitle(), mission.getId());
        } catch (Exception e) {
            log.warn("알림 발송 실패: userId={}, error={}", userId, e.getMessage());
        }

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

    /**
     * 미션 수행 시작
     * 이미 진행 중인 미션이 있으면 예외 발생
     */
    @Transactional
    public MissionExecutionResponse startExecution(Long missionId, String userId, LocalDate executionDate) {
        // 이미 진행 중인 미션이 있는지 확인
        executionRepository.findInProgressByUserId(userId).ifPresent(inProgressExecution -> {
            String inProgressMissionTitle = inProgressExecution.getParticipant().getMission().getTitle();
            throw new IllegalStateException(
                String.format("이미 진행 중인 미션이 있습니다: %s (ID: %d). 해당 미션을 완료하거나 취소한 후 시작해주세요.",
                    inProgressMissionTitle, inProgressExecution.getParticipant().getMission().getId())
            );
        });

        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        execution.start();
        executionRepository.save(execution);

        log.info("미션 수행 시작: missionId={}, userId={}, executionDate={}", missionId, userId, executionDate);
        return MissionExecutionResponse.from(execution);
    }

    /**
     * 진행 중인 미션 취소 (PENDING 상태로 되돌림)
     */
    @Transactional
    public MissionExecutionResponse skipExecution(Long missionId, String userId, LocalDate executionDate) {
        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        execution.skip();
        executionRepository.save(execution);

        log.info("미션 수행 취소: missionId={}, userId={}, executionDate={}", missionId, userId, executionDate);
        return MissionExecutionResponse.from(execution);
    }

    /**
     * 진행 중인 미션 취소 (오늘 날짜 기준)
     */
    @Transactional
    public MissionExecutionResponse skipExecutionToday(Long missionId, String userId) {
        return skipExecution(missionId, userId, LocalDate.now());
    }

    /**
     * 사용자의 현재 진행 중인 미션 조회
     */
    public MissionExecutionResponse getInProgressExecution(String userId) {
        return executionRepository.findInProgressByUserId(userId)
            .map(MissionExecutionResponse::from)
            .orElse(null);
    }

    /**
     * 미션 수행 시작 (오늘 날짜 기준)
     */
    @Transactional
    public MissionExecutionResponse startExecutionToday(Long missionId, String userId) {
        return startExecution(missionId, userId, LocalDate.now());
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

    /**
     * 월별 캘린더 데이터 조회
     * 해당 월의 완료된 미션 실행 내역과 총 획득 경험치 반환
     */
    public MonthlyCalendarResponse getMonthlyCalendarData(String userId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // 완료된 미션 실행 내역 조회
        List<MissionExecution> completedExecutions = executionRepository
            .findCompletedByUserIdAndDateRange(userId, startDate, endDate);

        // 월별 총 획득 경험치 조회
        int totalExp = executionRepository.sumExpEarnedByUserIdAndDateRange(userId, startDate, endDate);

        // 날짜별 미션 그룹화
        Map<String, List<DailyMission>> dailyMissions = new HashMap<>();
        for (MissionExecution execution : completedExecutions) {
            String dateKey = execution.getExecutionDate().toString();

            Integer durationMinutes = null;
            if (execution.getStartedAt() != null && execution.getCompletedAt() != null) {
                durationMinutes = (int) java.time.Duration.between(
                    execution.getStartedAt(), execution.getCompletedAt()).toMinutes();
            }

            DailyMission dailyMission = DailyMission.builder()
                .missionId(execution.getParticipant().getMission().getId())
                .missionTitle(execution.getParticipant().getMission().getTitle())
                .expEarned(execution.getExpEarned())
                .durationMinutes(durationMinutes)
                .build();

            dailyMissions.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(dailyMission);
        }

        // 완료된 미션이 있는 날짜 목록
        List<String> completedDates = new ArrayList<>(dailyMissions.keySet());
        completedDates.sort(String::compareTo);

        log.info("월별 캘린더 데이터 조회: userId={}, year={}, month={}, totalExp={}, completedDays={}",
            userId, year, month, totalExp, completedDates.size());

        return MonthlyCalendarResponse.builder()
            .year(year)
            .month(month)
            .totalExp(totalExp)
            .dailyMissions(dailyMissions)
            .completedDates(completedDates)
            .build();
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
                "미션 전체 완료 보너스: " + mission.getTitle(),
                mission.getCategoryName()
            );

            // 길드 미션인 경우 길드 보너스 경험치 지급
            if (mission.isGuildMission() && mission.getGuildId() != null) {
                int guildBonusExp = mission.getGuildBonusExpOnFullCompletion() != null
                    ? mission.getGuildBonusExpOnFullCompletion() : 20;
                try {
                    guildExperienceService.addExperience(
                        Long.parseLong(mission.getGuildId()),
                        guildBonusExp,
                        GuildExpSourceType.GUILD_MISSION_FULL_COMPLETION,
                        mission.getId(),
                        participant.getUserId(),
                        "길드 미션 전체 완료 보너스: " + mission.getTitle()
                    );
                    log.info("길드 전체 완료 보너스 지급: guildId={}, userId={}, exp={}",
                        mission.getGuildId(), participant.getUserId(), guildBonusExp);
                } catch (Exception e) {
                    log.warn("길드 보너스 경험치 지급 실패: guildId={}, error={}", mission.getGuildId(), e.getMessage());
                }
            }

            participant.complete();

            // 미션 전체 완료 업적 체크
            try {
                userStatsService.recordMissionFullCompletion(participant.getUserId());
                var userStats = userStatsService.getOrCreateUserStats(participant.getUserId());
                achievementService.checkMissionFullCompletionAchievements(
                    participant.getUserId(), userStats.getTotalMissionFullCompletions());
            } catch (Exception e) {
                log.warn("미션 전체 완료 업적 업데이트 실패: userId={}, error={}", participant.getUserId(), e.getMessage());
            }

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
