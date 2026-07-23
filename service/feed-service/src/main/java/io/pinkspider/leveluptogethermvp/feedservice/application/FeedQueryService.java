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
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeedImage;
import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedComment;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedSearchType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedImageRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedCommentLikeRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedCommentRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedLikeRepository;
import io.pinkspider.global.enums.ReportTargetType;
import io.pinkspider.leveluptogethermvp.supportservice.report.application.ReportService;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.facade.dto.GuildMembershipInfo;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.dto.UserTitleDto;
import io.pinkspider.global.translation.TitleNameUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final ActivityFeedImageRepository activityFeedImageRepository;
    private final FeedLikeRepository feedLikeRepository;
    private final FeedCommentRepository feedCommentRepository;
    private final FeedCommentLikeRepository feedCommentLikeRepository;
    private final AdminInternalFeignClient adminInternalFeignClient;
    private final UserQueryFacade userQueryFacadeService;
    private final GuildQueryFacade guildQueryFacadeService;
    private final GamificationQueryFacade gamificationQueryFacadeService;
    private final TranslationService translationService;
    private final ReportService reportService;
    private final FeedAccessChecker feedAccessChecker;

    /**
     * 전체 공개 피드 조회
     * 시간 필터: 전일 4시 ~ 당일 4시 (24시간 윈도우, 매일 4시에 리셋)
     */
    public Page<ActivityFeedResponse> getPublicFeeds(String currentUserId, int page, int size) {
        return getPublicFeeds(currentUserId, page, size, null);
    }

    /**
     * 전체 공개 피드 조회 (다국어 지원)
     *
     * QA-168: 사용자가 볼 수 있는 모든 피드를 보여준다.
     * - PUBLIC 피드 (모든 사람)
     * - 본인이 작성한 피드 (PRIVATE 제외)
     * - 친구가 작성한 FRIENDS 공개 피드
     * - 같은 길드원이 작성한 GUILD 공개 피드
     */
    public Page<ActivityFeedResponse> getPublicFeeds(String currentUserId, int page, int size, String acceptLanguage) {
        Pageable pageable = PageRequest.of(page, size);
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        List<String> friendIds = resolveFriendIds(currentUserId);
        List<Long> guildIds = resolveGuildIds(currentUserId);
        Page<ActivityFeed> feeds = activityFeedRepository.findAccessibleFeeds(
            currentUserId, friendIds, guildIds, pageable);

        Set<Long> likedFeedIds = getLikedFeedIds(currentUserId, feeds.getContent());

        // 신고 상태 일괄 조회
        List<String> feedIds = feeds.getContent().stream().map(f -> String.valueOf(f.getId())).toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.FEED, feedIds);

        // 번역 일괄 처리 (피드별 개별 Google 호출 방지)
        Map<Long, TranslationInfo> translationMap = translateFeedsBatch(feeds.getContent(), targetLocale);

        Page<ActivityFeedResponse> result = feeds.map(feed -> {
            TranslationInfo translation = translationFor(translationMap, feed);
            ActivityFeedResponse response = ActivityFeedResponse.from(
                feed,
                likedFeedIds.contains(feed.getId()),
                currentUserId != null && feed.getUserId().equals(currentUserId),
                translation
            );
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
            return response;
        });
        enrichWithImageUrls(result.getContent());
        localizeUserTitles(result.getContent(), acceptLanguage);
        return result;
    }

    /**
     * 필터 타입별 피드 조회 (QA-60)
     *
     * @param searchType ALL=전체 공개, FRIENDS=친구 글, GUILD=길드 글, MINE=내 글
     */
    public Page<ActivityFeedResponse> getFilteredFeeds(FeedSearchType searchType, String userId,
                                                        int page, int size, String acceptLanguage) {
        if (searchType == null || userId == null) {
            return getPublicFeeds(userId, page, size, acceptLanguage);
        }
        return switch (searchType) {
            case ALL -> getPublicFeeds(userId, page, size, acceptLanguage);
            case FRIENDS -> getFriendsOnlyFeeds(userId, page, size, acceptLanguage);
            case GUILD -> getMyGuildFeeds(userId, page, size, acceptLanguage);
            case MINE -> getMyFeeds(userId, page, size, acceptLanguage);
        };
    }

    /**
     * 친구 피드 조회 (PUBLIC + FRIENDS 공개범위)
     *
     * <p>QA-168: 본인이 작성한 친구공개 피드도 친구 탭에서 보여야 한다. friendIds 목록에 본인을 포함시켜
     * "내가 친구공개로 올린 피드"가 친구 탭에서 누락되지 않도록 한다.
     */
    private Page<ActivityFeedResponse> getFriendsOnlyFeeds(String userId, int page, int size, String acceptLanguage) {
        Pageable pageable = PageRequest.of(page, size);
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        List<String> friendIds = new ArrayList<>(userQueryFacadeService.getFriendIds(userId));
        friendIds.add(userId);

        Page<ActivityFeed> feeds = activityFeedRepository.findFriendsFeeds(friendIds, pageable);
        return enrichFeeds(feeds, userId, targetLocale);
    }

    /**
     * 내가 속한 길드의 피드 조회
     */
    private Page<ActivityFeedResponse> getMyGuildFeeds(String userId, int page, int size, String acceptLanguage) {
        Pageable pageable = PageRequest.of(page, size);
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        List<Long> guildIds = guildQueryFacadeService.getUserGuildMemberships(userId)
            .stream().map(GuildMembershipInfo::guildId).toList();

        if (guildIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<ActivityFeed> feeds = activityFeedRepository.findGuildOnlyFeedsByGuildIds(guildIds, pageable);
        return enrichFeeds(feeds, userId, targetLocale);
    }

    /**
     * 내가 쓴 피드만 조회 (PRIVATE 제외 — 홈피드 MINE 필터용)
     */
    private Page<ActivityFeedResponse> getMyFeeds(String userId, int page, int size, String acceptLanguage) {
        Pageable pageable = PageRequest.of(page, size);
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        Page<ActivityFeed> feeds = activityFeedRepository.findPublicFeedsByUserId(userId, pageable);
        return enrichFeeds(feeds, userId, targetLocale);
    }

    /**
     * QA-168: 사용자의 친구 ID 목록 조회. 비로그인은 빈 리스트.
     * 빈 리스트는 JPQL의 IN 절에서 false로 평가되어 친구 피드는 결과에서 제외됨.
     */
    private List<String> resolveFriendIds(String userId) {
        if (userId == null) {
            return List.of();
        }
        List<String> ids = userQueryFacadeService.getFriendIds(userId);
        return ids != null ? ids : List.of();
    }

    /**
     * QA-168: 사용자의 길드 ID 목록 조회. 비로그인은 빈 리스트.
     */
    private List<Long> resolveGuildIds(String userId) {
        if (userId == null) {
            return List.of();
        }
        List<GuildMembershipInfo> memberships = guildQueryFacadeService.getUserGuildMemberships(userId);
        if (memberships == null) {
            return List.of();
        }
        return memberships.stream().map(GuildMembershipInfo::guildId).toList();
    }

    /**
     * 피드 목록을 좋아요/신고/번역/다중이미지 정보로 보강 (QA-139: enrich 누락 제거)
     */
    private Page<ActivityFeedResponse> enrichFeeds(Page<ActivityFeed> feeds, String userId, String targetLocale) {
        Set<Long> likedFeedIds = getLikedFeedIds(userId, feeds.getContent());
        List<String> feedIds = feeds.getContent().stream().map(f -> String.valueOf(f.getId())).toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.FEED, feedIds);

        // 번역 일괄 처리 (피드별 개별 Google 호출 방지)
        Map<Long, TranslationInfo> translationMap = translateFeedsBatch(feeds.getContent(), targetLocale);

        Page<ActivityFeedResponse> result = feeds.map(feed -> {
            TranslationInfo translation = translationFor(translationMap, feed);
            ActivityFeedResponse response = ActivityFeedResponse.from(
                feed,
                likedFeedIds.contains(feed.getId()),
                userId != null && feed.getUserId().equals(userId),
                translation
            );
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
            return response;
        });
        enrichWithImageUrls(result.getContent());
        localizeUserTitles(result.getContent(), targetLocale);
        return result;
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
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

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

        // 2. 카테고리별 일반 피드 조회 (QA-168: 친구 FRIENDS / 길드 GUILD 포함)
        List<String> friendIds = resolveFriendIds(currentUserId);
        List<Long> guildIds = resolveGuildIds(currentUserId);
        Page<ActivityFeed> categoryFeeds = activityFeedRepository.findAccessibleFeedsByCategoryId(
            categoryId, currentUserId, friendIds, guildIds, pageable);

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

        // 번역 일괄 처리 (피드별 개별 Google 호출 방지)
        Map<Long, TranslationInfo> translationMap = translateFeedsBatch(combinedFeeds, targetLocale);

        List<ActivityFeedResponse> responseList = combinedFeeds.stream()
            .map(feed -> {
                TranslationInfo translation = translationFor(translationMap, feed);
                ActivityFeedResponse response = ActivityFeedResponse.from(
                    feed,
                    likedFeedIds.contains(feed.getId()),
                    currentUserId != null && feed.getUserId().equals(currentUserId),
                    translation
                );
                response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
                return response;
            })
            .collect(Collectors.toList());

        // QA-139: 다중 이미지 응답 보강
        enrichWithImageUrls(responseList);
        localizeUserTitles(responseList, acceptLanguage);

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

        // 번역 일괄 처리 (피드별 개별 Google 호출 방지)
        Map<Long, TranslationInfo> translationMap = translateFeedsBatch(feeds.getContent(), targetLocale);

        Page<ActivityFeedResponse> result = feeds.map(feed -> {
            TranslationInfo translation = translationFor(translationMap, feed);
            ActivityFeedResponse response = ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId()), false, translation);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
            return response;
        });
        // QA-139: 다중 이미지 응답 보강
        enrichWithImageUrls(result.getContent());
        localizeUserTitles(result.getContent(), acceptLanguage);
        return result;
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

        // 관계에 따라 visibility 필터링
        boolean isSelf = currentUserId.equals(targetUserId);
        boolean isFriend = !isSelf && userQueryFacadeService.areFriends(currentUserId, targetUserId);

        // 같은 길드 소속 여부 (GUILD 공개범위 피드 열람용)
        Set<Long> myGuildIds = guildQueryFacadeService.getUserGuildMemberships(currentUserId)
            .stream().map(GuildMembershipInfo::guildId).collect(java.util.stream.Collectors.toSet());

        // 신고 상태 일괄 조회
        List<String> feedIds = feeds.getContent().stream().map(f -> String.valueOf(f.getId())).toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.FEED, feedIds);

        // 번역 일괄 처리 (피드별 개별 Google 호출 방지)
        Map<Long, TranslationInfo> translationMap = translateFeedsBatch(feeds.getContent(), targetLocale);

        Page<ActivityFeedResponse> result = feeds
            .map(feed -> {
                TranslationInfo translation = translationFor(translationMap, feed);
                ActivityFeedResponse response;
                // 본인 피드이면 모두 표시
                if (isSelf) {
                    response = ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId()), true, translation);
                    response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
                    return response;
                }
                // visibility별 접근 제어
                boolean canView = switch (feed.getVisibility()) {
                    case PUBLIC -> true;
                    case FRIENDS -> isFriend;
                    case GUILD -> feed.getGuildId() != null && myGuildIds.contains(feed.getGuildId());
                    case PRIVATE -> false;
                };
                if (canView) {
                    response = ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId()), false, translation);
                    response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
                    return response;
                }
                return null;
            });
        // QA-139: 다중 이미지 응답 보강 (null 제외)
        enrichWithImageUrls(result.getContent().stream().filter(java.util.Objects::nonNull).toList());
        localizeUserTitles(result.getContent().stream().filter(java.util.Objects::nonNull).toList(), acceptLanguage);
        return result;
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

        // 길드 멤버만 GUILD 공개범위 피드 열람 가능
        List<Long> myGuildIds = guildQueryFacadeService.getUserGuildMemberships(currentUserId)
            .stream().map(GuildMembershipInfo::guildId).toList();
        if (!myGuildIds.contains(guildId)) {
            // 비멤버는 PUBLIC 피드만 조회
            Page<ActivityFeed> feeds = activityFeedRepository.findPublicFeedsByGuildId(guildId, pageable);
            return enrichFeeds(feeds, currentUserId, SupportedLocale.extractLanguageCode(acceptLanguage));
        }

        Page<ActivityFeed> feeds = activityFeedRepository.findGuildFeeds(guildId, pageable);
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        Set<Long> likedFeedIds = getLikedFeedIds(currentUserId, feeds.getContent());

        // 신고 상태 일괄 조회
        List<String> feedIds = feeds.getContent().stream().map(f -> String.valueOf(f.getId())).toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.FEED, feedIds);

        // 번역 일괄 처리 (피드별 개별 Google 호출 방지)
        Map<Long, TranslationInfo> translationMap = translateFeedsBatch(feeds.getContent(), targetLocale);

        Page<ActivityFeedResponse> result = feeds.map(feed -> {
            TranslationInfo translation = translationFor(translationMap, feed);
            ActivityFeedResponse response = ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId()), false, translation);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
            return response;
        });
        // QA-139: 다중 이미지 응답 보강
        enrichWithImageUrls(result.getContent());
        localizeUserTitles(result.getContent(), acceptLanguage);
        return result;
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

        // 번역 일괄 처리 (피드별 개별 Google 호출 방지)
        Map<Long, TranslationInfo> translationMap = translateFeedsBatch(feeds.getContent(), targetLocale);

        Page<ActivityFeedResponse> result = feeds.map(feed -> {
            TranslationInfo translation = translationFor(translationMap, feed);
            ActivityFeedResponse response = ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId()), false, translation);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
            return response;
        });
        // QA-139: 다중 이미지 응답 보강
        enrichWithImageUrls(result.getContent());
        localizeUserTitles(result.getContent(), acceptLanguage);
        return result;
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

        // 번역 일괄 처리 (피드별 개별 Google 호출 방지)
        Map<Long, TranslationInfo> translationMap = translateFeedsBatch(feeds.getContent(), targetLocale);

        Page<ActivityFeedResponse> result = feeds.map(feed -> {
            TranslationInfo translation = translationFor(translationMap, feed);
            ActivityFeedResponse response = ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId()), false, translation);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
            return response;
        });
        // QA-139: 다중 이미지 응답 보강
        enrichWithImageUrls(result.getContent());
        localizeUserTitles(result.getContent(), acceptLanguage);
        return result;
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

        // 번역 일괄 처리 (피드별 개별 Google 호출 방지)
        Map<Long, TranslationInfo> translationMap = translateFeedsBatch(feeds.getContent(), targetLocale);

        Page<ActivityFeedResponse> result = feeds.map(feed -> {
            TranslationInfo translation = translationFor(translationMap, feed);
            ActivityFeedResponse response = ActivityFeedResponse.from(feed, likedFeedIds.contains(feed.getId()), false, translation);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(feed.getId()), false));
            return response;
        });
        // QA-139: 다중 이미지 응답 보강
        enrichWithImageUrls(result.getContent());
        localizeUserTitles(result.getContent(), acceptLanguage);
        return result;
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
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "error.feed.not_found"));
        feedAccessChecker.assertAccessible(feed, currentUserId);
        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        boolean likedByMe = currentUserId != null && feedLikeRepository.existsByFeedIdAndUserId(feedId, currentUserId);
        boolean isMyFeed = currentUserId != null && feed.getUserId().equals(currentUserId);
        TranslationInfo translation = translateFeed(feed, targetLocale);
        ActivityFeedResponse response = ActivityFeedResponse.from(feed, likedByMe, isMyFeed, translation);

        // 신고 상태 조회
        response.setIsUnderReview(reportService.isUnderReview(ReportTargetType.FEED, String.valueOf(feedId)));

        // QA-53: 다중 이미지 캐러셀
        enrichWithImageUrls(List.of(response));
        localizeUserTitles(List.of(response), acceptLanguage);
        return response;
    }

    /**
     * 댓글 목록 조회
     */
    public Page<FeedCommentResponse> getComments(Long feedId, String currentUserId, int page, int size) {
        return getComments(feedId, currentUserId, page, size, null);
    }

    /**
     * 댓글 목록 조회 (다국어 + 트리 응답)
     * - 부모 댓글만 페이징하고, 각 부모 아래 대댓글을 한 번에 트리로 반환 (1-depth 제한).
     * - like_count / is_liked / is_editable / is_edited 필드 동시 포함.
     * - 모든 댓글에 대해 현재 유저 레벨을 표시 (작성 시점 레벨이 아닌 현재 레벨).
     */
    public Page<FeedCommentResponse> getComments(Long feedId, String currentUserId, int page, int size, String acceptLanguage) {
        ActivityFeed feed = activityFeedRepository.findById(feedId)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "error.feed.not_found"));
        feedAccessChecker.assertAccessible(feed, currentUserId);

        Pageable pageable = PageRequest.of(page, size);
        Page<FeedComment> rootPage = feedCommentRepository.findRootCommentsByFeedId(feedId, pageable);
        List<FeedComment> roots = rootPage.getContent();
        if (roots.isEmpty()) {
            return rootPage.map(c -> FeedCommentResponse.from(c, null, currentUserId));
        }

        String targetLocale = SupportedLocale.extractLanguageCode(acceptLanguage);

        // 1) 대댓글 일괄 조회 (N+1 방지)
        List<Long> rootIds = roots.stream().map(FeedComment::getId).toList();
        List<FeedComment> replies = feedCommentRepository.findRepliesByParentIds(rootIds);

        // 2) 신고 상태 / 좋아요 수 / 좋아요 여부 일괄 조회 (부모 + 대댓글 합산)
        List<Long> allCommentIds = new ArrayList<>(rootIds);
        replies.forEach(r -> allCommentIds.add(r.getId()));
        List<String> allCommentIdStrs = allCommentIds.stream().map(String::valueOf).toList();

        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.FEED_COMMENT, allCommentIdStrs);

        Map<Long, Integer> likeCountMap = feedCommentLikeRepository.countByCommentIds(allCommentIds).stream()
            .collect(Collectors.toMap(
                row -> ((Number) row[0]).longValue(),
                row -> ((Number) row[1]).intValue()
            ));
        Set<Long> likedSet = currentUserId == null ? new HashSet<>()
            : new HashSet<>(feedCommentLikeRepository.findLikedCommentIds(currentUserId, allCommentIds));

        // 2-1) 댓글 작성자 프로필(현재 레벨) 일괄 조회 (댓글별 개별 조회 N+1 방지)
        Set<String> commentUserIds = new HashSet<>();
        roots.forEach(c -> commentUserIds.add(c.getUserId()));
        replies.forEach(c -> commentUserIds.add(c.getUserId()));
        Map<String, UserProfileInfo> profileMap;
        try {
            profileMap = userQueryFacadeService.getUserProfiles(new ArrayList<>(commentUserIds));
        } catch (Exception e) {
            log.warn("Failed to batch load comment user profiles: feedId={}", feedId);
            profileMap = Map.of();
        }
        Map<String, UserProfileInfo> commentProfileMap = profileMap;

        // 2-2) 댓글 번역 일괄 처리 (댓글별 개별 Google 호출 방지)
        List<FeedComment> allComments = new ArrayList<>(roots);
        allComments.addAll(replies);
        Map<Long, TranslationInfo> commentTranslationMap = translateCommentsBatch(allComments, targetLocale);

        // 3) 부모 댓글별 활성 대댓글 카운트 (수정 가능 여부 결정용)
        Map<Long, Long> activeReplyCountByParent = replies.stream()
            .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
            .collect(Collectors.groupingBy(r -> r.getParent().getId(), Collectors.counting()));

        // 4) 응답 조립
        Map<Long, List<FeedCommentResponse>> repliesByParent = new java.util.HashMap<>();
        for (FeedComment reply : replies) {
            FeedCommentResponse r = buildCommentResponse(reply, currentUserId, likeCountMap, likedSet,
                underReviewMap, commentProfileMap, commentTranslationMap, /*hasReplies*/ false);
            repliesByParent.computeIfAbsent(reply.getParent().getId(), k -> new ArrayList<>()).add(r);
        }

        return rootPage.map(root -> {
            boolean hasReplies = activeReplyCountByParent.getOrDefault(root.getId(), 0L) > 0;
            FeedCommentResponse response = buildCommentResponse(root, currentUserId, likeCountMap,
                likedSet, underReviewMap, commentProfileMap, commentTranslationMap, hasReplies);
            response.setReplies(repliesByParent.getOrDefault(root.getId(), List.of()));
            return response;
        });
    }

    /**
     * 단일 댓글 응답 빌드 헬퍼. 트리 응답에서 부모/대댓글 공통으로 사용.
     */
    private FeedCommentResponse buildCommentResponse(FeedComment comment, String currentUserId,
                                                     Map<Long, Integer> likeCountMap, Set<Long> likedSet,
                                                     Map<String, Boolean> underReviewMap,
                                                     Map<String, UserProfileInfo> profileMap,
                                                     Map<Long, TranslationInfo> translationMap, boolean hasReplies) {
        TranslationInfo translation = translationMap.getOrDefault(
            comment.getId(), TranslationInfo.notTranslated(SupportedLocale.DEFAULT.getCode()));
        Integer userLevel;
        UserProfileInfo userProfile = profileMap.get(comment.getUserId());
        if (userProfile != null) {
            userLevel = userProfile.level();
        } else {
            userLevel = comment.getUserLevel() != null ? comment.getUserLevel() : 1;
        }

        FeedCommentResponse response = FeedCommentResponse.from(comment, translation, currentUserId, userLevel);
        response.setLikeCount(likeCountMap.getOrDefault(comment.getId(), 0));
        response.setLiked(likedSet.contains(comment.getId()));
        response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(comment.getId()), false));

        // 수정 가능 여부: 본인 + 미삭제 + (최상위면 활성 대댓글 없음 / 대댓글은 항상 가능)
        boolean editable = response.getIsMyComment()
            && !response.getIsDeleted()
            && (comment.isReply() || !hasReplies);
        response.setEditable(editable);

        return response;
    }

    /**
     * executionId로 피드 존재 여부 확인
     */
    public boolean existsFeedByExecutionId(Long executionId) {
        return activityFeedRepository.findFirstByExecutionIdOrderByCreatedAtDesc(executionId).isPresent();
    }

    /**
     * QA-152 안전망: 입력된 execution_id 목록 중 실제 ActivityFeed 가 존재하는 id 만 Set 으로 반환.
     * mission-service 응답에서 mission_execution.is_shared_to_feed 값을 보정할 때 배치로 호출한다.
     */
    public Set<Long> findExecutionIdsWithFeed(java.util.Collection<Long> executionIds) {
        if (executionIds == null || executionIds.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        return new HashSet<>(activityFeedRepository.findExistingExecutionIdsByExecutionIdIn(executionIds));
    }

    /**
     * executionId로 피드 공개범위 조회 (피드 미생성 시 null)
     */
    public String getFeedVisibilityByExecutionId(Long executionId) {
        return activityFeedRepository.findFirstByExecutionIdOrderByCreatedAtDesc(executionId)
            .map(feed -> feed.getVisibility().name())
            .orElse(null);
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
     * 피드 목록 일괄 번역 — 캐시 미스 항목만 모아 Google API 를 목록당 1~2회 호출 (피드별 순차 호출 방지)
     */
    private Map<Long, TranslationInfo> translateFeedsBatch(List<ActivityFeed> feeds, String targetLocale) {
        if (SupportedLocale.DEFAULT.getCode().equals(targetLocale) || feeds.isEmpty()) {
            return Map.of();
        }
        List<TranslationService.BatchItem> items = feeds.stream()
            .map(f -> new TranslationService.BatchItem(f.getId(), f.getTitle(), f.getDescription()))
            .toList();
        return translationService.translateContents(ContentType.FEED, items, targetLocale);
    }

    /**
     * 댓글 목록 일괄 번역 (삭제된 댓글 제외)
     */
    private Map<Long, TranslationInfo> translateCommentsBatch(List<FeedComment> comments, String targetLocale) {
        if (SupportedLocale.DEFAULT.getCode().equals(targetLocale) || comments.isEmpty()) {
            return Map.of();
        }
        List<TranslationService.BatchItem> items = comments.stream()
            .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
            .map(c -> new TranslationService.BatchItem(c.getId(), null, c.getContent()))
            .toList();
        return translationService.translateContents(ContentType.FEED_COMMENT, items, targetLocale);
    }

    /**
     * 배치 번역 결과에서 피드 번역 정보 조회 (없으면 notTranslated)
     */
    private TranslationInfo translationFor(Map<Long, TranslationInfo> translationMap, ActivityFeed feed) {
        return translationMap.getOrDefault(
            feed.getId(), TranslationInfo.notTranslated(SupportedLocale.DEFAULT.getCode()));
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
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "error.feed.not_found"));
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

    /**
     * QA-53: ActivityFeedResponse 들의 imageUrls 를 ActivityFeedImage 테이블에서 일괄 조회해 채운다.
     * 단일 이미지 폴백은 ActivityFeedResponse.from 에서 이미 처리되어 있고,
     * 다중 이미지가 있는 피드만 여기서 실제 sort_order 순으로 덮어쓴다.
     * Page 의 mapping 으로 호출.
     */
    private void enrichWithImageUrls(List<ActivityFeedResponse> responses) {
        if (responses == null || responses.isEmpty()) return;

        List<Long> feedIds = responses.stream().map(ActivityFeedResponse::getId).toList();
        Map<Long, List<String>> imageMap = new HashMap<>();
        for (ActivityFeedImage img : activityFeedImageRepository.findByFeedIdInOrderBySortOrder(feedIds)) {
            imageMap.computeIfAbsent(img.getFeed().getId(), k -> new ArrayList<>()).add(img.getImageUrl());
        }

        for (ActivityFeedResponse r : responses) {
            List<String> urls = imageMap.get(r.getId());
            if (urls != null && !urls.isEmpty()) {
                r.setImageUrls(urls);
                r.setImageUrl(urls.get(0));
            }
        }
    }

    /**
     * LUT-255: 피드의 user_title은 작성/칭호변경 시점의 한국어 스냅샷이라, 한국어 외 locale 요청 시
     * 작성자의 현재 장착 칭호를 배치 조회해 locale에 맞는 조합 칭호명으로 교체한다.
     * (칭호 변경 시 FeedProjectionEventListener가 스냅샷을 갱신하므로 현재 장착 칭호와 의미가 같다)
     */
    private void localizeUserTitles(List<ActivityFeedResponse> responses, String locale) {
        if (responses == null || responses.isEmpty() || locale == null || locale.isBlank()) {
            return;
        }
        String langCode = SupportedLocale.extractLanguageCode(locale);
        if (SupportedLocale.KOREAN.getCode().equals(langCode)) {
            return;
        }
        List<String> userIds = responses.stream()
            .map(ActivityFeedResponse::getUserId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        if (userIds.isEmpty()) {
            return;
        }
        try {
            Map<String, List<UserTitleDto>> titlesByUser =
                gamificationQueryFacadeService.getEquippedTitlesByUserIds(userIds);
            for (ActivityFeedResponse response : responses) {
                List<UserTitleDto> equipped = titlesByUser.get(response.getUserId());
                if (equipped == null || equipped.isEmpty()) {
                    continue;
                }
                String localized = TitleNameUtils.combinedTitleName(equipped, langCode);
                if (localized != null && !localized.isBlank()) {
                    response.setUserTitle(localized);
                }
            }
        } catch (Exception e) {
            log.warn("피드 칭호 다국어 변환 실패 - 스냅샷 유지: {}", e.getMessage());
        }
    }
}
