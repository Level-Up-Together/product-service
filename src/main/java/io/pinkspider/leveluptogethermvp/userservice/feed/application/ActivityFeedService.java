package io.pinkspider.leveluptogethermvp.userservice.feed.application;

import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.CreateFeedRequest;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.FeedCommentRequest;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.FeedCommentResponse;
import io.pinkspider.leveluptogethermvp.userservice.feed.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.userservice.feed.domain.entity.FeedComment;
import io.pinkspider.leveluptogethermvp.userservice.feed.domain.entity.FeedLike;
import io.pinkspider.leveluptogethermvp.userservice.feed.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.userservice.feed.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.userservice.feed.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.userservice.feed.infrastructure.FeedCommentRepository;
import io.pinkspider.leveluptogethermvp.userservice.feed.infrastructure.FeedLikeRepository;
import io.pinkspider.leveluptogethermvp.userservice.friend.infrastructure.FriendshipRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ActivityFeedService {

    private final ActivityFeedRepository activityFeedRepository;
    private final FeedLikeRepository feedLikeRepository;
    private final FeedCommentRepository feedCommentRepository;
    private final FriendshipRepository friendshipRepository;

    /**
     * 시스템에서 자동 생성되는 활동 피드
     */
    @Transactional
    public ActivityFeed createActivityFeed(String userId, String userNickname, String userProfileImageUrl,
                                           ActivityType activityType, String title, String description,
                                           String referenceType, Long referenceId, String referenceName,
                                           FeedVisibility visibility, Long guildId,
                                           String imageUrl, String iconUrl) {
        ActivityFeed feed = ActivityFeed.builder()
            .userId(userId)
            .userNickname(userNickname)
            .userProfileImageUrl(userProfileImageUrl)
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
    @Transactional
    public ActivityFeedResponse createFeed(String userId, String userNickname, String userProfileImageUrl,
                                           CreateFeedRequest request) {
        ActivityFeed feed = ActivityFeed.builder()
            .userId(userId)
            .userNickname(userNickname)
            .userProfileImageUrl(userProfileImageUrl)
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
     * 전체 공개 피드 조회
     */
    public Page<ActivityFeedResponse> getPublicFeeds(String currentUserId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ActivityFeed> feeds = activityFeedRepository.findPublicFeeds(pageable);

        Set<Long> likedFeedIds = getLikedFeedIds(currentUserId, feeds.getContent());
        return feeds.map(feed -> ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId())));
    }

    /**
     * 내 타임라인 피드 조회 (내 피드 + 친구 피드)
     */
    public Page<ActivityFeedResponse> getTimelineFeeds(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        List<String> friendIds = friendshipRepository.findFriendIds(userId);

        Page<ActivityFeed> feeds;
        if (friendIds.isEmpty()) {
            feeds = activityFeedRepository.findByUserId(userId, pageable);
        } else {
            feeds = activityFeedRepository.findTimelineFeeds(userId, friendIds, pageable);
        }

        Set<Long> likedFeedIds = getLikedFeedIds(userId, feeds.getContent());
        return feeds.map(feed -> ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId())));
    }

    /**
     * 특정 사용자의 피드 조회
     */
    public Page<ActivityFeedResponse> getUserFeeds(String targetUserId, String currentUserId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ActivityFeed> feeds = activityFeedRepository.findByUserId(targetUserId, pageable);

        Set<Long> likedFeedIds = getLikedFeedIds(currentUserId, feeds.getContent());

        // 친구 여부에 따라 visibility 필터링
        boolean isFriend = friendshipRepository.areFriends(currentUserId, targetUserId);
        boolean isSelf = currentUserId.equals(targetUserId);

        return feeds
            .map(feed -> {
                // 본인 피드이면 모두 표시
                if (isSelf) {
                    return ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId()));
                }
                // 공개 피드만 표시 (친구면 FRIENDS까지)
                if (feed.getVisibility() == FeedVisibility.PUBLIC ||
                    (isFriend && feed.getVisibility() == FeedVisibility.FRIENDS)) {
                    return ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId()));
                }
                return null;
            });
    }

    /**
     * 길드 피드 조회
     */
    public Page<ActivityFeedResponse> getGuildFeeds(Long guildId, String currentUserId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ActivityFeed> feeds = activityFeedRepository.findGuildFeeds(guildId, pageable);

        Set<Long> likedFeedIds = getLikedFeedIds(currentUserId, feeds.getContent());
        return feeds.map(feed -> ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId())));
    }

    /**
     * 카테고리별 피드 조회
     */
    public Page<ActivityFeedResponse> getFeedsByCategory(String category, String currentUserId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        List<ActivityType> types = ActivityType.getByCategory(category);

        if (types.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<ActivityFeed> feeds = activityFeedRepository.findByCategoryTypes(types, pageable);
        Set<Long> likedFeedIds = getLikedFeedIds(currentUserId, feeds.getContent());
        return feeds.map(feed -> ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId())));
    }

    /**
     * 피드 상세 조회
     */
    public ActivityFeedResponse getFeed(Long feedId, String currentUserId) {
        ActivityFeed feed = activityFeedRepository.findById(feedId)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "피드를 찾을 수 없습니다"));

        boolean likedByMe = currentUserId != null && feedLikeRepository.existsByFeedIdAndUserId(feedId, currentUserId);
        return ActivityFeedResponse.from(feed, likedByMe);
    }

    /**
     * 좋아요 토글
     */
    @Transactional
    public boolean toggleLike(Long feedId, String userId) {
        ActivityFeed feed = activityFeedRepository.findById(feedId)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "피드를 찾을 수 없습니다"));

        return feedLikeRepository.findByFeedIdAndUserId(feedId, userId)
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
    }

    /**
     * 댓글 작성
     */
    @Transactional
    public FeedCommentResponse addComment(Long feedId, String userId, String userNickname,
                                          FeedCommentRequest request) {
        ActivityFeed feed = activityFeedRepository.findById(feedId)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "피드를 찾을 수 없습니다"));

        FeedComment comment = FeedComment.builder()
            .feed(feed)
            .userId(userId)
            .userNickname(userNickname)
            .content(request.getContent())
            .isDeleted(false)
            .build();

        FeedComment saved = feedCommentRepository.save(comment);
        feed.incrementCommentCount();
        activityFeedRepository.save(feed);

        log.info("Comment added: feedId={}, commentId={}, userId={}", feedId, saved.getId(), userId);
        return FeedCommentResponse.from(saved);
    }

    /**
     * 댓글 목록 조회
     */
    public Page<FeedCommentResponse> getComments(Long feedId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<FeedComment> comments = feedCommentRepository.findByFeedId(feedId, pageable);
        return comments.map(FeedCommentResponse::from);
    }

    /**
     * 댓글 삭제
     */
    @Transactional
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
    @Transactional
    public void deleteFeed(Long feedId, String userId) {
        ActivityFeed feed = activityFeedRepository.findById(feedId)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "피드를 찾을 수 없습니다"));

        if (!feed.getUserId().equals(userId)) {
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "본인의 피드만 삭제할 수 있습니다");
        }

        activityFeedRepository.delete(feed);
        log.info("Feed deleted: feedId={}, userId={}", feedId, userId);
    }

    // ========== Helper Methods ==========

    private Set<Long> getLikedFeedIds(String userId, List<ActivityFeed> feeds) {
        if (userId == null || feeds.isEmpty()) {
            return new HashSet<>();
        }
        List<Long> feedIds = feeds.stream()
            .map(ActivityFeed::getId)
            .collect(Collectors.toList());
        return new HashSet<>(feedLikeRepository.findLikedFeedIds(userId, feedIds));
    }

    // ========== System Activity Feed 생성 헬퍼 메서드 ==========

    @Transactional
    public void notifyMissionJoined(String userId, String userNickname, String userProfileImageUrl,
                                    Long missionId, String missionTitle) {
        String title = String.format("%s 미션에 참여했습니다!", missionTitle);
        createActivityFeed(userId, userNickname, userProfileImageUrl,
            ActivityType.MISSION_JOINED, title, null,
            "MISSION", missionId, missionTitle,
            FeedVisibility.PUBLIC, null, null, null);
    }

    @Transactional
    public void notifyMissionCompleted(String userId, String userNickname, String userProfileImageUrl,
                                       Long missionId, String missionTitle, int completionRate) {
        String title = String.format("%s 미션 인터벌을 완료했습니다!", missionTitle);
        String description = String.format("달성률: %d%%", completionRate);
        createActivityFeed(userId, userNickname, userProfileImageUrl,
            ActivityType.MISSION_COMPLETED, title, description,
            "MISSION", missionId, missionTitle,
            FeedVisibility.PUBLIC, null, null, null);
    }

    @Transactional
    public void notifyMissionFullCompleted(String userId, String userNickname, String userProfileImageUrl,
                                           Long missionId, String missionTitle) {
        String title = String.format("%s 미션을 100%% 완료했습니다!", missionTitle);
        createActivityFeed(userId, userNickname, userProfileImageUrl,
            ActivityType.MISSION_FULL_COMPLETED, title, null,
            "MISSION", missionId, missionTitle,
            FeedVisibility.PUBLIC, null, null, null);
    }

    @Transactional
    public void notifyAchievementUnlocked(String userId, String userNickname, String userProfileImageUrl,
                                          Long achievementId, String achievementName, String achievementDescription) {
        String title = String.format("[%s] 업적을 달성했습니다!", achievementName);
        createActivityFeed(userId, userNickname, userProfileImageUrl,
            ActivityType.ACHIEVEMENT_UNLOCKED, title, achievementDescription,
            "ACHIEVEMENT", achievementId, achievementName,
            FeedVisibility.PUBLIC, null, null, null);
    }

    @Transactional
    public void notifyTitleAcquired(String userId, String userNickname, String userProfileImageUrl,
                                    Long titleId, String titleName) {
        String title = String.format("[%s] 칭호를 획득했습니다!", titleName);
        createActivityFeed(userId, userNickname, userProfileImageUrl,
            ActivityType.TITLE_ACQUIRED, title, null,
            "TITLE", titleId, titleName,
            FeedVisibility.PUBLIC, null, null, null);
    }

    @Transactional
    public void notifyLevelUp(String userId, String userNickname, String userProfileImageUrl,
                              int newLevel, int totalExp) {
        String title = String.format("레벨 %d 달성!", newLevel);
        String description = String.format("누적 경험치: %,d", totalExp);
        createActivityFeed(userId, userNickname, userProfileImageUrl,
            ActivityType.LEVEL_UP, title, description,
            "LEVEL", (long) newLevel, "레벨 " + newLevel,
            FeedVisibility.PUBLIC, null, null, null);
    }

    @Transactional
    public void notifyGuildCreated(String userId, String userNickname, String userProfileImageUrl,
                                   Long guildId, String guildName) {
        String title = String.format("[%s] 길드를 창설했습니다!", guildName);
        createActivityFeed(userId, userNickname, userProfileImageUrl,
            ActivityType.GUILD_CREATED, title, null,
            "GUILD", guildId, guildName,
            FeedVisibility.PUBLIC, guildId, null, null);
    }

    @Transactional
    public void notifyGuildJoined(String userId, String userNickname, String userProfileImageUrl,
                                  Long guildId, String guildName) {
        String title = String.format("[%s] 길드에 가입했습니다!", guildName);
        createActivityFeed(userId, userNickname, userProfileImageUrl,
            ActivityType.GUILD_JOINED, title, null,
            "GUILD", guildId, guildName,
            FeedVisibility.PUBLIC, guildId, null, null);
    }

    @Transactional
    public void notifyGuildLevelUp(String userId, String userNickname, String userProfileImageUrl,
                                   Long guildId, String guildName, int newLevel) {
        String title = String.format("[%s] 길드가 레벨 %d로 성장했습니다!", guildName, newLevel);
        createActivityFeed(userId, userNickname, userProfileImageUrl,
            ActivityType.GUILD_LEVEL_UP, title, null,
            "GUILD", guildId, guildName,
            FeedVisibility.GUILD, guildId, null, null);
    }

    @Transactional
    public void notifyFriendAdded(String userId, String userNickname, String userProfileImageUrl,
                                  String friendId, String friendNickname) {
        String title = String.format("%s님과 친구가 되었습니다!", friendNickname);
        createActivityFeed(userId, userNickname, userProfileImageUrl,
            ActivityType.FRIEND_ADDED, title, null,
            "USER", null, friendNickname,
            FeedVisibility.FRIENDS, null, null, null);
    }

    @Transactional
    public void notifyAttendanceStreak(String userId, String userNickname, String userProfileImageUrl,
                                       int streakDays) {
        String title = String.format("%d일 연속 출석 달성!", streakDays);
        createActivityFeed(userId, userNickname, userProfileImageUrl,
            ActivityType.ATTENDANCE_STREAK, title, null,
            "ATTENDANCE", (long) streakDays, streakDays + "일 연속 출석",
            FeedVisibility.PUBLIC, null, null, null);
    }
}
