package io.pinkspider.leveluptogethermvp.feedservice.application;

import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.event.FeedCommentEvent;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.CreateFeedRequest;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedCommentRequest;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedCommentResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedLikeResponse;
import io.pinkspider.leveluptogethermvp.feedservice.domain.dto.UserTitleInfo;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedComment;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedLike;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedCommentRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedLikeRepository;
import io.pinkspider.leveluptogethermvp.userservice.core.application.UserExistsCacheService;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "feedTransactionManager")
public class FeedCommandService {

    private final ActivityFeedRepository activityFeedRepository;
    private final FeedLikeRepository feedLikeRepository;
    private final FeedCommentRepository feedCommentRepository;
    private final UserExistsCacheService userExistsCacheService;
    private final UserProfileCacheService userProfileCacheService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserTitleInfoHelper userTitleInfoHelper;

    /**
     * 시스템에서 자동 생성되는 활동 피드
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public ActivityFeed createActivityFeed(String userId, String userNickname, String userProfileImageUrl,
                                           Integer userLevel, String userTitle, TitleRarity userTitleRarity,
                                           String userTitleColorCode,
                                           ActivityType activityType, String title, String description,
                                           String referenceType, Long referenceId, String referenceName,
                                           FeedVisibility visibility, Long guildId,
                                           String imageUrl, String iconUrl) {
        ActivityFeed feed = ActivityFeed.builder()
            .userId(userId)
            .userNickname(userNickname)
            .userProfileImageUrl(userProfileImageUrl)
            .userLevel(userLevel != null ? userLevel : 1)
            .userTitle(userTitle)
            .userTitleRarity(userTitleRarity)
            .userTitleColorCode(userTitleColorCode)
            .activityType(activityType)
            .title(title)
            .description(description)
            .referenceType(referenceType)
            .referenceId(referenceId)
            .referenceName(referenceName)
            .visibility(visibility)
            .guildId(guildId)
            .imageUrl(imageUrl)
            .iconUrl(iconUrl)
            .likeCount(0)
            .commentCount(0)
            .build();

        ActivityFeed saved = activityFeedRepository.save(feed);
        log.info("Activity feed created: userId={}, type={}, feedId={}", userId, activityType, saved.getId());
        return saved;
    }

    /**
     * 사용자가 직접 생성하는 피드
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public ActivityFeedResponse createFeed(String userId, CreateFeedRequest request) {
        // 사용자 존재 확인
        if (!userExistsCacheService.existsById(userId)) {
            throw new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "사용자를 찾을 수 없습니다");
        }

        // 사용자 프로필 조회 (캐시)
        UserProfileCache userProfile = userProfileCacheService.getUserProfile(userId);

        // 사용자 장착 칭호 정보 조회
        UserTitleInfo titleInfo = userTitleInfoHelper.getUserEquippedTitleInfo(userId);
        String userTitle = titleInfo.titleName();
        TitleRarity userTitleRarity = titleInfo.titleRarity();
        String userTitleColorCode = titleInfo.colorCode();

        ActivityFeed feed = ActivityFeed.builder()
            .userId(userId)
            .userNickname(userProfile.nickname())
            .userProfileImageUrl(userProfile.picture())
            .userTitle(userTitle)
            .userTitleRarity(userTitleRarity)
            .userTitleColorCode(userTitleColorCode)
            .activityType(request.getActivityType())
            .title(request.getTitle())
            .description(request.getDescription())
            .referenceType(request.getReferenceType())
            .referenceId(request.getReferenceId())
            .referenceName(request.getReferenceName())
            .visibility(request.getVisibility() != null ? request.getVisibility() : FeedVisibility.PUBLIC)
            .guildId(request.getGuildId())
            .imageUrl(request.getImageUrl())
            .iconUrl(request.getIconUrl())
            .likeCount(0)
            .commentCount(0)
            .build();

        ActivityFeed saved = activityFeedRepository.save(feed);
        return ActivityFeedResponse.from(saved);
    }

    /**
     * 좋아요 토글
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public FeedLikeResponse toggleLike(Long feedId, String userId) {
        ActivityFeed feed = activityFeedRepository.findById(feedId)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "피드를 찾을 수 없습니다"));

        // 작성자는 자신의 피드에 좋아요를 할 수 없음
        if (feed.getUserId().equals(userId)) {
            throw new CustomException(ApiStatus.INVALID_INPUT.getResultCode(), "자신의 피드에는 좋아요를 할 수 없습니다");
        }

        boolean isLiked = feedLikeRepository.findByFeedIdAndUserId(feedId, userId)
            .map(like -> {
                // 이미 좋아요 상태 -> 취소
                feedLikeRepository.delete(like);
                feed.decrementLikeCount();
                activityFeedRepository.save(feed);
                log.info("Feed unliked: feedId={}, userId={}", feedId, userId);
                return false;
            })
            .orElseGet(() -> {
                // 좋아요 추가
                FeedLike like = FeedLike.builder()
                    .feed(feed)
                    .userId(userId)
                    .build();
                feedLikeRepository.save(like);
                feed.incrementLikeCount();
                activityFeedRepository.save(feed);
                log.info("Feed liked: feedId={}, userId={}", feedId, userId);
                return true;
            });

        return new FeedLikeResponse(isLiked, feed.getLikeCount());
    }

    /**
     * 댓글 작성
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public FeedCommentResponse addComment(Long feedId, String userId, FeedCommentRequest request) {
        ActivityFeed feed = activityFeedRepository.findById(feedId)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "피드를 찾을 수 없습니다"));

        // 사용자 프로필 정보 조회 (캐시)
        UserProfileCache userProfile = userProfileCacheService.getUserProfile(userId);

        FeedComment comment = FeedComment.builder()
            .feed(feed)
            .userId(userId)
            .userNickname(userProfile.nickname())
            .userProfileImageUrl(userProfile.picture())
            .userLevel(userProfile.level())
            .content(request.getContent())
            .isDeleted(false)
            .build();

        FeedComment saved = feedCommentRepository.save(comment);
        feed.incrementCommentCount();
        activityFeedRepository.save(feed);

        log.info("Comment added: feedId={}, commentId={}, userId={}", feedId, saved.getId(), userId);

        // 피드 댓글 알림 이벤트 발행 (자신의 글에 자신이 댓글 단 경우 제외)
        if (!userId.equals(feed.getUserId())) {
            eventPublisher.publishEvent(new FeedCommentEvent(
                userId,
                feed.getUserId(),
                userProfile.nickname(),
                feedId
            ));
        }

        return FeedCommentResponse.from(saved, null, userId);
    }

    /**
     * 댓글 삭제
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public void deleteComment(Long feedId, Long commentId, String userId) {
        FeedComment comment = feedCommentRepository.findById(commentId)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "댓글을 찾을 수 없습니다"));

        if (!comment.getFeed().getId().equals(feedId)) {
            throw new CustomException(ApiStatus.INVALID_INPUT.getResultCode(), "해당 피드의 댓글이 아닙니다");
        }

        if (!comment.getUserId().equals(userId)) {
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "본인의 댓글만 삭제할 수 있습니다");
        }

        comment.delete();
        feedCommentRepository.save(comment);

        ActivityFeed feed = comment.getFeed();
        feed.decrementCommentCount();
        activityFeedRepository.save(feed);

        log.info("Comment deleted: feedId={}, commentId={}, userId={}", feedId, commentId, userId);
    }

    /**
     * 피드 삭제
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public void deleteFeed(Long feedId, String userId) {
        ActivityFeed feed = activityFeedRepository.findById(feedId)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "피드를 찾을 수 없습니다"));

        if (!feed.getUserId().equals(userId)) {
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "본인의 피드만 삭제할 수 있습니다");
        }

        activityFeedRepository.delete(feed);
        log.info("Feed deleted: feedId={}, userId={}", feedId, userId);
    }

    // ========== 사용자 공유 피드 생성 ==========

    /**
     * 사용자가 미션 완료 시 피드에 공유하는 경우 호출
     * - 미션 실행 정보(note, imageUrl, duration, expEarned) 포함
     * - 공개 피드로 생성
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public ActivityFeed createMissionSharedFeed(String userId, String userNickname, String userProfileImageUrl,
                                                Integer userLevel, String userTitle, TitleRarity userTitleRarity,
                                                String userTitleColorCode,
                                                Long executionId, Long missionId, String missionTitle,
                                                String missionDescription, Long categoryId,
                                                String note, String imageUrl,
                                                Integer durationMinutes, Integer expEarned) {
        String title = missionTitle;

        ActivityFeed feed = ActivityFeed.builder()
            .userId(userId)
            .userNickname(userNickname)
            .userProfileImageUrl(userProfileImageUrl)
            .userLevel(userLevel != null ? userLevel : 1)
            .userTitle(userTitle)
            .userTitleRarity(userTitleRarity)
            .userTitleColorCode(userTitleColorCode)
            .activityType(ActivityType.MISSION_SHARED)
            .title(title)
            .description(note)
            .referenceType("MISSION_EXECUTION")
            .referenceId(executionId)
            .referenceName(missionTitle)
            .visibility(FeedVisibility.PUBLIC)
            .categoryId(categoryId)
            .imageUrl(imageUrl)
            .missionId(missionId)
            .executionId(executionId)
            .durationMinutes(durationMinutes)
            .expEarned(expEarned)
            .likeCount(0)
            .commentCount(0)
            .build();

        ActivityFeed saved = activityFeedRepository.save(feed);
        log.info("Mission shared feed created: userId={}, missionId={}, executionId={}, feedId={}",
            userId, missionId, executionId, saved.getId());
        return saved;
    }

    /**
     * 피드 삭제 (ID로 직접 삭제 - 보상 처리용)
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public void deleteFeedById(Long feedId) {
        activityFeedRepository.deleteById(feedId);
        log.info("Feed deleted by id: feedId={}", feedId);
    }

    /**
     * referenceId와 referenceType으로 피드 삭제 (미션 삭제 시 관련 피드 삭제용)
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public int deleteFeedsByReferenceId(Long referenceId, String referenceType) {
        int deletedCount = activityFeedRepository.deleteByReferenceIdAndReferenceType(referenceId, referenceType);
        log.info("Feeds deleted by referenceId: referenceId={}, referenceType={}, deletedCount={}",
            referenceId, referenceType, deletedCount);
        return deletedCount;
    }

    /**
     * missionId로 피드 삭제 (미션 삭제 시 관련 피드 삭제용)
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public int deleteFeedsByMissionId(Long missionId) {
        int deletedCount = activityFeedRepository.deleteByMissionId(missionId);
        log.info("Feeds deleted by missionId: missionId={}, deletedCount={}", missionId, deletedCount);
        return deletedCount;
    }

    /**
     * 피드 이미지 URL 업데이트 (미션 실행 이미지 업로드/삭제 시 연동)
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public void updateFeedImageUrl(Long feedId, String imageUrl) {
        activityFeedRepository.findById(feedId).ifPresent(feed -> {
            feed.setImageUrl(imageUrl);
            activityFeedRepository.save(feed);
            log.info("Feed image updated: feedId={}, imageUrl={}", feedId, imageUrl);
        });
    }

    /**
     * executionId로 피드 삭제 (feedId 역참조 제거 후 unshare용)
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public void deleteFeedByExecutionId(Long executionId) {
        activityFeedRepository.findByExecutionId(executionId).ifPresent(feed -> {
            activityFeedRepository.delete(feed);
            log.info("Feed deleted by executionId: executionId={}, feedId={}", executionId, feed.getId());
        });
    }

    /**
     * executionId로 피드 이미지 URL 업데이트 (feedId 역참조 제거 후 이미지 동기화용)
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public void updateFeedImageUrlByExecutionId(Long executionId, String imageUrl) {
        activityFeedRepository.findByExecutionId(executionId).ifPresent(feed -> {
            feed.setImageUrl(imageUrl);
            activityFeedRepository.save(feed);
            log.info("Feed image updated by executionId: executionId={}, imageUrl={}", executionId, imageUrl);
        });
    }

    /**
     * 사용자의 모든 피드의 칭호 정보 업데이트 (칭호 장착/해제 시 호출)
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public int updateFeedTitles(String userId, String titleName, TitleRarity titleRarity, String titleColorCode) {
        return activityFeedRepository.updateUserTitleByUserId(userId, titleName, titleRarity, titleColorCode);
    }
}
