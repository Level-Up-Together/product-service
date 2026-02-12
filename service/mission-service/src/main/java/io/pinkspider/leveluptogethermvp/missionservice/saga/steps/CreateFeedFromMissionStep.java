package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.leveluptogethermvp.feedservice.application.FeedCommandService;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 피드 생성 (일반 미션 + 고정 미션 통합)
 *
 * 사용자가 shareToFeed=true를 선택한 경우 공개 피드에 미션 완료 기록을 게시
 */
@Slf4j
@Component
public class CreateFeedFromMissionStep implements SagaStep<MissionCompletionContext> {

    private final FeedCommandService feedCommandService;
    private final UserProfileCacheService userProfileCacheService;
    private final MissionExecutionRepository executionRepository;
    private final DailyMissionInstanceRepository instanceRepository;
    private final CreateFeedFromMissionStep self;

    public CreateFeedFromMissionStep(
            FeedCommandService feedCommandService,
            UserProfileCacheService userProfileCacheService,
            MissionExecutionRepository executionRepository,
            DailyMissionInstanceRepository instanceRepository,
            @Lazy CreateFeedFromMissionStep self) {
        this.feedCommandService = feedCommandService;
        this.userProfileCacheService = userProfileCacheService;
        this.executionRepository = executionRepository;
        this.instanceRepository = instanceRepository;
        this.self = self;
    }

    @Override
    public String getName() {
        return "CreateFeedFromMission";
    }

    @Override
    public boolean isMandatory() {
        // 피드 생성 실패는 미션 완료 자체를 실패시키지 않음
        return false;
    }

    @Override
    public SagaStepResult execute(MissionCompletionContext context) {
        // shareToFeed가 false면 스킵
        if (!context.isShareToFeed()) {
            log.debug("Feed sharing not requested, skipping: userId={}", context.getUserId());
            return SagaStepResult.success("피드 공유 미요청");
        }

        if (context.isPinned()) {
            return executePinned(context);
        } else {
            return executeRegular(context);
        }
    }

    private SagaStepResult executeRegular(MissionCompletionContext context) {
        String userId = context.getUserId();
        MissionExecution execution = context.getExecution();
        Mission mission = context.getMission();

        log.debug("Creating mission shared feed: userId={}, executionId={}, missionId={}",
            userId, execution.getId(), mission.getId());

        try {
            UserProfileCache profile = userProfileCacheService.getUserProfile(userId);

            Integer durationMinutes = execution.calculateExpByDuration();
            Long categoryId = mission.getCategoryId();

            ActivityFeed feed = feedCommandService.createMissionSharedFeed(
                userId,
                profile.nickname(),
                profile.picture(),
                profile.level(),
                profile.titleName(),
                profile.titleRarity(),
                profile.titleColorCode(),
                execution.getId(),
                mission.getId(),
                mission.getTitle(),
                mission.getDescription(),
                categoryId,
                execution.getNote(),
                execution.getImageUrl(),
                durationMinutes,
                execution.getExpEarned()
            );

            context.setCreatedFeedId(feed.getId());

            // execution 엔티티의 공유 상태 업데이트 (별도 트랜잭션으로 missionTransactionManager 사용)
            self.updateExecutionSharedStatus(execution.getId(), true);

            log.info("Mission shared feed created: userId={}, feedId={}, executionId={}",
                userId, feed.getId(), execution.getId());
            return SagaStepResult.success("피드 생성 완료: feedId=" + feed.getId());

        } catch (Exception e) {
            log.warn("Failed to create mission shared feed: userId={}, error={}",
                userId, e.getMessage());
            return SagaStepResult.failure("피드 생성 실패", e);
        }
    }

    private SagaStepResult executePinned(MissionCompletionContext context) {
        String userId = context.getUserId();
        DailyMissionInstance instance = context.getInstance();
        Mission mission = context.getMission();

        log.debug("Creating feed for pinned mission: userId={}, instanceId={}", userId, instance.getId());

        try {
            UserProfileCache profile = userProfileCacheService.getUserProfile(userId);

            Integer durationMinutes = instance.getDurationMinutes();

            ActivityFeed feed = feedCommandService.createMissionSharedFeed(
                userId,
                profile.nickname(),
                profile.picture(),
                profile.level(),
                profile.titleName(),
                profile.titleRarity(),
                profile.titleColorCode(),
                instance.getId(),  // executionId - 고정 미션은 instanceId 사용
                mission.getId(),
                instance.getMissionTitle(),
                instance.getMissionDescription(),
                context.getCategoryId(),
                instance.getNote(),
                instance.getImageUrl(),
                durationMinutes,
                instance.getExpEarned()
            );

            context.setCreatedFeedId(feed.getId());

            // 인스턴스의 공유 상태 업데이트 (별도 트랜잭션)
            self.updateInstanceSharedStatus(instance.getId(), true);

            log.info("Pinned mission feed created: feedId={}, instanceId={}", feed.getId(), instance.getId());
            return SagaStepResult.success("피드 생성 완료: feedId=" + feed.getId());

        } catch (Exception e) {
            log.warn("Failed to create feed for pinned mission: userId={}, error={}",
                userId, e.getMessage());
            return SagaStepResult.failure("피드 생성 실패", e);
        }
    }

    @Override
    @Transactional(transactionManager = "missionTransactionManager")
    public SagaStepResult compensate(MissionCompletionContext context) {
        Long feedId = context.getCreatedFeedId();

        if (feedId == null) {
            log.debug("No feed to compensate");
            return SagaStepResult.success();
        }

        try {
            if (context.isPinned()) {
                // 인스턴스의 공유 상태 초기화
                DailyMissionInstance instance = context.getInstance();
                if (instance != null && Boolean.TRUE.equals(instance.getIsSharedToFeed())) {
                    instance.unshareFromFeed();
                    instanceRepository.save(instance);
                    log.info("Instance shared status cleared: instanceId={}", instance.getId());
                }
            } else {
                // execution의 공유 상태 초기화
                MissionExecution execution = context.getExecution();
                if (execution != null && Boolean.TRUE.equals(execution.getIsSharedToFeed())) {
                    execution.unshareFromFeed();
                    executionRepository.save(execution);
                    log.info("Execution shared status cleared: executionId={}", execution.getId());
                }
            }

            // 피드 삭제
            feedCommandService.deleteFeedById(feedId);
            log.info("Compensated feed deletion: feedId={}", feedId);
            return SagaStepResult.success("피드 삭제 완료");
        } catch (Exception e) {
            log.warn("Failed to compensate feed: feedId={}, error={}", feedId, e.getMessage());
            return SagaStepResult.failure("피드 보상 실패", e);
        }
    }

    /**
     * execution의 공유 상태를 업데이트 (별도 트랜잭션)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public void updateExecutionSharedStatus(Long executionId, boolean shared) {
        MissionExecution execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        if (shared) {
            execution.shareToFeed();
        } else {
            execution.unshareFromFeed();
        }
        executionRepository.save(execution);
        log.info("Execution shared status updated: executionId={}, shared={}", executionId, shared);
    }

    /**
     * instance의 공유 상태를 업데이트 (별도 트랜잭션)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public void updateInstanceSharedStatus(Long instanceId, boolean shared) {
        instanceRepository.findById(instanceId).ifPresent(instance -> {
            instance.setIsSharedToFeed(shared);
            instanceRepository.save(instance);
            log.info("Instance shared status updated: instanceId={}, shared={}", instanceId, shared);
        });
    }
}
