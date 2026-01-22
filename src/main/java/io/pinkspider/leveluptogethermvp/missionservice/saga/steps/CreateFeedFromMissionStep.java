package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step: 피드 생성
 *
 * 사용자가 shareToFeed=true를 선택한 경우 공개 피드에 미션 완료 기록을 게시
 */
@Slf4j
@Component
public class CreateFeedFromMissionStep implements SagaStep<MissionCompletionContext> {

    private final ActivityFeedService activityFeedService;
    private final UserProfileCacheService userProfileCacheService;
    private final MissionExecutionRepository executionRepository;
    private final CreateFeedFromMissionStep self;

    public CreateFeedFromMissionStep(
            ActivityFeedService activityFeedService,
            UserProfileCacheService userProfileCacheService,
            MissionExecutionRepository executionRepository,
            @Lazy CreateFeedFromMissionStep self) {
        this.activityFeedService = activityFeedService;
        this.userProfileCacheService = userProfileCacheService;
        this.executionRepository = executionRepository;
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
        log.info("CreateFeedFromMissionStep 시작: userId={}, shareToFeed={}",
            context.getUserId(), context.isShareToFeed());

        // shareToFeed가 false면 스킵
        if (!context.isShareToFeed()) {
            log.info("피드 공유 미요청으로 스킵: userId={}", context.getUserId());
            return SagaStepResult.success("피드 공유 미요청");
        }

        String userId = context.getUserId();
        MissionExecution execution = context.getExecution();
        Mission mission = context.getMission();

        log.debug("Creating mission shared feed: userId={}, executionId={}, missionId={}",
            userId, execution.getId(), mission.getId());

        try {
            // 사용자 프로필 캐시 조회 (nickname, picture, level, titleName, titleRarity)
            UserProfileCache profile = userProfileCacheService.getUserProfile(userId);
            log.info("사용자 프로필 조회 완료: userId={}, nickname={}, level={}, title={}",
                userId, profile.nickname(), profile.level(), profile.titleName());

            // 수행 시간 계산
            Integer durationMinutes = execution.calculateExpByDuration();
            log.info("수행 시간: {}분, 획득 EXP: {}", durationMinutes, execution.getExpEarned());

            // 카테고리 ID 추출
            Long categoryId = (mission.getCategory() != null) ? mission.getCategory().getId() : null;
            log.info("피드 생성 정보: missionTitle={}, note={}, imageUrl={}, categoryId={}",
                mission.getTitle(), execution.getNote(), execution.getImageUrl(), categoryId);

            // 피드 생성
            ActivityFeed feed = activityFeedService.createMissionSharedFeed(
                userId,
                profile.nickname(),
                profile.picture(),
                profile.level(),
                profile.titleName(),
                profile.titleRarity(),
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

            // 생성된 피드 ID 저장 (Saga 컨텍스트)
            context.setCreatedFeedId(feed.getId());

            // execution 엔티티에도 feedId 저장 (나중에 이미지 업로드 시 피드 동기화용)
            // 별도 트랜잭션으로 실행하여 missionTransactionManager 사용
            self.updateExecutionFeedId(execution.getId(), feed.getId());

            log.info("Mission shared feed created: userId={}, feedId={}, executionId={}",
                userId, feed.getId(), execution.getId());
            return SagaStepResult.success("피드 생성 완료: feedId=" + feed.getId());

        } catch (Exception e) {
            log.warn("Failed to create mission shared feed: userId={}, error={}",
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
            // execution의 feedId도 초기화
            MissionExecution execution = context.getExecution();
            if (execution != null && execution.getFeedId() != null) {
                execution.setFeedId(null);
                executionRepository.save(execution);
                log.info("Execution feedId cleared: executionId={}", execution.getId());
            }

            // 피드 삭제
            activityFeedService.deleteFeedById(feedId);
            log.info("Compensated feed deletion: feedId={}", feedId);
            return SagaStepResult.success("피드 삭제 완료");
        } catch (Exception e) {
            log.warn("Failed to compensate feed: feedId={}, error={}", feedId, e.getMessage());
            return SagaStepResult.failure("피드 보상 실패", e);
        }
    }

    /**
     * execution의 feedId를 업데이트 (별도 트랜잭션)
     * missionTransactionManager를 사용하여 mission_db에 저장
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public void updateExecutionFeedId(Long executionId, Long feedId) {
        MissionExecution execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        execution.setFeedId(feedId);
        executionRepository.save(execution);
        log.info("Execution feedId updated: executionId={}, feedId={}", executionId, feedId);
    }
}
