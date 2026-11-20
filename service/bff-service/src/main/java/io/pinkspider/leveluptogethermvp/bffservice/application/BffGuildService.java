package io.pinkspider.leveluptogethermvp.bffservice.application;

import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildDetailDataResponse;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildDetailDataResponse.PostPageData;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildListDataResponse;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildListDataResponse.FeedPageData;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.GuildListDataResponse.GuildPageData;
import io.pinkspider.leveluptogethermvp.chatservice.application.GuildChatActivityQueryService;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.feedservice.application.FeedQueryService;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildPostService;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildMemberResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostListResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.policy.GuildRecentActivity;
import io.pinkspider.leveluptogethermvp.missionservice.application.GuildMissionActivityQueryService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** BFF (Backend for Frontend) 서비스 - 길드 길드 관련 화면에 필요한 여러 데이터를 한 번에 조회합니다. */
@Service
@Slf4j
public class BffGuildService {

    private final GuildQueryService guildQueryService;
    private final GuildPostService guildPostService;
    private final FeedQueryService feedQueryService;
    // LUT-853: 길드 홈 목록의 "최근 활동"(방금 대화·방금 길드 미션) — 길드 서비스는 채팅·미션을 모르므로 BFF 가 모아 넘긴다
    private final GuildChatActivityQueryService guildChatActivityQueryService;
    private final GuildMissionActivityQueryService guildMissionActivityQueryService;
    private final Executor bffExecutor;
    private final int recentChatMinutes;
    private final int recentMissionMinutes;

    public BffGuildService(
            GuildQueryService guildQueryService,
            GuildPostService guildPostService,
            FeedQueryService feedQueryService,
            GuildChatActivityQueryService guildChatActivityQueryService,
            GuildMissionActivityQueryService guildMissionActivityQueryService,
            @Qualifier("bffExecutor") Executor bffExecutor,
            @Value("${guild.home.recent-chat-minutes:30}") int recentChatMinutes,
            @Value("${guild.home.recent-mission-minutes:30}") int recentMissionMinutes) {
        this.guildQueryService = guildQueryService;
        this.guildPostService = guildPostService;
        this.feedQueryService = feedQueryService;
        this.guildChatActivityQueryService = guildChatActivityQueryService;
        this.guildMissionActivityQueryService = guildMissionActivityQueryService;
        this.bffExecutor = bffExecutor;
        this.recentChatMinutes = recentChatMinutes;
        this.recentMissionMinutes = recentMissionMinutes;
    }

    /**
     * 길드 상세 화면에 필요한 모든 데이터를 한 번에 조회합니다.
     *
     * @param guildId 길드 ID
     * @param userId 현재 로그인한 사용자 ID
     * @param postPage 게시글 페이지 번호 (기본: 0)
     * @param postSize 게시글 페이지 크기 (기본: 20)
     * @return GuildDetailDataResponse 길드 상세 데이터
     */
    public GuildDetailDataResponse getGuildDetail(
            Long guildId, String userId, int postPage, int postSize) {
        log.info("BFF getGuildDetail called: guildId={}, userId={}", guildId, userId);

        // 병렬로 모든 데이터 조회
        CompletableFuture<GuildResponse> guildFuture =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return guildQueryService.getGuild(guildId, userId);
                            } catch (Exception e) {
                                log.error("Failed to fetch guild", e);
                                return null;
                            }
                        },
                        bffExecutor);

        CompletableFuture<List<GuildMemberResponse>> membersFuture =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return guildQueryService.getGuildMembers(guildId, userId);
                            } catch (Exception e) {
                                log.error("Failed to fetch guild members", e);
                                return Collections.emptyList();
                            }
                        },
                        bffExecutor);

        CompletableFuture<PostPageData> postsFuture =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                Page<GuildPostListResponse> postsPage =
                                        guildPostService.getPosts(
                                                guildId,
                                                userId,
                                                PageRequest.of(postPage, postSize),
                                                null);
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
                        },
                        bffExecutor);

        // 모든 결과 취합
        CompletableFuture.allOf(guildFuture, membersFuture, postsFuture).join();

        GuildResponse guild = guildFuture.join();
        List<GuildMemberResponse> members = membersFuture.join();

        // 멤버 여부 및 역할 확인 (비인증 시 비멤버)
        boolean isMember =
                userId != null && members.stream().anyMatch(m -> m.getUserId().equals(userId));
        String memberRole =
                userId != null
                        ? members.stream()
                                .filter(m -> m.getUserId().equals(userId))
                                .findFirst()
                                .map(m -> m.getRole().name())
                                .orElse(null)
                        : null;

        GuildDetailDataResponse response =
                GuildDetailDataResponse.builder()
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
    public GuildListDataResponse getGuildList(
            String userId, int recommendedGuildSize, int activityFeedSize) {
        return getGuildList(userId, recommendedGuildSize, activityFeedSize, null);
    }

    /**
     * LUT-277: 길드 홈 카드 카테고리·공지·활동피드에 locale 적용. LUT-853: 나의 길드·추천 길드는 서버가 정한 순서(최근 활동 → 레벨, 나의 길드는
     * 마스터인 길드 먼저)로 내리고 길드마다 recent_chat·recent_mission 을 채운다.
     */
    public GuildListDataResponse getGuildList(
            String userId, int recommendedGuildSize, int activityFeedSize, String locale) {
        log.info("BFF getGuildList called: userId={}, locale={}", userId, locale);

        // LUT-853: 두 목록의 순서·표시에 쓰는 최근 활동을 먼저 모은다 (실패하면 활동 없음으로 — 목록은 그대로 내린다)
        GuildRecentActivity recentActivity = loadRecentActivity();

        // 먼저 내 길드 목록 조회 (비인증 시 빈 리스트)
        List<GuildResponse> myGuilds;
        if (userId != null) {
            try {
                myGuilds = guildQueryService.getMyGuildsForHome(userId, locale, recentActivity);
            } catch (Exception e) {
                log.error("Failed to fetch my guilds", e);
                myGuilds = Collections.emptyList();
            }
        } else {
            myGuilds = Collections.emptyList();
        }

        boolean hasGuild = !myGuilds.isEmpty();
        // 모든 내 길드의 ID 목록 (카테고리별 1개 길드 정책으로 여러 개 가능)
        List<Long> myGuildIds = myGuilds.stream().map(GuildResponse::getId).toList();

        // 병렬로 나머지 데이터 조회
        final List<GuildResponse> finalMyGuilds = myGuilds;

        CompletableFuture<GuildPageData> recommendedGuildsFuture =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                Page<GuildResponse> guildsPage =
                                        guildQueryService.getRecommendedGuilds(
                                                userId,
                                                PageRequest.of(0, recommendedGuildSize),
                                                locale,
                                                recentActivity);
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
                        },
                        bffExecutor);

        // 모든 내 길드의 공지사항 병합 조회
        CompletableFuture<List<GuildPostListResponse>> noticesFuture =
                CompletableFuture.supplyAsync(
                        () -> {
                            if (myGuildIds.isEmpty()) {
                                return Collections.emptyList();
                            }
                            try {
                                // 모든 내 길드의 공지사항을 병합
                                return myGuildIds.stream()
                                        .flatMap(
                                                guildId -> {
                                                    try {
                                                        return guildPostService
                                                                .getNotices(guildId, userId, locale)
                                                                .stream();
                                                    } catch (Exception e) {
                                                        log.warn(
                                                                "Failed to fetch notices for guild"
                                                                        + " {}: {}",
                                                                guildId,
                                                                e.getMessage());
                                                        return java.util.stream.Stream.empty();
                                                    }
                                                })
                                        .sorted(
                                                (a, b) ->
                                                        b.getCreatedAt()
                                                                .compareTo(
                                                                        a.getCreatedAt())) // 최신순 정렬
                                        .limit(10) // 최대 10개
                                        .toList();
                            } catch (Exception e) {
                                log.error("Failed to fetch guild notices", e);
                                return Collections.emptyList();
                            }
                        },
                        bffExecutor);

        // 모든 내 길드의 활동 피드 병합 조회
        CompletableFuture<FeedPageData> activityFeedsFuture =
                CompletableFuture.supplyAsync(
                        () -> {
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
                                List<ActivityFeedResponse> allFeeds =
                                        myGuildIds.stream()
                                                .flatMap(
                                                        guildId -> {
                                                            try {
                                                                return feedQueryService
                                                                        .getGuildFeeds(
                                                                                guildId,
                                                                                userId,
                                                                                0,
                                                                                activityFeedSize,
                                                                                locale)
                                                                        .getContent()
                                                                        .stream();
                                                            } catch (Exception e) {
                                                                log.warn(
                                                                        "Failed to fetch feeds for"
                                                                                + " guild {}: {}",
                                                                        guildId,
                                                                        e.getMessage());
                                                                return java.util.stream.Stream
                                                                        .empty();
                                                            }
                                                        })
                                                .sorted(
                                                        (a, b) ->
                                                                b.getCreatedAt()
                                                                        .compareTo(
                                                                                a
                                                                                        .getCreatedAt())) // 최신순 정렬
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
                        },
                        bffExecutor);

        // 모든 결과 취합
        CompletableFuture.allOf(recommendedGuildsFuture, noticesFuture, activityFeedsFuture).join();

        GuildListDataResponse response =
                GuildListDataResponse.builder()
                        .myGuilds(finalMyGuilds)
                        .recommendedGuilds(recommendedGuildsFuture.join())
                        .guildNotices(noticesFuture.join())
                        .guildActivityFeeds(activityFeedsFuture.join())
                        .guildJoined(hasGuild)
                        .build();

        log.info("BFF getGuildList completed: userId={}, hasGuild={}", userId, hasGuild);
        return response;
    }

    /**
     * LUT-853: 길드 홈 목록의 "최근 활동" — 방금 대화(단체채팅, 기본 30분)와 방금 길드 미션(시작·완료, 기본 30분)이 있었던 길드. 두 조회를 함께
     * 돌리고, 한쪽이 실패하면 그쪽만 비운다(목록·다른 쪽 표시는 그대로).
     */
    private GuildRecentActivity loadRecentActivity() {
        LocalDateTime now = LocalDateTime.now();
        CompletableFuture<Set<Long>> chatFuture =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return guildChatActivityQueryService.findGuildIdsWithRecentChat(
                                        now.minusMinutes(recentChatMinutes));
                            } catch (Exception e) {
                                log.warn("Failed to fetch recent guild chat activity", e);
                                return Set.<Long>of();
                            }
                        },
                        bffExecutor);
        CompletableFuture<Set<Long>> missionFuture =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return guildMissionActivityQueryService
                                        .findGuildIdsWithRecentActivity(
                                                now.minusMinutes(recentMissionMinutes));
                            } catch (Exception e) {
                                log.warn("Failed to fetch recent guild mission activity", e);
                                return Set.<Long>of();
                            }
                        },
                        bffExecutor);
        return new GuildRecentActivity(chatFuture.join(), missionFuture.join());
    }
}
