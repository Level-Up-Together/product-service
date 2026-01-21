package io.pinkspider.leveluptogethermvp.bffservice.application;

import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildDetailDataResponse;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildDetailDataResponse.PostPageData;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildListDataResponse;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildListDataResponse.FeedPageData;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildListDataResponse.GuildPageData;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildPostService;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildMemberResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostListResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * BFF (Backend for Frontend) 서비스 - 길드
 * 길드 관련 화면에 필요한 여러 데이터를 한 번에 조회합니다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BffGuildService {

    private final GuildService guildService;
    private final GuildPostService guildPostService;
    private final ActivityFeedService activityFeedService;

    /**
     * 길드 상세 화면에 필요한 모든 데이터를 한 번에 조회합니다.
     *
     * @param guildId 길드 ID
     * @param userId 현재 로그인한 사용자 ID
     * @param postPage 게시글 페이지 번호 (기본: 0)
     * @param postSize 게시글 페이지 크기 (기본: 20)
     * @return GuildDetailDataResponse 길드 상세 데이터
     */
    public GuildDetailDataResponse getGuildDetail(Long guildId, String userId, int postPage, int postSize) {
        log.info("BFF getGuildDetail called: guildId={}, userId={}", guildId, userId);

        // 병렬로 모든 데이터 조회
        CompletableFuture<GuildResponse> guildFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return guildService.getGuild(guildId, userId);
            } catch (Exception e) {
                log.error("Failed to fetch guild", e);
                return null;
            }
        });

        CompletableFuture<List<GuildMemberResponse>> membersFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return guildService.getGuildMembers(guildId, userId);
            } catch (Exception e) {
                log.error("Failed to fetch guild members", e);
                return Collections.emptyList();
            }
        });

        CompletableFuture<PostPageData> postsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Page<GuildPostListResponse> postsPage = guildPostService.getPosts(
                    guildId, userId, PageRequest.of(postPage, postSize));
                return PostPageData.builder()
                    .content(postsPage.getContent())
                    .page(postsPage.getNumber())
                    .size(postsPage.getSize())
                    .totalElements(postsPage.getTotalElements())
                    .totalPages(postsPage.getTotalPages())
                    .build();
            } catch (Exception e) {
                log.error("Failed to fetch guild posts", e);
                return PostPageData.builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(postSize)
                    .totalElements(0)
                    .totalPages(0)
                    .build();
            }
        });

        // 모든 결과 취합
        CompletableFuture.allOf(guildFuture, membersFuture, postsFuture).join();

        GuildResponse guild = guildFuture.join();
        List<GuildMemberResponse> members = membersFuture.join();

        // 멤버 여부 및 역할 확인
        boolean isMember = members.stream()
            .anyMatch(m -> m.getUserId().equals(userId));
        String memberRole = members.stream()
            .filter(m -> m.getUserId().equals(userId))
            .findFirst()
            .map(m -> m.getRole().name())
            .orElse(null);

        GuildDetailDataResponse response = GuildDetailDataResponse.builder()
            .guild(guild)
            .members(members)
            .posts(postsFuture.join())
            .member(isMember)
            .memberRole(memberRole)
            .build();

        log.info("BFF getGuildDetail completed: guildId={}", guildId);
        return response;
    }

    /**
     * 길드 목록 화면에 필요한 모든 데이터를 한 번에 조회합니다.
     *
     * @param userId 현재 로그인한 사용자 ID
     * @param recommendedGuildSize 추천 길드 조회 개수 (기본: 10)
     * @param activityFeedSize 활동 피드 조회 개수 (기본: 10)
     * @return GuildListDataResponse 길드 목록 데이터
     */
    public GuildListDataResponse getGuildList(String userId, int recommendedGuildSize, int activityFeedSize) {
        log.info("BFF getGuildList called: userId={}", userId);

        // 먼저 내 길드 목록 조회
        List<GuildResponse> myGuilds;
        try {
            myGuilds = guildService.getMyGuilds(userId);
        } catch (Exception e) {
            log.error("Failed to fetch my guilds", e);
            myGuilds = Collections.emptyList();
        }

        boolean hasGuild = !myGuilds.isEmpty();
        // 모든 내 길드의 ID 목록 (카테고리별 1개 길드 정책으로 여러 개 가능)
        List<Long> myGuildIds = myGuilds.stream().map(GuildResponse::getId).toList();

        // 병렬로 나머지 데이터 조회
        final List<GuildResponse> finalMyGuilds = myGuilds;

        CompletableFuture<GuildPageData> recommendedGuildsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Page<GuildResponse> guildsPage = guildService.getPublicGuilds(
                    userId, PageRequest.of(0, recommendedGuildSize));
                return GuildPageData.builder()
                    .content(guildsPage.getContent())
                    .page(guildsPage.getNumber())
                    .size(guildsPage.getSize())
                    .totalElements(guildsPage.getTotalElements())
                    .totalPages(guildsPage.getTotalPages())
                    .build();
            } catch (Exception e) {
                log.error("Failed to fetch recommended guilds", e);
                return GuildPageData.builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(recommendedGuildSize)
                    .totalElements(0)
                    .totalPages(0)
                    .build();
            }
        });

        // 모든 내 길드의 공지사항 병합 조회
        CompletableFuture<List<GuildPostListResponse>> noticesFuture = CompletableFuture.supplyAsync(() -> {
            if (myGuildIds.isEmpty()) {
                return Collections.emptyList();
            }
            try {
                // 모든 내 길드의 공지사항을 병합
                return myGuildIds.stream()
                    .flatMap(guildId -> {
                        try {
                            return guildPostService.getNotices(guildId, userId).stream();
                        } catch (Exception e) {
                            log.warn("Failed to fetch notices for guild {}: {}", guildId, e.getMessage());
                            return java.util.stream.Stream.empty();
                        }
                    })
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())) // 최신순 정렬
                    .limit(10) // 최대 10개
                    .toList();
            } catch (Exception e) {
                log.error("Failed to fetch guild notices", e);
                return Collections.emptyList();
            }
        });

        // 모든 내 길드의 활동 피드 병합 조회
        CompletableFuture<FeedPageData> activityFeedsFuture = CompletableFuture.supplyAsync(() -> {
            if (myGuildIds.isEmpty()) {
                return FeedPageData.builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(activityFeedSize)
                    .totalElements(0)
                    .totalPages(0)
                    .build();
            }
            try {
                // 모든 내 길드의 활동 피드를 병합
                List<ActivityFeedResponse> allFeeds = myGuildIds.stream()
                    .flatMap(guildId -> {
                        try {
                            return activityFeedService.getGuildFeeds(guildId, userId, 0, activityFeedSize)
                                .getContent().stream();
                        } catch (Exception e) {
                            log.warn("Failed to fetch feeds for guild {}: {}", guildId, e.getMessage());
                            return java.util.stream.Stream.empty();
                        }
                    })
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())) // 최신순 정렬
                    .limit(activityFeedSize)
                    .toList();

                return FeedPageData.builder()
                    .content(allFeeds)
                    .page(0)
                    .size(activityFeedSize)
                    .totalElements(allFeeds.size())
                    .totalPages(1)
                    .build();
            } catch (Exception e) {
                log.error("Failed to fetch guild activity feeds", e);
                return FeedPageData.builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(activityFeedSize)
                    .totalElements(0)
                    .totalPages(0)
                    .build();
            }
        });

        // 모든 결과 취합
        CompletableFuture.allOf(recommendedGuildsFuture, noticesFuture, activityFeedsFuture).join();

        GuildListDataResponse response = GuildListDataResponse.builder()
            .myGuilds(finalMyGuilds)
            .recommendedGuilds(recommendedGuildsFuture.join())
            .guildNotices(noticesFuture.join())
            .guildActivityFeeds(activityFeedsFuture.join())
            .guildJoined(hasGuild)
            .build();

        log.info("BFF getGuildList completed: userId={}, hasGuild={}", userId, hasGuild);
        return response;
    }
}
