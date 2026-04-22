package io.pinkspider.leveluptogethermvp.feedservice.application;

import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.event.FeedCommentEvent;
import io.pinkspider.global.event.FeedLikedEvent;
import io.pinkspider.global.event.FeedUnlikedEvent;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.CreateFeedRequest;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedCommentRequest;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedCommentResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedLikeResponse;
import io.pinkspider.global.facade.dto.DetailedTitleInfoDto;
import io.pinkspider.global.facade.dto.TitleInfoDto;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedComment;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedLike;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedCommentRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedLikeRepository;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
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
    private final UserQueryFacade userQueryFacadeService;
    private final ApplicationEventPublisher eventPublisher;
    private final GamificationQueryFacade gamificationQueryFacadeService;

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
        // 좌/우 칭호 상세 정보 조회
        DetailedTitleInfoDto detailedTitle = getDetailedTitleInfoSafe(userId);

        ActivityFeed feed = ActivityFeed.builder()
            .userId(userId)
            .userNickname(userNickname)
            .userProfileImageUrl(userProfileImageUrl)
            .userLevel(userLevel != null ? userLevel : 1)
            .userTitle(userTitle)
            .userTitleRarity(userTitleRarity)
            .userTitleColorCode(userTitleColorCode)
            .userLeftTitle(detailedTitle.leftTitle())
            .userLeftTitleRarity(detailedTitle.leftRarity())
            .userRightTitle(detailedTitle.rightTitle())
            .userRightTitleRarity(detailedTitle.rightRarity())
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
        if (!userQueryFacadeService.userExistsById(userId)) {
            throw new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "error.user.not_found");
        }

        // 사용자 프로필 조회 (캐시)
        UserProfileInfo userProfile = userQueryFacadeService.getUserProfile(userId);

        // 사용자 장착 칭호 정보 조회
        TitleInfoDto titleInfo = gamificationQueryFacadeService.getCombinedEquippedTitleInfo(userId);
        DetailedTitleInfoDto detailedTitle = getDetailedTitleInfoSafe(userId);

        ActivityFeed feed = ActivityFeed.builder()
            .userId(userId)
            .userNickname(userProfile.nickname())
            .userProfileImageUrl(userProfile.picture())
            .userTitle(titleInfo.name())
            .userTitleRarity(titleInfo.rarity())
            .userTitleColorCode(titleInfo.colorCode())
            .userLeftTitle(detailedTitle.leftTitle())
            .userLeftTitleRarity(detailedTitle.leftRarity())
            .userRightTitle(detailedTitle.rightTitle())
            .userRightTitleRarity(detailedTitle.rightRarity())
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
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "error.feed.not_found"));

        // 작성자는 자신의 피드에 좋아요를 할 수 없음
        if (feed.getUserId().equals(userId)) {
            throw new CustomException(ApiStatus.INVALID_INPUT.getResultCode(), "error.feed.self_like");
        }

        boolean isLiked = feedLikeRepository.findByFeedIdAndUserId(feedId, userId)
            .map(like -> {
                // 이미 좋아요 상태 -> 취소
                feedLikeRepository.delete(like);
                feed.decrementLikeCount();
                activityFeedRepository.save(feed);
                eventPublisher.publishEvent(new FeedUnlikedEvent(userId, feed.getUserId(), feedId));
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
                eventPublisher.publishEvent(new FeedLikedEvent(userId, feed.getUserId(), feedId));
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
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "error.feed.not_found"));

        // 사용자 프로필 정보 조회 (캐시)
        UserProfileInfo userProfile = userQueryFacadeService.getUserProfile(userId);

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
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "error.feed.comment.not_found"));

        if (!comment.getFeed().getId().equals(feedId)) {
            throw new CustomException(ApiStatus.INVALID_INPUT.getResultCode(), "error.feed.comment.wrong_feed");
        }

        if (!comment.getUserId().equals(userId)) {
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "error.feed.comment.not_owner");
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
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "error.feed.not_found"));

        if (!feed.getUserId().equals(userId)) {
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "error.feed.not_owner");
        }

        activityFeedRepository.delete(feed);
        log.info("Feed deleted: feedId={}, userId={}", feedId, userId);
    }

    /**
     * 피드 공개범위 변경
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public ActivityFeedResponse updateFeedVisibility(Long feedId, String userId, FeedVisibility visibility) {
        ActivityFeed feed = activityFeedRepository.findById(feedId)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "error.feed.not_found"));

        if (!feed.getUserId().equals(userId)) {
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "error.feed.not_owner");
        }

        feed.setVisibility(visibility);
        activityFeedRepository.save(feed);
        log.info("Feed visibility updated: feedId={}, userId={}, visibility={}", feedId, userId, visibility);

        return ActivityFeedResponse.from(feed, false, true, null);
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
        return createMissionSharedFeed(userId, userNickname, userProfileImageUrl, userLevel, userTitle,
            userTitleRarity, userTitleColorCode, executionId, missionId, missionTitle, missionDescription,
            categoryId, note, imageUrl, durationMinutes, expEarned, FeedVisibility.PUBLIC);
    }

    @Transactional(transactionManager = "feedTransactionManager")
    public ActivityFeed createMissionSharedFeed(String userId, String userNickname, String userProfileImageUrl,
                                                Integer userLevel, String userTitle, TitleRarity userTitleRarity,
                                                String userTitleColorCode,
                                                Long executionId, Long missionId, String missionTitle,
                                                String missionDescription, Long categoryId,
                                                String note, String imageUrl,
                                                Integer durationMinutes, Integer expEarned,
                                                FeedVisibility visibility) {
        String title = missionTitle;

        // 좌/우 칭호 상세 정보 조회
        DetailedTitleInfoDto detailedTitle = getDetailedTitleInfoSafe(userId);

        ActivityFeed feed = ActivityFeed.builder()
            .userId(userId)
            .userNickname(userNickname)
            .userProfileImageUrl(userProfileImageUrl)
            .userLevel(userLevel != null ? userLevel : 1)
            .userTitle(userTitle)
            .userTitleRarity(userTitleRarity)
            .userTitleColorCode(userTitleColorCode)
            .userLeftTitle(detailedTitle.leftTitle())
            .userLeftTitleRarity(detailedTitle.leftRarity())
            .userRightTitle(detailedTitle.rightTitle())
            .userRightTitleRarity(detailedTitle.rightRarity())
            .activityType(ActivityType.MISSION_SHARED)
            .title(title)
            .description(note)
            .referenceType("MISSION_EXECUTION")
            .referenceId(executionId)
            .referenceName(missionTitle)
            .visibility(visibility)
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
        log.info("Mission shared feed created: userId={}, missionId={}, executionId={}, feedId={}, visibility={}",
            userId, missionId, executionId, saved.getId(), visibility);
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
        activityFeedRepository.findFirstByExecutionIdOrderByCreatedAtDesc(executionId).ifPresent(feed -> {
            activityFeedRepository.delete(feed);
            log.info("Feed deleted by executionId: executionId={}, feedId={}", executionId, feed.getId());
        });
    }

    /**
     * executionId로 피드 이미지 URL 업데이트 (feedId 역참조 제거 후 이미지 동기화용)
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public void updateFeedImageUrlByExecutionId(Long executionId, String imageUrl) {
        activityFeedRepository.findFirstByExecutionIdOrderByCreatedAtDesc(executionId).ifPresent(feed -> {
            feed.setImageUrl(imageUrl);
            activityFeedRepository.save(feed);
            log.info("Feed image updated by executionId: executionId={}, imageUrl={}", executionId, imageUrl);
        });
    }

    /**
     * executionId로 피드 description(노트) 업데이트
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public void updateFeedDescriptionByExecutionId(Long executionId, String description) {
        activityFeedRepository.findFirstByExecutionIdOrderByCreatedAtDesc(executionId).ifPresent(feed -> {
            feed.setDescription(description);
            activityFeedRepository.save(feed);
            log.info("Feed description updated by executionId: executionId={}, descLength={}", executionId,
                description != null ? description.length() : 0);
        });
    }

    /**
     * executionId로 피드 visibility/description/imageUrl 업데이트
     * Saga가 생성한 기존 피드를 record 페이지에서 갱신할 때 사용
     *
     * @return 업데이트된 피드, 없으면 null
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public ActivityFeed updateFeedContentByExecutionId(Long executionId, String description, String imageUrl,
                                                        FeedVisibility visibility) {
        return activityFeedRepository.findFirstByExecutionIdOrderByCreatedAtDesc(executionId).map(feed -> {
            feed.setDescription(description);
            feed.setImageUrl(imageUrl);
            feed.setVisibility(visibility);
            activityFeedRepository.save(feed);
            log.info("Feed content updated by executionId: executionId={}, visibility={}", executionId, visibility);
            return feed;
        }).orElse(null);
    }

    /**
     * 사용자의 모든 피드의 칭호 정보 업데이트 (칭호 장착/해제 시 호출)
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public int updateFeedTitles(String userId, String titleName, TitleRarity titleRarity, String titleColorCode) {
        // 좌/우 칭호 상세 정보 조회
        DetailedTitleInfoDto detailedTitle = getDetailedTitleInfoSafe(userId);
        return activityFeedRepository.updateUserTitleByUserId(userId, titleName, titleRarity, titleColorCode,
            detailedTitle.leftTitle(), detailedTitle.leftRarity(),
            detailedTitle.rightTitle(), detailedTitle.rightRarity());
    }

    /**
     * 좌/우 칭호 상세 정보를 안전하게 조회 (실패 시 빈 정보 반환)
     */
    private DetailedTitleInfoDto getDetailedTitleInfoSafe(String userId) {
        try {
            return gamificationQueryFacadeService.getDetailedEquippedTitleInfo(userId);
        } catch (Exception e) {
            log.warn("좌/우 칭호 상세 정보 조회 실패: userId={}, error={}", userId, e.getMessage());
            return new DetailedTitleInfoDto(null, null, null, null, null, null);
        }
    }

    /**
     * Admin에 의한 피드 삭제 (내부 API)
     */
    @Transactional(transactionManager = "feedTransactionManager")
    public void deleteFeedByAdmin(Long id, String reason, String adminInfo) {
        ActivityFeed feed = activityFeedRepository.findById(id)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "error.feed.not_found"));

        log.info("Admin 피드 삭제: feedId={}, adminInfo={}, reason={}", id, adminInfo, reason);
        activityFeedRepository.delete(feed);
    }
}
