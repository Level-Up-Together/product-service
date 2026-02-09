package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildExperienceService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildExperienceHistory.GuildExpSourceType;
import io.pinkspider.leveluptogethermvp.missionservice.application.strategy.MissionExecutionStrategyResolver;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionSaga;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.UserStatsService;
import io.pinkspider.leveluptogethermvp.userservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ExpSourceType;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "missionTransactionManager", readOnly = true)
public class MissionExecutionService {

    private final MissionExecutionRepository executionRepository;
    private final MissionParticipantRepository participantRepository;
    private final UserExperienceService userExperienceService;
    private final GuildExperienceService guildExperienceService;
    private final UserStatsService userStatsService;
    private final AchievementService achievementService;
    private final MissionCompletionSaga missionCompletionSaga;
    private final ActivityFeedService activityFeedService;
    private final MissionExecutionStrategyResolver strategyResolver;

    /**
     * 미션 참여 시 실행 일정 생성
     *
     * - 고정 미션(isPinned=true): DailyMissionInstance를 사용하므로 여기서 생성하지 않음
     * - 일반 미션(isPinned=false): 오늘 하루치만 생성 (1회성 미션)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public void generateExecutionsForParticipant(MissionParticipant participant) {
        Mission mission = participant.getMission();
        LocalDate today = LocalDate.now();

        // 고정 미션(isPinned=true)은 DailyMissionInstance를 사용하므로 여기서 생성하지 않음
        if (Boolean.TRUE.equals(mission.getIsPinned())) {
            log.info("고정 미션은 DailyMissionInstance를 사용하므로 MissionExecution 생성 건너뜀: missionId={}",
                mission.getId());
            return;
        }

        // 일반 미션(isPinned=false 또는 null)은 오늘 하루치만 생성
        // 기존 실행 날짜 조회 (재참여 시 중복 방지)
        boolean alreadyExists = executionRepository.findByParticipantIdAndExecutionDate(
            participant.getId(), today).isPresent();

        if (alreadyExists) {
            log.info("미션 수행 일정 생성 건너뜀: participantId={}, 오늘 날짜 기존재", participant.getId());
            return;
        }

        MissionExecution execution = MissionExecution.builder()
            .participant(participant)
            .executionDate(today)
            .status(ExecutionStatus.PENDING)
            .build();

        executionRepository.save(execution);
        log.info("일반 미션 수행 일정 생성: participantId={}, missionId={}, date={}",
            participant.getId(), mission.getId(), today);
    }

    // ============ Strategy 패턴으로 위임하는 메서드들 ============

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse startExecution(Long missionId, String userId, LocalDate executionDate) {
        return strategyResolver.resolve(missionId, userId).startExecution(missionId, userId, executionDate);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse skipExecution(Long missionId, String userId, LocalDate executionDate) {
        return strategyResolver.resolve(missionId, userId).skipExecution(missionId, userId, executionDate);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse completeExecution(Long missionId, String userId, LocalDate executionDate, String note) {
        return completeExecution(missionId, userId, executionDate, note, false);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse completeExecution(Long missionId, String userId, LocalDate executionDate, String note, boolean shareToFeed) {
        return strategyResolver.resolve(missionId, userId).completeExecution(missionId, userId, executionDate, note, shareToFeed);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse uploadExecutionImage(Long missionId, String userId, LocalDate executionDate, MultipartFile image) {
        return strategyResolver.resolve(missionId, userId).uploadExecutionImage(missionId, userId, executionDate, image);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse deleteExecutionImage(Long missionId, String userId, LocalDate executionDate) {
        return strategyResolver.resolve(missionId, userId).deleteExecutionImage(missionId, userId, executionDate);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse shareExecutionToFeed(Long missionId, String userId, LocalDate executionDate) {
        return strategyResolver.resolve(missionId, userId).shareExecutionToFeed(missionId, userId, executionDate);
    }

    // ============ 오늘 날짜 기준 편의 메서드 ============

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse skipExecutionToday(Long missionId, String userId) {
        return skipExecution(missionId, userId, LocalDate.now());
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse startExecutionToday(Long missionId, String userId) {
        return startExecution(missionId, userId, LocalDate.now());
    }

    // ============ executionId 기반 메서드 (Saga, 일반 미션 전용) ============

    @Transactional(transactionManager = "missionTransactionManager")
    public int markMissedExecutions() {
        LocalDate today = LocalDate.now();
        int count = executionRepository.markMissedExecutions(today);
        log.info("미실행 처리된 수행 기록: {}개", count);
        return count;
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
        return completeExecution(executionId, userId, note, false);
    }

    /**
     * Saga 패턴을 사용한 미션 수행 완료 처리 (피드 공유 옵션 포함)
     *
     * @param executionId 수행 기록 ID
     * @param userId 사용자 ID
     * @param note 메모
     * @param shareToFeed 피드 공유 여부
     * @return 미션 수행 응답
     */
    public MissionExecutionResponse completeExecution(Long executionId, String userId, String note, boolean shareToFeed) {
        log.info("미션 수행 완료 요청 (Saga): executionId={}, userId={}, shareToFeed={}",
            executionId, userId, shareToFeed);

        // Saga 실행
        SagaResult<MissionCompletionContext> result = missionCompletionSaga.execute(executionId, userId, note, shareToFeed);

        if (result.isSuccess()) {
            return missionCompletionSaga.toResponse(result);
        } else {
            // Saga 실패 로깅 (디버깅용)
            log.warn("미션 완료 처리 실패 (sagaId={}, status={}): {}",
                result.getSagaId(), result.getStatus(), result.getMessage());

            if (result.isCompensated()) {
                log.info("미션 완료 실패 - 보상 트랜잭션 완료: sagaId={}", result.getSagaId());
            }

            // 사용자에게는 원본 에러 메시지만 전달
            throw new IllegalStateException(result.getMessage(), result.getException());
        }
    }

    /**
     * 레거시 방식의 미션 수행 완료 처리 (Saga 미적용)
     *
     * @deprecated Saga 패턴 적용된 completeExecution 사용 권장
     */
    @Deprecated
    @Transactional(transactionManager = "missionTransactionManager")
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

        Long categoryId = mission.getCategoryId();
        userExperienceService.addExperience(
            userId,
            expEarned,
            ExpSourceType.MISSION_EXECUTION,
            mission.getId(),
            "미션 수행 완료: " + mission.getTitle(),
            categoryId,
            mission.getCategoryName()
        );

        // 길드 미션인 경우 길드 경험치 지급
        if (mission.isGuildMission() && mission.getGuildId() != null) {
            int guildExpEarned = mission.getGuildExpPerCompletion() != null ? mission.getGuildExpPerCompletion() : 5;
            try {
                guildExperienceService.addExperience(
                    mission.getGuildIdAsLong(),
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
            // 동적 Strategy 패턴으로 USER_STATS 관련 업적 체크
            achievementService.checkAchievementsByDataSource(userId, "USER_STATS");
        } catch (Exception e) {
            log.warn("업적 업데이트 실패: userId={}, error={}", userId, e.getMessage());
        }

        log.info("미션 수행 완료: executionId={}, userId={}, exp={}", executionId, userId, expEarned);
        return MissionExecutionResponse.from(execution);
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse completeExecutionByDate(Long missionId, String userId, LocalDate date, String note) {
        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), date)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + date));

        return completeExecution(execution.getId(), userId, note);
    }

    /**
     * 완료된 미션 실행의 노트(기록) 업데이트
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse updateExecutionNote(Long missionId, String userId, LocalDate executionDate, String note) {
        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        if (execution.getStatus() != ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 미션만 기록을 추가할 수 있습니다.");
        }

        execution.setNote(note);
        executionRepository.save(execution);

        log.info("미션 기록 업데이트: missionId={}, userId={}, executionDate={}", missionId, userId, executionDate);
        return MissionExecutionResponse.from(execution);
    }

    /**
     * 피드 공유 취소
     * - 공유된 피드 삭제 및 feedId 초기화
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse unshareExecutionFromFeed(Long missionId, String userId, LocalDate executionDate) {
        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));

        MissionExecution execution = executionRepository.findByParticipantIdAndExecutionDate(participant.getId(), executionDate)
            .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 수행 기록을 찾을 수 없습니다: " + executionDate));

        if (!Boolean.TRUE.equals(execution.getIsSharedToFeed())) {
            throw new IllegalStateException("공유된 피드가 없습니다.");
        }

        try {
            // executionId로 피드 삭제
            activityFeedService.deleteFeedByExecutionId(execution.getId());

            // 공유 상태 초기화
            execution.unshareFromFeed();
            executionRepository.save(execution);

            log.info("미션 피드 공유 취소 완료: userId={}, missionId={}, executionDate={}, executionId={}",
                userId, missionId, executionDate, execution.getId());

        } catch (Exception e) {
            log.error("피드 공유 취소 실패: userId={}, missionId={}, error={}", userId, missionId, e.getMessage());
            throw new IllegalStateException("피드 공유 취소에 실패했습니다: " + e.getMessage(), e);
        }

        return MissionExecutionResponse.from(execution);
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

            Long bonusCategoryId = mission.getCategoryId();
            userExperienceService.addExperience(
                participant.getUserId(),
                bonusExp,
                ExpSourceType.MISSION_FULL_COMPLETION,
                mission.getId(),
                "미션 전체 완료 보너스: " + mission.getTitle(),
                bonusCategoryId,
                mission.getCategoryName()
            );

            // 길드 미션인 경우 길드 보너스 경험치 지급
            if (mission.isGuildMission() && mission.getGuildId() != null) {
                int guildBonusExp = mission.getGuildBonusExpOnFullCompletion() != null
                    ? mission.getGuildBonusExpOnFullCompletion() : 20;
                try {
                    guildExperienceService.addExperience(
                        mission.getGuildIdAsLong(),
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
                // 미션 기간 계산: totalExecutions가 실제 완주한 일수
                int durationDays = (int) totalExecutions;
                userStatsService.recordMissionFullCompletion(participant.getUserId(), durationDays);
                // 동적 Strategy 패턴으로 USER_STATS 관련 업적 체크
                achievementService.checkAchievementsByDataSource(participant.getUserId(), "USER_STATS");
            } catch (Exception e) {
                log.warn("미션 전체 완료 업적 업데이트 실패: userId={}, error={}", participant.getUserId(), e.getMessage());
            }

            log.info("미션 전체 완료 보너스 지급: userId={}, missionId={}, bonusExp={}",
                participant.getUserId(), mission.getId(), bonusExp);
        }
    }
}
