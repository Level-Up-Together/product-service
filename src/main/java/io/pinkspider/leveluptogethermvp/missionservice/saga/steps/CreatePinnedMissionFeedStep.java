package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.PinnedMissionCompletionContext;
import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 5: 고정 미션 피드 생성 (사용자 선택시)
 */
@Slf4j
@Component
public class CreatePinnedMissionFeedStep implements SagaStep<PinnedMissionCompletionContext> {

    private final ActivityFeedService activityFeedService;
    private final UserProfileCacheService userProfileCacheService;
    private final DailyMissionInstanceRepository instanceRepository;
    private final CreatePinnedMissionFeedStep self;

    public CreatePinnedMissionFeedStep(
            ActivityFeedService activityFeedService,
            UserProfileCacheService userProfileCacheService,
            DailyMissionInstanceRepository instanceRepository,
            @Lazy CreatePinnedMissionFeedStep self) {
        this.activityFeedService = activityFeedService;
        this.userProfileCacheService = userProfileCacheService;
        this.instanceRepository = instanceRepository;
        this.self = self;
    }

    @Override
    public String getName() {
        return "CreatePinnedMissionFeed";
    }

    @Override
    public boolean isMandatory() {
        return false; // 실패해도 전체 Saga 실패 아님
    }

    @Override
    public SagaStepResult execute(PinnedMissionCompletionContext context) {
        // 피드 공유 옵션이 아니면 스킵
        if (!context.isShareToFeed()) {
            log.debug("Feed sharing not requested, skipping");
            return SagaStepResult.success("피드 공유 미요청");
        }

        String userId = context.getUserId();
        DailyMissionInstance instance = context.getInstance();
        Mission mission = context.getMission();

        log.debug("Creating feed for pinned mission: userId={}, instanceId={}", userId, instance.getId());

        try {
            // 사용자 프로필 캐시 조회
            UserProfileCache profile = userProfileCacheService.getUserProfile(userId);

            // 수행 시간 계산
            Integer durationMinutes = instance.getDurationMinutes();

            // 피드 생성
            ActivityFeed feed = activityFeedService.createMissionSharedFeed(
                userId,
                profile.nickname(),
                profile.picture(),
                profile.level(),
                profile.titleName(),
                profile.titleRarity(),
                profile.titleColorCode(),
                null,  // executionId - 고정 미션은 null
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

            // 인스턴스에 피드 정보 업데이트 (별도 트랜잭션)
            self.updateInstanceFeedInfo(instance.getId(), feed.getId());

            log.info("Feed created for pinned mission: feedId={}, instanceId={}", feed.getId(), instance.getId());
            return SagaStepResult.success("피드 생성 완료", feed.getId());

        } catch (Exception e) {
            log.warn("Failed to create feed for pinned mission: {}", e.getMessage());
            return SagaStepResult.failure("피드 생성 실패", e);
        }
    }

    @Transactional(transactionManager = "missionTransactionManager")
    public void updateInstanceFeedInfo(Long instanceId, Long feedId) {
        instanceRepository.findById(instanceId).ifPresent(instance -> {
            instance.setFeedId(feedId);
            instance.setIsSharedToFeed(true);
            instanceRepository.save(instance);
            log.info("Instance feedId updated: instanceId={}, feedId={}", instanceId, feedId);
        });
    }

    @Override
    @Transactional(transactionManager = "missionTransactionManager")
    public SagaStepResult compensate(PinnedMissionCompletionContext context) {
        Long feedId = context.getCreatedFeedId();
        if (feedId == null) {
            return SagaStepResult.success("삭제할 피드 없음");
        }

        log.debug("Compensating pinned mission feed: feedId={}", feedId);

        try {
            // 인스턴스의 feedId도 초기화
            DailyMissionInstance instance = context.getInstance();
            if (instance != null && instance.getFeedId() != null) {
                instance.setFeedId(null);
                instance.setIsSharedToFeed(false);
                instanceRepository.save(instance);
            }

            // 피드 삭제
            activityFeedService.deleteFeedById(feedId);
            log.info("Pinned mission feed deleted: feedId={}", feedId);
            return SagaStepResult.success("피드 삭제 완료");
        } catch (Exception e) {
            log.warn("Failed to delete pinned mission feed: feedId={}, error={}", feedId, e.getMessage());
            return SagaStepResult.failure("피드 삭제 실패", e);
        }
    }
}
