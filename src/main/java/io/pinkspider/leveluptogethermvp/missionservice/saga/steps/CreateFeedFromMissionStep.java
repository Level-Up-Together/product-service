package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import io.pinkspider.global.saga.SagaStep;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.userservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import io.pinkspider.leveluptogethermvp.userservice.feed.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.application.UserService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Step: 피드 생성
 *
 * 사용자가 shareToFeed=true를 선택한 경우 공개 피드에 미션 완료 기록을 게시
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateFeedFromMissionStep implements SagaStep<MissionCompletionContext> {

    private final ActivityFeedService activityFeedService;
    private final UserService userService;
    private final UserExperienceService userExperienceService;
    private final TitleService titleService;

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
            // 사용자 정보 조회
            Users user = userService.findByUserId(userId);
            log.info("사용자 정보 조회 완료: userId={}, nickname={}", userId, user.getNickname());

            // 사용자 레벨 조회
            Integer userLevel = userExperienceService.getOrCreateUserExperience(userId).getCurrentLevel();
            log.info("사용자 레벨 조회 완료: userId={}, level={}", userId, userLevel);

            // 사용자 칭호 조회
            String userTitle = titleService.getCombinedEquippedTitleName(userId);
            log.info("사용자 칭호 조회 완료: userId={}, title={}", userId, userTitle);

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
                user.getNickname(),
                user.getPicture(),
                userLevel,
                userTitle,
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

            // 생성된 피드 ID 저장
            context.setCreatedFeedId(feed.getId());

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
    public SagaStepResult compensate(MissionCompletionContext context) {
        Long feedId = context.getCreatedFeedId();

        if (feedId == null) {
            log.debug("No feed to compensate");
            return SagaStepResult.success();
        }

        try {
            activityFeedService.deleteFeedById(feedId);
            log.info("Compensated feed deletion: feedId={}", feedId);
            return SagaStepResult.success("피드 삭제 완료");
        } catch (Exception e) {
            log.warn("Failed to compensate feed: feedId={}, error={}", feedId, e.getMessage());
            return SagaStepResult.failure("피드 보상 실패", e);
        }
    }
}
