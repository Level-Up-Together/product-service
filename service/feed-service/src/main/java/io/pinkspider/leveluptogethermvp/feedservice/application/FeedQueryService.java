package io.pinkspider.leveluptogethermvp.feedservice.application;

import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.translation.TranslationService;
import io.pinkspider.global.translation.dto.TranslationInfo;
import io.pinkspider.global.translation.enums.ContentType;
import io.pinkspider.global.translation.enums.SupportedLocale;
import io.pinkspider.global.feign.admin.AdminInternalFeignClient;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedCommentResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.admin.FeedAdminPageResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.admin.FeedAdminResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.admin.FeedAdminStatsResponse;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeed;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedComment;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedCommentRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedLikeRepository;
import io.pinkspider.global.enums.ReportTargetType;
import io.pinkspider.leveluptogethermvp.supportservice.report.application.ReportService;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserQueryFacadeService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
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
@Transactional(readOnly = true, transactionManager = "feedTransactionManager")
public class FeedQueryService {

    private final ActivityFeedRepository activityFeedRepository;
    private final FeedLikeRepository feedLikeRepository;
    private final FeedCommentRepository feedCommentRepository;
    private final AdminInternalFeignClient adminInternalFeignClient;
    private final UserQueryFacadeService userQueryFacadeService;
    private final TranslationService translationService;
    private final ReportService reportService;

    /**
     * 전체 공개 피드 조회
     * 시간 필터: 전일 4시 ~ 당일 4시 (24시간 윈도우, 매일 4시에 리셋)
     */
    public Page<ActivityFeedResponse> getPublicFeeds(String currentUserId, int page, int size) {
        return getPublicFeeds(currentUserId, page, size, null);
    }

    /**
     * 전체 공개 피드 조회 (다국어 지원)
     */
    public Page<ActivityFeedResponse> getPublicFeeds(String currentUserId, int page, int size, String acceptLanguage) {
        Pageable pageable = PageRequest.of(page, size);
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        // 시간 범위 계산 (매일 04:00 기준)
        LocalDateTime[] timeRange = calculateDailyFeedTimeRange();
        LocalDateTime startTime = timeRange[0];
        LocalDateTime endTime = timeRange[1];

        Page<ActivityFeed> feeds = activityFeedRepository.findPublicFeedsInTimeRange(startTime, endTime, pageable);

        Set<Long> likedFeedIds = getLikedFeedIds(currentUserId, feeds.getContent());

        // 신고 상태 일괄 조회
        List<String> feedIds = feeds.getContent().stream().map(f -> String.valueOf(f.getId())).toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.FEED, feedIds);

        return feeds.map(feed -> {
            TranslationInfo translation = translateFeed(feed, targetLocale);
            ActivityFeedResponse response = ActivityFeedResponse.from(
                feed,
                likedFeedIds.contains(feed.getId()),
                currentUserId != null && feed.getUserId().equals(currentUserId),
                translation
            );
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
            return response;
        });
    }

    /**
     * 피드 시간 범위 계산 (매일 04:00 기준)
     * - 현재 시간이 04:00 이후: 당일 04:00 ~ 다음날 04:00
     * - 현재 시간이 04:00 이전: 전일 04:00 ~ 당일 04:00
     */
    private LocalDateTime[] calculateDailyFeedTimeRange() {
        LocalDateTime now = LocalDateTime.now();
        LocalTime resetTime = LocalTime.of(4, 0); // 04:00

        LocalDate today = now.toLocalDate();
        LocalDateTime todayReset = LocalDateTime.of(today, resetTime);

        LocalDateTime startTime;
        LocalDateTime endTime;

        if (now.toLocalTime().isBefore(resetTime)) {
            // 현재 시간이 04:00 이전 → 전일 04:00 ~ 당일 04:00
            startTime = todayReset.minusDays(1);
            endTime = todayReset;
        } else {
            // 현재 시간이 04:00 이후 → 당일 04:00 ~ 다음날 04:00
            startTime = todayReset;
            endTime = todayReset.plusDays(1);
        }

        log.debug("Feed time range: {} ~ {}", startTime, endTime);
        return new LocalDateTime[]{startTime, endTime};
    }

    /**
     * 카테고리별 공개 피드 조회 (하이브리드 선정)
     * 1. Admin이 설정한 Featured Feed 먼저 표시
     * 2. 자동 선정 (해당 카테고리의 최신 공개 피드) - 시간 필터 적용
     * 3. 중복 제거 후 페이징
     */
    public Page<ActivityFeedResponse> getPublicFeedsByCategory(Long categoryId, String currentUserId, int page, int size) {
        return getPublicFeedsByCategory(categoryId, currentUserId, page, size, null);
    }

    /**
     * 카테고리별 공개 피드 조회 (다국어 지원)
     */
    public Page<ActivityFeedResponse> getPublicFeedsByCategory(Long categoryId, String currentUserId, int page, int size, String acceptLanguage) {
        Pageable pageable = PageRequest.of(page, size);
        LocalDateTime now = LocalDateTime.now();
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        // 시간 범위 계산 (매일 04:00 기준)
        LocalDateTime[] timeRange = calculateDailyFeedTimeRange();
        LocalDateTime startTime = timeRange[0];
        LocalDateTime endTime = timeRange[1];

        // 1. Admin Featured Feeds 먼저 조회
        List<Long> featuredFeedIds = adminInternalFeignClient.getFeaturedFeedIds(categoryId);

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

        // 2. 카테고리별 일반 피드 조회 (시간 필터 적용)
        Page<ActivityFeed> categoryFeeds = activityFeedRepository.findPublicFeedsByCategoryIdInTimeRange(
            categoryId, startTime, endTime, pageable);

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

        // 신고 상태 일괄 조회
        List<String> feedIds = combinedFeeds.stream().map(f -> String.valueOf(f.getId())).toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.FEED, feedIds);

        List<ActivityFeedResponse> responseList = combinedFeeds.stream()
            .map(feed -> {
                TranslationInfo translation = translateFeed(feed, targetLocale);
                ActivityFeedResponse response = ActivityFeedResponse.from(
                    feed,
                    likedFeedIds.contains(feed.getId()),
                    currentUserId != null && feed.getUserId().equals(currentUserId),
                    translation
                );
                response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
                return response;
            })
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
        return getTimelineFeeds(userId, page, size, null);
    }

    /**
     * 내 타임라인 피드 조회 (다국어 지원)
     */
    public Page<ActivityFeedResponse> getTimelineFeeds(String userId, int page, int size, String acceptLanguage) {
        Pageable pageable = PageRequest.of(page, size);
        List<String> friendIds = userQueryFacadeService.getFriendIds(userId);
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        Page<ActivityFeed> feeds;
        if (friendIds.isEmpty()) {
            feeds = activityFeedRepository.findByUserId(userId, pageable);
        } else {
            feeds = activityFeedRepository.findTimelineFeeds(userId, friendIds, pageable);
        }

        Set<Long> likedFeedIds = getLikedFeedIds(userId, feeds.getContent());

        // 신고 상태 일괄 조회
        List<String> feedIds = feeds.getContent().stream().map(f -> String.valueOf(f.getId())).toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.FEED, feedIds);

        return feeds.map(feed -> {
            TranslationInfo translation = translateFeed(feed, targetLocale);
            ActivityFeedResponse response = ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId()), false, translation);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
            return response;
        });
    }

    /**
     * 특정 사용자의 피드 조회
     */
    public Page<ActivityFeedResponse> getUserFeeds(String targetUserId, String currentUserId, int page, int size) {
        return getUserFeeds(targetUserId, currentUserId, page, size, null);
    }

    /**
     * 특정 사용자의 피드 조회 (다국어 지원)
     */
    public Page<ActivityFeedResponse> getUserFeeds(String targetUserId, String currentUserId, int page, int size, String acceptLanguage) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ActivityFeed> feeds = activityFeedRepository.findByUserId(targetUserId, pageable);
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        Set<Long> likedFeedIds = getLikedFeedIds(currentUserId, feeds.getContent());

        // 친구 여부에 따라 visibility 필터링
        boolean isFriend = userQueryFacadeService.areFriends(currentUserId, targetUserId);
        boolean isSelf = currentUserId.equals(targetUserId);

        // 신고 상태 일괄 조회
        List<String> feedIds = feeds.getContent().stream().map(f -> String.valueOf(f.getId())).toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.FEED, feedIds);

        return feeds
            .map(feed -> {
                TranslationInfo translation = translateFeed(feed, targetLocale);
                ActivityFeedResponse response;
                // 본인 피드이면 모두 표시
                if (isSelf) {
                    response = ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId()), true, translation);
                    response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
                    return response;
                }
                // 공개 피드만 표시 (친구면 FRIENDS까지)
                if (feed.getVisibility() == FeedVisibility.PUBLIC ||
                    (isFriend && feed.getVisibility() == FeedVisibility.FRIENDS)) {
                    response = ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId()), false, translation);
                    response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
                    return response;
                }
                return null;
            });
    }

    /**
     * 길드 피드 조회
     */
    public Page<ActivityFeedResponse> getGuildFeeds(Long guildId, String currentUserId, int page, int size) {
        return getGuildFeeds(guildId, currentUserId, page, size, null);
    }

    /**
     * 길드 피드 조회 (다국어 지원)
     */
    public Page<ActivityFeedResponse> getGuildFeeds(Long guildId, String currentUserId, int page, int size, String acceptLanguage) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ActivityFeed> feeds = activityFeedRepository.findGuildFeeds(guildId, pageable);
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        Set<Long> likedFeedIds = getLikedFeedIds(currentUserId, feeds.getContent());

        // 신고 상태 일괄 조회
        List<String> feedIds = feeds.getContent().stream().map(f -> String.valueOf(f.getId())).toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.FEED, feedIds);

        return feeds.map(feed -> {
            TranslationInfo translation = translateFeed(feed, targetLocale);
            ActivityFeedResponse response = ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId()), false, translation);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
            return response;
        });
    }

    /**
     * 카테고리별 피드 조회
     */
    public Page<ActivityFeedResponse> getFeedsByCategory(String category, String currentUserId, int page, int size) {
        return getFeedsByCategory(category, currentUserId, page, size, null);
    }

    /**
     * 카테고리별 피드 조회 (다국어 지원)
     */
    public Page<ActivityFeedResponse> getFeedsByCategory(String category, String currentUserId, int page, int size, String acceptLanguage) {
        Pageable pageable = PageRequest.of(page, size);
        List<ActivityType> types = ActivityType.getByCategory(category);
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        if (types.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<ActivityFeed> feeds = activityFeedRepository.findByCategoryTypes(types, pageable);
        Set<Long> likedFeedIds = getLikedFeedIds(currentUserId, feeds.getContent());

        // 신고 상태 일괄 조회
        List<String> feedIds = feeds.getContent().stream().map(f -> String.valueOf(f.getId())).toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.FEED, feedIds);

        return feeds.map(feed -> {
            TranslationInfo translation = translateFeed(feed, targetLocale);
            ActivityFeedResponse response = ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId()), false, translation);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
            return response;
        });
    }

    /**
     * 피드 검색 (제목/미션명 기준, 전체 카테고리)
     */
    public Page<ActivityFeedResponse> searchFeeds(String keyword, String currentUserId, int page, int size) {
        return searchFeeds(keyword, currentUserId, page, size, null);
    }

    /**
     * 피드 검색 (다국어 지원)
     */
    public Page<ActivityFeedResponse> searchFeeds(String keyword, String currentUserId, int page, int size, String acceptLanguage) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ActivityFeed> feeds = activityFeedRepository.searchByKeyword(keyword, pageable);
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        Set<Long> likedFeedIds = getLikedFeedIds(currentUserId, feeds.getContent());

        // 신고 상태 일괄 조회
        List<String> feedIds = feeds.getContent().stream().map(f -> String.valueOf(f.getId())).toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.FEED, feedIds);

        return feeds.map(feed -> {
            TranslationInfo translation = translateFeed(feed, targetLocale);
            ActivityFeedResponse response = ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId()), false, translation);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
            return response;
        });
    }

    /**
     * 피드 검색 (제목/미션명 기준, 카테고리 내 검색)
     */
    public Page<ActivityFeedResponse> searchFeedsByCategory(String keyword, String category, String currentUserId, int page, int size) {
        return searchFeedsByCategory(keyword, category, currentUserId, page, size, null);
    }

    /**
     * 피드 검색 (카테고리별, 다국어 지원)
     */
    public Page<ActivityFeedResponse> searchFeedsByCategory(String keyword, String category, String currentUserId, int page, int size, String acceptLanguage) {
        Pageable pageable = PageRequest.of(page, size);
        List<ActivityType> types = ActivityType.getByCategory(category);
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        if (types.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<ActivityFeed> feeds = activityFeedRepository.searchByKeywordAndCategory(keyword, types, pageable);
        Set<Long> likedFeedIds = getLikedFeedIds(currentUserId, feeds.getContent());

        // 신고 상태 일괄 조회
        List<String> feedIds = feeds.getContent().stream().map(f -> String.valueOf(f.getId())).toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.FEED, feedIds);

        return feeds.map(feed -> {
            TranslationInfo translation = translateFeed(feed, targetLocale);
            ActivityFeedResponse response = ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId()), false, translation);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
            return response;
        });
    }

    /**
     * 피드 상세 조회
     */
    public ActivityFeedResponse getFeed(Long feedId, String currentUserId) {
        return getFeed(feedId, currentUserId, null);
    }

    /**
     * 피드 상세 조회 (다국어 지원)
     */
    public ActivityFeedResponse getFeed(Long feedId, String currentUserId, String acceptLanguage) {
        ActivityFeed feed = activityFeedRepository.findById(feedId)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "피드를 찾을 수 없습니다"));
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        boolean likedByMe = currentUserId != null && feedLikeRepository.existsByFeedIdAndUserId(feedId, currentUserId);
        boolean isMyFeed = currentUserId != null && feed.getUserId().equals(currentUserId);
        TranslationInfo translation = translateFeed(feed, targetLocale);
        ActivityFeedResponse response = ActivityFeedResponse.from(feed, likedByMe, isMyFeed, translation);

        // 신고 상태 조회
        response.setIsUnderReview(reportService.isUnderReview(ReportTargetType.FEED, String.valueOf(feedId)));
        return response;
    }

    /**
     * 댓글 목록 조회
     */
    public Page<FeedCommentResponse> getComments(Long feedId, String currentUserId, int page, int size) {
        return getComments(feedId, currentUserId, page, size, null);
    }

    /**
     * 댓글 목록 조회 (다국어 지원)
     * - 모든 댓글에 대해 현재 유저 레벨을 표시 (작성 시점 레벨이 아닌 현재 레벨)
     */
    public Page<FeedCommentResponse> getComments(Long feedId, String currentUserId, int page, int size, String acceptLanguage) {
        Pageable pageable = PageRequest.of(page, size);
        Page<FeedComment> comments = feedCommentRepository.findByFeedId(feedId, pageable);
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        // 신고 상태 일괄 조회
        List<String> commentIds = comments.getContent().stream().map(c -> String.valueOf(c.getId())).toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.FEED_COMMENT, commentIds);

        return comments.map(comment -> {
            TranslationInfo translation = translateComment(comment, targetLocale);
            // 모든 댓글에 대해 현재 유저 레벨 조회
            Integer userLevel;
            try {
                UserProfileCache userProfile = userQueryFacadeService.getUserProfile(comment.getUserId());
                userLevel = userProfile.level();
            } catch (Exception e) {
                log.warn("Failed to get user level for comment: commentId={}, userId={}", comment.getId(), comment.getUserId());
                userLevel = comment.getUserLevel() != null ? comment.getUserLevel() : 1;
            }
            FeedCommentResponse response = FeedCommentResponse.from(comment, translation, currentUserId, userLevel);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(comment.getId()), false));
            return response;
        });
    }

    /**
     * executionId로 피드 존재 여부 확인
     */
    public boolean existsFeedByExecutionId(Long executionId) {
        return activityFeedRepository.findByExecutionId(executionId).isPresent();
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

    /**
     * 피드 번역 (제목 + 설명)
     */
    private TranslationInfo translateFeed(ActivityFeed feed, String targetLocale) {
        // 기본 언어(한국어)면 번역 불필요
        if (SupportedLocale.DEFAULT.getCode().equals(targetLocale)) {
            return TranslationInfo.notTranslated(SupportedLocale.DEFAULT.getCode());
        }

        return translationService.translateContent(
            ContentType.FEED,
            feed.getId(),
            feed.getTitle(),
            feed.getDescription(),
            targetLocale
        );
    }

    /**
     * 댓글 번역
     */
    private TranslationInfo translateComment(FeedComment comment, String targetLocale) {
        // 기본 언어(한국어)면 번역 불필요
        if (SupportedLocale.DEFAULT.getCode().equals(targetLocale)) {
            return TranslationInfo.notTranslated(SupportedLocale.DEFAULT.getCode());
        }

        // 삭제된 댓글은 번역하지 않음
        if (comment.getIsDeleted()) {
            return TranslationInfo.notTranslated(SupportedLocale.DEFAULT.getCode());
        }

        return translationService.translateContent(
            ContentType.FEED_COMMENT,
            comment.getId(),
            comment.getContent(),
            targetLocale
        );
    }

    // ===== Admin 내부 API용 메서드 =====

    /**
     * Admin 피드 검색 (내부 API)
     */
    public FeedAdminPageResponse searchFeedsForAdmin(
            String activityType, String visibility, String userId,
            Long categoryId, String keyword, int page, int size,
            String sortBy, String sortDirection) {

        Sort sort = "ASC".equalsIgnoreCase(sortDirection)
            ? Sort.by(sortBy != null ? sortBy : "id").ascending()
            : Sort.by(sortBy != null ? sortBy : "id").descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        ActivityType activityTypeEnum = activityType != null ? ActivityType.valueOf(activityType) : null;
        FeedVisibility visibilityEnum = visibility != null ? FeedVisibility.valueOf(visibility) : null;

        Page<ActivityFeed> feedPage = activityFeedRepository.searchFeedsForAdmin(
            activityTypeEnum, visibilityEnum, userId, categoryId, keyword, pageable);

        Page<FeedAdminResponse> responsePage = feedPage.map(FeedAdminResponse::from);
        return FeedAdminPageResponse.from(responsePage);
    }

    /**
     * Admin 피드 상세 조회 (내부 API)
     */
    public FeedAdminResponse getFeedForAdmin(Long id) {
        ActivityFeed feed = activityFeedRepository.findById(id)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "피드를 찾을 수 없습니다"));
        return FeedAdminResponse.from(feed);
    }

    /**
     * Admin 피드 통계 (내부 API)
     * reportedCount는 admin_db 소유이므로 admin backend에서 enrichment
     */
    public FeedAdminStatsResponse getFeedStats() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        long totalCount = activityFeedRepository.count();
        long publicCount = activityFeedRepository.countByVisibility(FeedVisibility.PUBLIC);
        long todayNewCount = activityFeedRepository.countByCreatedAtSince(startOfToday);
        long missionSharedCount = activityFeedRepository.countByActivityType(ActivityType.MISSION_SHARED);

        return FeedAdminStatsResponse.builder()
            .totalCount(totalCount)
            .publicCount(publicCount)
            .todayNewCount(todayNewCount)
            .missionSharedCount(missionSharedCount)
            .build();
    }
}
