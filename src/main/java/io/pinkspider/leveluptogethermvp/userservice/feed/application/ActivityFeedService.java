package io.pinkspider.leveluptogethermvp.userservice.feed.application;

import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.FeaturedFeed;
import io.pinkspider.leveluptogethermvp.metaservice.infrastructure.FeaturedFeedRepository;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
    private final FeaturedFeedRepository featuredFeedRepository;

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
     * 카테고리별 공개 피드 조회 (하이브리드 선정)
     * 1. Admin이 설정한 Featured Feed 먼저 표시
     * 2. 자동 선정 (해당 카테고리의 최신 공개 피드)
     * 3. 중복 제거 후 페이징
     */
    public Page<ActivityFeedResponse> getPublicFeedsByCategory(Long categoryId, String currentUserId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        LocalDateTime now = LocalDateTime.now();

        // 1. Admin Featured Feeds 먼저 조회
        List<FeaturedFeed> featuredFeeds = featuredFeedRepository.findActiveFeaturedFeeds(categoryId, now);
        List<Long> featuredFeedIds = featuredFeeds.stream()
            .map(FeaturedFeed::getFeedId)
            .toList();

        List<ActivityFeed> featuredFeedList = new ArrayList<>();
        if (!featuredFeedIds.isEmpty()) {
            List<ActivityFeed> fetchedFeeds = activityFeedRepository.findByIdIn(featuredFeedIds);
            // displayOrder 순서 유지를 위해 Map 사용
            Map<Long, ActivityFeed> feedMap = fetchedFeeds.stream()
                .filter(f -> f.getVisibility() == FeedVisibility.PUBLIC)
                .collect(Collectors.toMap(ActivityFeed::getId, Function.identity()));
            for (Long feedId : featuredFeedIds) {
                ActivityFeed feed = feedMap.get(feedId);
                if (feed != null) {
                    featuredFeedList.add(feed);
                }
            }
        }

        // 2. 카테고리별 일반 피드 조회
        Page<ActivityFeed> categoryFeeds = activityFeedRepository.findPublicFeedsByCategoryId(categoryId, pageable);

        // 3. Featured 피드와 합치기 (첫 페이지에만 Featured 추가)
        List<ActivityFeed> combinedFeeds = new ArrayList<>();
        Set<Long> addedFeedIds = new HashSet<>();

        if (page == 0) {
            for (ActivityFeed featured : featuredFeedList) {
                if (!addedFeedIds.contains(featured.getId())) {
                    combinedFeeds.add(featured);
                    addedFeedIds.add(featured.getId());
                }
            }
        }

        for (ActivityFeed feed : categoryFeeds.getContent()) {
            if (!addedFeedIds.contains(feed.getId())) {
                combinedFeeds.add(feed);
                addedFeedIds.add(feed.getId());
            }
        }

        // 4. 사이즈 조정 (첫 페이지에 featured가 추가되므로)
        if (combinedFeeds.size() > size) {
            combinedFeeds = combinedFeeds.subList(0, size);
        }

        Set<Long> likedFeedIds = getLikedFeedIds(currentUserId, combinedFeeds);
        List<ActivityFeedResponse> responseList = combinedFeeds.stream()
            .map(feed -> ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId())))
            .toList();

        return new org.springframework.data.domain.PageImpl<>(
            responseList,
            pageable,
            categoryFeeds.getTotalElements() + (page == 0 ? featuredFeedList.size() : 0)
        );
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
     * 피드 검색 (제목/미션명 기준, 전체 카테고리)
     */
    public Page<ActivityFeedResponse> searchFeeds(String keyword, String currentUserId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ActivityFeed> feeds = activityFeedRepository.searchByKeyword(keyword, pageable);
        Set<Long> likedFeedIds = getLikedFeedIds(currentUserId, feeds.getContent());
        return feeds.map(feed -> ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId())));
    }

    /**
     * 피드 검색 (제목/미션명 기준, 카테고리 내 검색)
     */
    public Page<ActivityFeedResponse> searchFeedsByCategory(String keyword, String category, String currentUserId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        List<ActivityType> types = ActivityType.getByCategory(category);

        if (types.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<ActivityFeed> feeds = activityFeedRepository.searchByKeywordAndCategory(keyword, types, pageable);
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

    // ========== 사용자 공유 피드 생성 ==========

    /**
     * 사용자가 미션 완료 시 피드에 공유하는 경우 호출
     * - 미션 실행 정보(note, imageUrl, duration, expEarned) 포함
     * - 공개 피드로 생성
     */
    @Transactional
    public ActivityFeed createMissionSharedFeed(String userId, String userNickname, String userProfileImageUrl,
                                                Long executionId, Long missionId, String missionTitle,
                                                String missionDescription, Long categoryId,
                                                String note, String imageUrl,
                                                Integer durationMinutes, Integer expEarned) {
        String title = missionTitle;

        ActivityFeed feed = ActivityFeed.builder()
            .userId(userId)
            .userNickname(userNickname)
            .userProfileImageUrl(userProfileImageUrl)
            .activityType(ActivityType.MISSION_SHARED)
            .title(title)
            .description(note)
            .referenceType("MISSION_EXECUTION")
            .referenceId(executionId)
            .referenceName(missionTitle)
            .visibility(FeedVisibility.PUBLIC)
            .categoryId(categoryId)
            .imageUrl(imageUrl)
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
    @Transactional
    public void deleteFeedById(Long feedId) {
        activityFeedRepository.deleteById(feedId);
        log.info("Feed deleted by id: feedId={}", feedId);
    }
}
