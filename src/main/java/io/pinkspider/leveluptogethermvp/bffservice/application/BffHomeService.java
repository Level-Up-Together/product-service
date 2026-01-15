package io.pinkspider.leveluptogethermvp.bffservice.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpData;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.application.SeasonRankingService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.HomeDataResponse;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.HomeDataResponse.FeedPageData;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.HomeDataResponse.GuildPageData;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.noticeservice.api.dto.NoticeResponse;
import io.pinkspider.leveluptogethermvp.noticeservice.application.NoticeService;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.MvpGuildResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.TodayPlayerResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.application.HomeService;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * BFF (Backend for Frontend) 서비스
 * 홈 화면에 필요한 여러 데이터를 한 번에 조회합니다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BffHomeService {

    private final ActivityFeedService activityFeedService;
    private final HomeService homeService;
    private final MissionCategoryService missionCategoryService;
    private final GuildService guildService;
    private final NoticeService noticeService;
    private final SeasonRankingService seasonRankingService;
    private final AchievementService achievementService;

    /**
     * 홈 화면에 필요한 모든 데이터를 한 번에 조회합니다.
     *
     * @param userId 현재 로그인한 사용자 ID
     * @param categoryId 카테고리 ID (선택적, null이면 전체)
     * @param feedPage 피드 페이지 번호 (기본: 0)
     * @param feedSize 피드 페이지 크기 (기본: 20)
     * @param publicGuildSize 공개 길드 조회 개수 (기본: 5)
     * @return HomeDataResponse 홈 화면 데이터
     */
    public HomeDataResponse getHomeData(String userId, Long categoryId, int feedPage, int feedSize, int publicGuildSize) {
        return getHomeData(userId, categoryId, feedPage, feedSize, publicGuildSize, null);
    }

    /**
     * 홈 화면에 필요한 모든 데이터를 한 번에 조회합니다. (다국어 지원)
     *
     * @param userId 현재 로그인한 사용자 ID
     * @param categoryId 카테고리 ID (선택적, null이면 전체)
     * @param feedPage 피드 페이지 번호 (기본: 0)
     * @param feedSize 피드 페이지 크기 (기본: 20)
     * @param publicGuildSize 공개 길드 조회 개수 (기본: 5)
     * @param locale Accept-Language 헤더에서 추출한 locale (null이면 기본 한국어)
     * @return HomeDataResponse 홈 화면 데이터
     */
    public HomeDataResponse getHomeData(String userId, Long categoryId, int feedPage, int feedSize, int publicGuildSize, String locale) {
        log.info("BFF getHomeData called: userId={}, categoryId={}, feedPage={}, feedSize={}, locale={}", userId, categoryId, feedPage, feedSize, locale);

        // 업적 동기화 - 기존 유저 업적 소급 적용 및 자동 보상 수령
        try {
            log.info("업적 동기화 호출 시작: userId={}", userId);
            achievementService.syncUserAchievements(userId);
            log.info("업적 동기화 호출 완료: userId={}", userId);
        } catch (Exception e) {
            log.error("업적 동기화 호출 중 오류: userId={}, error={}", userId, e.getMessage(), e);
        }

        // 병렬로 모든 데이터 조회
        CompletableFuture<FeedPageData> feedsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Page<ActivityFeedResponse> feedPage1;
                if (categoryId != null) {
                    // 카테고리별 피드 조회 (하이브리드)
                    feedPage1 = activityFeedService.getPublicFeedsByCategory(categoryId, userId, feedPage, feedSize);
                } else {
                    // 전체 피드 조회
                    feedPage1 = activityFeedService.getPublicFeeds(userId, feedPage, feedSize);
                }
                return FeedPageData.builder()
                    .content(feedPage1.getContent())
                    .page(feedPage1.getNumber())
                    .size(feedPage1.getSize())
                    .totalElements(feedPage1.getTotalElements())
                    .totalPages(feedPage1.getTotalPages())
                    .build();
            } catch (Exception e) {
                log.error("Failed to fetch feeds", e);
                return FeedPageData.builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(feedSize)
                    .totalElements(0)
                    .totalPages(0)
                    .build();
            }
        });

        CompletableFuture<List<TodayPlayerResponse>> rankingsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                if (categoryId != null) {
                    // 카테고리별 MVP 조회 (하이브리드) - 로컬라이즈된 칭호
                    return homeService.getTodayPlayersByCategory(categoryId, locale);
                } else {
                    // 전체 MVP 조회 - 로컬라이즈된 칭호
                    return homeService.getTodayPlayers(locale);
                }
            } catch (Exception e) {
                log.error("Failed to fetch rankings", e);
                return Collections.emptyList();
            }
        });

        CompletableFuture<List<MvpGuildResponse>> mvpGuildsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return homeService.getMvpGuilds();
            } catch (Exception e) {
                log.error("Failed to fetch MVP guilds", e);
                return Collections.emptyList();
            }
        });

        CompletableFuture<List<MissionCategoryResponse>> categoriesFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return missionCategoryService.getActiveCategories();
            } catch (Exception e) {
                log.error("Failed to fetch categories", e);
                return Collections.emptyList();
            }
        });

        CompletableFuture<List<GuildResponse>> myGuildsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return guildService.getMyGuilds(userId);
            } catch (Exception e) {
                log.error("Failed to fetch my guilds", e);
                return Collections.emptyList();
            }
        });

        CompletableFuture<GuildPageData> publicGuildsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                if (categoryId != null) {
                    // 카테고리별 공개 길드 조회 (하이브리드)
                    List<GuildResponse> guilds = guildService.getPublicGuildsByCategory(categoryId);
                    return GuildPageData.builder()
                        .content(guilds)
                        .page(0)
                        .size(guilds.size())
                        .totalElements(guilds.size())
                        .totalPages(1)
                        .build();
                } else {
                    // 전체 공개 길드 조회
                    Page<GuildResponse> guildPage = guildService.getPublicGuilds(PageRequest.of(0, publicGuildSize));
                    return GuildPageData.builder()
                        .content(guildPage.getContent())
                        .page(guildPage.getNumber())
                        .size(guildPage.getSize())
                        .totalElements(guildPage.getTotalElements())
                        .totalPages(guildPage.getTotalPages())
                        .build();
                }
            } catch (Exception e) {
                log.error("Failed to fetch public guilds", e);
                return GuildPageData.builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(publicGuildSize)
                    .totalElements(0)
                    .totalPages(0)
                    .build();
            }
        });

        CompletableFuture<List<NoticeResponse>> noticesFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return noticeService.getActiveNotices();
            } catch (Exception e) {
                log.error("Failed to fetch notices", e);
                return Collections.emptyList();
            }
        });

        CompletableFuture<Optional<SeasonMvpData>> seasonMvpFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return seasonRankingService.getSeasonMvpData(locale);
            } catch (Exception e) {
                log.error("Failed to fetch season MVP data", e);
                return Optional.empty();
            }
        });

        // 모든 결과 취합
        CompletableFuture.allOf(
            feedsFuture, rankingsFuture, mvpGuildsFuture, categoriesFuture,
            myGuildsFuture, publicGuildsFuture, noticesFuture, seasonMvpFuture
        ).join();

        Optional<SeasonMvpData> seasonMvpData = seasonMvpFuture.join();

        HomeDataResponse response = HomeDataResponse.builder()
            .feeds(feedsFuture.join())
            .rankings(rankingsFuture.join())
            .mvpGuilds(mvpGuildsFuture.join())
            .categories(categoriesFuture.join())
            .myGuilds(myGuildsFuture.join())
            .publicGuilds(publicGuildsFuture.join())
            .notices(noticesFuture.join())
            .currentSeason(seasonMvpData.map(SeasonMvpData::currentSeason).orElse(null))
            .seasonMvpPlayers(seasonMvpData.map(SeasonMvpData::seasonMvpPlayers).orElse(Collections.emptyList()))
            .seasonMvpGuilds(seasonMvpData.map(SeasonMvpData::seasonMvpGuilds).orElse(Collections.emptyList()))
            .build();

        log.info("BFF getHomeData completed: userId={}, categoryId={}, hasActiveSeason={}", userId, categoryId, seasonMvpData.isPresent());
        return response;
    }
}
