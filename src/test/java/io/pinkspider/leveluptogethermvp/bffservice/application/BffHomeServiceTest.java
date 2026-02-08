package io.pinkspider.leveluptogethermvp.bffservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpData;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpGuildResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpPlayerResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.application.SeasonRankingService;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.enums.SeasonStatus;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.HomeDataResponse;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.noticeservice.api.dto.NoticeResponse;
import io.pinkspider.leveluptogethermvp.noticeservice.application.NoticeService;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.TodayPlayerResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.application.HomeService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class BffHomeServiceTest {

    @Mock
    private ActivityFeedService activityFeedService;

    @Mock
    private HomeService homeService;

    @Mock
    private MissionCategoryService missionCategoryService;

    @Mock
    private GuildQueryService guildQueryService;

    @Mock
    private NoticeService noticeService;

    @Mock
    private SeasonRankingService seasonRankingService;

    @Mock
    private io.pinkspider.leveluptogethermvp.gamificationservice.event.application.EventService eventService;

    @Mock
    private io.pinkspider.leveluptogethermvp.userservice.achievement.application.AchievementService achievementService;

    // 테스트용 동기 Executor - CompletableFuture가 즉시 실행되도록 함
    private final Executor directExecutor = Runnable::run;

    private BffHomeService bffHomeService;

    private String testUserId;
    private ActivityFeedResponse testFeedResponse;
    private TodayPlayerResponse testPlayerResponse;
    private MissionCategoryResponse testCategoryResponse;
    private GuildResponse testGuildResponse;
    private NoticeResponse testNoticeResponse;
    private SeasonResponse testSeasonResponse;
    private SeasonMvpPlayerResponse testSeasonMvpPlayerResponse;
    private SeasonMvpGuildResponse testSeasonMvpGuildResponse;
    private SeasonMvpData testSeasonMvpData;

    @BeforeEach
    void setUp() {
        // BffHomeService 수동 생성 (Executor 주입을 위해)
        bffHomeService = new BffHomeService(
            activityFeedService,
            homeService,
            missionCategoryService,
            guildQueryService,
            noticeService,
            eventService,
            seasonRankingService,
            achievementService,
            directExecutor
        );

        testUserId = "test-user-id";

        testFeedResponse = ActivityFeedResponse.builder()
            .id(1L)
            .userId(testUserId)
            .userNickname("테스터")
            .activityType(ActivityType.MISSION_COMPLETED)
            .activityTypeDisplayName("미션 완료")
            .category("MISSION")
            .title("미션 완료!")
            .description("테스트 미션을 완료했습니다.")
            .visibility(FeedVisibility.PUBLIC)
            .likeCount(5)
            .commentCount(2)
            .likedByMe(false)
            .createdAt(LocalDateTime.now())
            .build();

        testPlayerResponse = TodayPlayerResponse.builder()
            .userId(testUserId)
            .nickname("테스터")
            .profileImageUrl("https://example.com/profile.jpg")
            .level(5)
            .title("초보 모험가")
            .earnedExp(100L)
            .rank(1)
            .build();

        testCategoryResponse = MissionCategoryResponse.builder()
            .id(1L)
            .name("자기계발")
            .icon("📚")
            .isActive(true)
            .build();

        testGuildResponse = GuildResponse.builder()
            .id(1L)
            .name("테스트 길드")
            .description("테스트 길드 설명")
            .visibility(GuildVisibility.PUBLIC)
            .masterId(testUserId)
            .maxMembers(50)
            .currentMemberCount(10)
            .currentLevel(1)
            .currentExp(100)
            .totalExp(100)
            .categoryId(1L)
            .categoryName("자기계발")
            .categoryIcon("📚")
            .createdAt(LocalDateTime.now())
            .build();

        testNoticeResponse = NoticeResponse.builder()
            .id(1L)
            .title("시스템 공지")
            .content("테스트 공지 내용입니다.")
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .build();

        testSeasonResponse = new SeasonResponse(
            1L,
            "2024 윈터 시즌",
            "겨울 시즌 이벤트입니다.",
            LocalDateTime.now().minusDays(7),
            LocalDateTime.now().plusDays(7),
            1L,
            "윈터 마스터",
            SeasonStatus.ACTIVE,
            "진행중"
        );

        testSeasonMvpPlayerResponse = SeasonMvpPlayerResponse.of(
            testUserId,
            "테스터",
            "https://example.com/profile.jpg",
            5,
            "초보 모험가",
            null,
            null,
            null,
            null,
            null,
            1000L,
            1
        );

        testSeasonMvpGuildResponse = SeasonMvpGuildResponse.of(
            1L,
            "테스트 길드",
            "https://example.com/guild.jpg",
            3,
            10,
            5000L,
            1
        );

        testSeasonMvpData = SeasonMvpData.of(
            testSeasonResponse,
            List.of(testSeasonMvpPlayerResponse),
            List.of(testSeasonMvpGuildResponse)
        );
    }

    @Nested
    @DisplayName("홈 데이터 조회 테스트")
    class GetHomeDataTest {

        @Test
        @DisplayName("모든 데이터를 정상적으로 조회한다")
        void getHomeData_success() {
            // given
            Page<ActivityFeedResponse> feedPage = new PageImpl<>(
                List.of(testFeedResponse), PageRequest.of(0, 20), 1
            );
            Page<GuildResponse> guildPage = new PageImpl<>(
                List.of(testGuildResponse), PageRequest.of(0, 5), 1
            );

            when(activityFeedService.getPublicFeeds(anyString(), anyInt(), anyInt())).thenReturn(feedPage);
            when(homeService.getTodayPlayers(any())).thenReturn(List.of(testPlayerResponse));
            when(homeService.getMvpGuilds()).thenReturn(Collections.emptyList());
            when(missionCategoryService.getActiveCategories()).thenReturn(List.of(testCategoryResponse));
            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(List.of(testGuildResponse));
            when(guildQueryService.getPublicGuilds(any(), any())).thenReturn(guildPage);
            when(noticeService.getActiveNotices()).thenReturn(List.of(testNoticeResponse));
            when(seasonRankingService.getSeasonMvpData(any())).thenReturn(Optional.of(testSeasonMvpData));

            // when - categoryId = null (전체 조회)
            HomeDataResponse response = bffHomeService.getHomeData(testUserId, null, 0, 20, 5, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getFeeds().getContent()).hasSize(1);
            assertThat(response.getRankings()).hasSize(1);
            assertThat(response.getCategories()).hasSize(1);
            assertThat(response.getMyGuilds()).hasSize(1);
            assertThat(response.getPublicGuilds().getContent()).hasSize(1);
            assertThat(response.getNotices()).hasSize(1);
            // 시즌 데이터 검증
            assertThat(response.getCurrentSeason()).isNotNull();
            assertThat(response.getCurrentSeason().title()).isEqualTo("2024 윈터 시즌");
            assertThat(response.getSeasonMvpPlayers()).hasSize(1);
            assertThat(response.getSeasonMvpGuilds()).hasSize(1);
        }

        @Test
        @DisplayName("카테고리별 필터링 데이터를 정상적으로 조회한다")
        void getHomeData_withCategoryFilter_success() {
            // given
            Long categoryId = 1L;
            Page<ActivityFeedResponse> feedPage = new PageImpl<>(
                List.of(testFeedResponse), PageRequest.of(0, 20), 1
            );

            when(activityFeedService.getPublicFeedsByCategory(eq(categoryId), anyString(), anyInt(), anyInt())).thenReturn(feedPage);
            when(homeService.getTodayPlayersByCategory(eq(categoryId), any())).thenReturn(List.of(testPlayerResponse));
            when(missionCategoryService.getActiveCategories()).thenReturn(List.of(testCategoryResponse));
            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(List.of(testGuildResponse));
            when(guildQueryService.getPublicGuildsByCategory(anyString(), eq(categoryId))).thenReturn(List.of(testGuildResponse));
            when(noticeService.getActiveNotices()).thenReturn(List.of(testNoticeResponse));

            // when
            HomeDataResponse response = bffHomeService.getHomeData(testUserId, categoryId, 0, 20, 5, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getFeeds().getContent()).hasSize(1);
            assertThat(response.getRankings()).hasSize(1);
            assertThat(response.getCategories()).hasSize(1);
            assertThat(response.getMyGuilds()).hasSize(1);
            assertThat(response.getPublicGuilds().getContent()).hasSize(1);
            assertThat(response.getNotices()).hasSize(1);
        }

        @Test
        @DisplayName("카테고리별 오늘의 플레이어 조회 실패 시 빈 목록 반환")
        void getHomeData_withCategory_rankingsFetchFailed() {
            // given
            Long categoryId = 1L;
            Page<ActivityFeedResponse> feedPage = new PageImpl<>(List.of(testFeedResponse));

            when(activityFeedService.getPublicFeedsByCategory(eq(categoryId), anyString(), anyInt(), anyInt())).thenReturn(feedPage);
            when(homeService.getTodayPlayersByCategory(eq(categoryId), any())).thenThrow(new RuntimeException("랭킹 조회 실패"));
            when(missionCategoryService.getActiveCategories()).thenReturn(List.of(testCategoryResponse));
            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(List.of(testGuildResponse));
            when(guildQueryService.getPublicGuildsByCategory(anyString(), eq(categoryId))).thenReturn(List.of(testGuildResponse));
            when(noticeService.getActiveNotices()).thenReturn(List.of(testNoticeResponse));

            // when
            HomeDataResponse response = bffHomeService.getHomeData(testUserId, categoryId, 0, 20, 5, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getRankings()).isEmpty();
            assertThat(response.getFeeds().getContent()).hasSize(1);
        }

        @Test
        @DisplayName("카테고리별 공개 길드 조회 실패 시 빈 목록 반환")
        void getHomeData_withCategory_publicGuildsFetchFailed() {
            // given
            Long categoryId = 1L;
            Page<ActivityFeedResponse> feedPage = new PageImpl<>(List.of(testFeedResponse));

            when(activityFeedService.getPublicFeedsByCategory(eq(categoryId), anyString(), anyInt(), anyInt())).thenReturn(feedPage);
            when(homeService.getTodayPlayersByCategory(eq(categoryId), any())).thenReturn(List.of(testPlayerResponse));
            when(missionCategoryService.getActiveCategories()).thenReturn(List.of(testCategoryResponse));
            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(List.of(testGuildResponse));
            when(guildQueryService.getPublicGuildsByCategory(anyString(), eq(categoryId))).thenThrow(new RuntimeException("길드 조회 실패"));
            when(noticeService.getActiveNotices()).thenReturn(List.of(testNoticeResponse));

            // when
            HomeDataResponse response = bffHomeService.getHomeData(testUserId, categoryId, 0, 20, 5, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getPublicGuilds().getContent()).isEmpty();
            assertThat(response.getMyGuilds()).hasSize(1);
        }

        @Test
        @DisplayName("피드 조회 실패 시 빈 목록 반환")
        void getHomeData_feedsFetchFailed() {
            // given
            Page<GuildResponse> guildPage = new PageImpl<>(Collections.emptyList());

            when(activityFeedService.getPublicFeeds(anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("피드 조회 실패"));
            when(homeService.getTodayPlayers(any())).thenReturn(List.of(testPlayerResponse));
            when(missionCategoryService.getActiveCategories()).thenReturn(List.of(testCategoryResponse));
            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(List.of(testGuildResponse));
            when(guildQueryService.getPublicGuilds(any(), any())).thenReturn(guildPage);
            when(noticeService.getActiveNotices()).thenReturn(List.of(testNoticeResponse));

            // when
            HomeDataResponse response = bffHomeService.getHomeData(testUserId, null, 0, 20, 5, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getFeeds().getContent()).isEmpty();
            // 다른 데이터는 정상 조회
            assertThat(response.getRankings()).hasSize(1);
        }

        @Test
        @DisplayName("랭킹 조회 실패 시 빈 목록 반환")
        void getHomeData_rankingsFetchFailed() {
            // given
            Page<ActivityFeedResponse> feedPage = new PageImpl<>(List.of(testFeedResponse));
            Page<GuildResponse> guildPage = new PageImpl<>(Collections.emptyList());

            when(activityFeedService.getPublicFeeds(anyString(), anyInt(), anyInt())).thenReturn(feedPage);
            when(homeService.getTodayPlayers(any())).thenThrow(new RuntimeException("랭킹 조회 실패"));
            when(missionCategoryService.getActiveCategories()).thenReturn(List.of(testCategoryResponse));
            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(List.of(testGuildResponse));
            when(guildQueryService.getPublicGuilds(any(), any())).thenReturn(guildPage);
            when(noticeService.getActiveNotices()).thenReturn(List.of(testNoticeResponse));

            // when
            HomeDataResponse response = bffHomeService.getHomeData(testUserId, null, 0, 20, 5, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getRankings()).isEmpty();
            // 다른 데이터는 정상 조회
            assertThat(response.getFeeds().getContent()).hasSize(1);
        }

        @Test
        @DisplayName("카테고리 조회 실패 시 빈 목록 반환")
        void getHomeData_categoriesFetchFailed() {
            // given
            Page<ActivityFeedResponse> feedPage = new PageImpl<>(List.of(testFeedResponse));
            Page<GuildResponse> guildPage = new PageImpl<>(Collections.emptyList());

            when(activityFeedService.getPublicFeeds(anyString(), anyInt(), anyInt())).thenReturn(feedPage);
            when(homeService.getTodayPlayers(any())).thenReturn(List.of(testPlayerResponse));
            when(missionCategoryService.getActiveCategories()).thenThrow(new RuntimeException("카테고리 조회 실패"));
            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(List.of(testGuildResponse));
            when(guildQueryService.getPublicGuilds(any(), any())).thenReturn(guildPage);
            when(noticeService.getActiveNotices()).thenReturn(List.of(testNoticeResponse));

            // when
            HomeDataResponse response = bffHomeService.getHomeData(testUserId, null, 0, 20, 5, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getCategories()).isEmpty();
            // 다른 데이터는 정상 조회
            assertThat(response.getRankings()).hasSize(1);
        }

        @Test
        @DisplayName("내 길드 조회 실패 시 빈 목록 반환")
        void getHomeData_myGuildsFetchFailed() {
            // given
            Page<ActivityFeedResponse> feedPage = new PageImpl<>(List.of(testFeedResponse));
            Page<GuildResponse> guildPage = new PageImpl<>(List.of(testGuildResponse));

            when(activityFeedService.getPublicFeeds(anyString(), anyInt(), anyInt())).thenReturn(feedPage);
            when(homeService.getTodayPlayers(any())).thenReturn(List.of(testPlayerResponse));
            when(missionCategoryService.getActiveCategories()).thenReturn(List.of(testCategoryResponse));
            when(guildQueryService.getMyGuilds(testUserId)).thenThrow(new RuntimeException("내 길드 조회 실패"));
            when(guildQueryService.getPublicGuilds(any(), any())).thenReturn(guildPage);
            when(noticeService.getActiveNotices()).thenReturn(List.of(testNoticeResponse));

            // when
            HomeDataResponse response = bffHomeService.getHomeData(testUserId, null, 0, 20, 5, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getMyGuilds()).isEmpty();
            // 공개 길드는 정상 조회
            assertThat(response.getPublicGuilds().getContent()).hasSize(1);
        }

        @Test
        @DisplayName("공개 길드 조회 실패 시 빈 목록 반환")
        void getHomeData_publicGuildsFetchFailed() {
            // given
            Page<ActivityFeedResponse> feedPage = new PageImpl<>(List.of(testFeedResponse));

            when(activityFeedService.getPublicFeeds(anyString(), anyInt(), anyInt())).thenReturn(feedPage);
            when(homeService.getTodayPlayers(any())).thenReturn(List.of(testPlayerResponse));
            when(missionCategoryService.getActiveCategories()).thenReturn(List.of(testCategoryResponse));
            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(List.of(testGuildResponse));
            when(guildQueryService.getPublicGuilds(any(), any())).thenThrow(new RuntimeException("공개 길드 조회 실패"));
            when(noticeService.getActiveNotices()).thenReturn(List.of(testNoticeResponse));

            // when
            HomeDataResponse response = bffHomeService.getHomeData(testUserId, null, 0, 20, 5, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getPublicGuilds().getContent()).isEmpty();
            // 내 길드는 정상 조회
            assertThat(response.getMyGuilds()).hasSize(1);
        }

        @Test
        @DisplayName("공지사항 조회 실패 시 빈 목록 반환")
        void getHomeData_noticesFetchFailed() {
            // given
            Page<ActivityFeedResponse> feedPage = new PageImpl<>(List.of(testFeedResponse));
            Page<GuildResponse> guildPage = new PageImpl<>(List.of(testGuildResponse));

            when(activityFeedService.getPublicFeeds(anyString(), anyInt(), anyInt())).thenReturn(feedPage);
            when(homeService.getTodayPlayers(any())).thenReturn(List.of(testPlayerResponse));
            when(missionCategoryService.getActiveCategories()).thenReturn(List.of(testCategoryResponse));
            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(List.of(testGuildResponse));
            when(guildQueryService.getPublicGuilds(any(), any())).thenReturn(guildPage);
            when(noticeService.getActiveNotices()).thenThrow(new RuntimeException("공지사항 조회 실패"));

            // when
            HomeDataResponse response = bffHomeService.getHomeData(testUserId, null, 0, 20, 5, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getNotices()).isEmpty();
            // 다른 데이터는 정상 조회
            assertThat(response.getFeeds().getContent()).hasSize(1);
        }

        @Test
        @DisplayName("길드에 가입되지 않은 사용자의 홈 데이터를 조회한다")
        void getHomeData_noGuild() {
            // given
            Page<ActivityFeedResponse> feedPage = new PageImpl<>(List.of(testFeedResponse));
            Page<GuildResponse> guildPage = new PageImpl<>(List.of(testGuildResponse));

            when(activityFeedService.getPublicFeeds(anyString(), anyInt(), anyInt())).thenReturn(feedPage);
            when(homeService.getTodayPlayers(any())).thenReturn(List.of(testPlayerResponse));
            when(missionCategoryService.getActiveCategories()).thenReturn(List.of(testCategoryResponse));
            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(Collections.emptyList());
            when(guildQueryService.getPublicGuilds(any(), any())).thenReturn(guildPage);
            when(noticeService.getActiveNotices()).thenReturn(List.of(testNoticeResponse));

            // when
            HomeDataResponse response = bffHomeService.getHomeData(testUserId, null, 0, 20, 5, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getMyGuilds()).isEmpty();
            assertThat(response.getPublicGuilds().getContent()).hasSize(1);
        }

        @Test
        @DisplayName("피드가 있는 경우 정상적으로 조회된다")
        void getHomeData_withFeeds() {
            // given
            Page<ActivityFeedResponse> feedPage = new PageImpl<>(
                List.of(testFeedResponse), PageRequest.of(0, 20), 1
            );
            Page<GuildResponse> guildPage = new PageImpl<>(Collections.emptyList());

            when(activityFeedService.getPublicFeeds(anyString(), anyInt(), anyInt())).thenReturn(feedPage);
            when(homeService.getTodayPlayers(any())).thenReturn(Collections.emptyList());
            when(missionCategoryService.getActiveCategories()).thenReturn(Collections.emptyList());
            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(Collections.emptyList());
            when(guildQueryService.getPublicGuilds(any(), any())).thenReturn(guildPage);
            when(noticeService.getActiveNotices()).thenReturn(Collections.emptyList());

            // when
            HomeDataResponse response = bffHomeService.getHomeData(testUserId, null, 0, 20, 5, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getFeeds()).isNotNull();
            assertThat(response.getFeeds().getContent()).hasSize(1);
        }

        @Test
        @DisplayName("활성 시즌이 있을 때 시즌 MVP 데이터를 조회한다")
        void getHomeData_withActiveSeason_success() {
            // given
            Page<ActivityFeedResponse> feedPage = new PageImpl<>(
                List.of(testFeedResponse), PageRequest.of(0, 20), 1
            );
            Page<GuildResponse> guildPage = new PageImpl<>(
                List.of(testGuildResponse), PageRequest.of(0, 5), 1
            );

            when(activityFeedService.getPublicFeeds(anyString(), anyInt(), anyInt())).thenReturn(feedPage);
            when(homeService.getTodayPlayers(any())).thenReturn(List.of(testPlayerResponse));
            when(homeService.getMvpGuilds()).thenReturn(Collections.emptyList());
            when(missionCategoryService.getActiveCategories()).thenReturn(List.of(testCategoryResponse));
            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(List.of(testGuildResponse));
            when(guildQueryService.getPublicGuilds(any(), any())).thenReturn(guildPage);
            when(noticeService.getActiveNotices()).thenReturn(List.of(testNoticeResponse));
            when(seasonRankingService.getSeasonMvpData(any())).thenReturn(Optional.of(testSeasonMvpData));

            // when
            HomeDataResponse response = bffHomeService.getHomeData(testUserId, null, 0, 20, 5, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getCurrentSeason()).isNotNull();
            assertThat(response.getCurrentSeason().id()).isEqualTo(1L);
            assertThat(response.getCurrentSeason().title()).isEqualTo("2024 윈터 시즌");
            assertThat(response.getCurrentSeason().status()).isEqualTo(SeasonStatus.ACTIVE);
            assertThat(response.getSeasonMvpPlayers()).hasSize(1);
            assertThat(response.getSeasonMvpPlayers().get(0).userId()).isEqualTo(testUserId);
            assertThat(response.getSeasonMvpPlayers().get(0).seasonExp()).isEqualTo(1000L);
            assertThat(response.getSeasonMvpGuilds()).hasSize(1);
            assertThat(response.getSeasonMvpGuilds().get(0).guildId()).isEqualTo(1L);
            assertThat(response.getSeasonMvpGuilds().get(0).seasonExp()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("활성 시즌이 없을 때 시즌 관련 데이터는 null 또는 빈 목록이다")
        void getHomeData_noActiveSeason_nullSeasonData() {
            // given
            Page<ActivityFeedResponse> feedPage = new PageImpl<>(
                List.of(testFeedResponse), PageRequest.of(0, 20), 1
            );
            Page<GuildResponse> guildPage = new PageImpl<>(
                List.of(testGuildResponse), PageRequest.of(0, 5), 1
            );

            when(activityFeedService.getPublicFeeds(anyString(), anyInt(), anyInt())).thenReturn(feedPage);
            when(homeService.getTodayPlayers(any())).thenReturn(List.of(testPlayerResponse));
            when(homeService.getMvpGuilds()).thenReturn(Collections.emptyList());
            when(missionCategoryService.getActiveCategories()).thenReturn(List.of(testCategoryResponse));
            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(List.of(testGuildResponse));
            when(guildQueryService.getPublicGuilds(any(), any())).thenReturn(guildPage);
            when(noticeService.getActiveNotices()).thenReturn(List.of(testNoticeResponse));
            when(seasonRankingService.getSeasonMvpData(any())).thenReturn(Optional.empty());

            // when
            HomeDataResponse response = bffHomeService.getHomeData(testUserId, null, 0, 20, 5, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getCurrentSeason()).isNull();
            assertThat(response.getSeasonMvpPlayers()).isEmpty();
            assertThat(response.getSeasonMvpGuilds()).isEmpty();
            // 다른 데이터는 정상 조회
            assertThat(response.getFeeds().getContent()).hasSize(1);
            assertThat(response.getRankings()).hasSize(1);
        }

        @Test
        @DisplayName("시즌 데이터 조회 실패 시 빈 데이터 반환")
        void getHomeData_seasonFetchFailed_emptySeasonData() {
            // given
            Page<ActivityFeedResponse> feedPage = new PageImpl<>(
                List.of(testFeedResponse), PageRequest.of(0, 20), 1
            );
            Page<GuildResponse> guildPage = new PageImpl<>(
                List.of(testGuildResponse), PageRequest.of(0, 5), 1
            );

            when(activityFeedService.getPublicFeeds(anyString(), anyInt(), anyInt())).thenReturn(feedPage);
            when(homeService.getTodayPlayers(any())).thenReturn(List.of(testPlayerResponse));
            when(homeService.getMvpGuilds()).thenReturn(Collections.emptyList());
            when(missionCategoryService.getActiveCategories()).thenReturn(List.of(testCategoryResponse));
            when(guildQueryService.getMyGuilds(testUserId)).thenReturn(List.of(testGuildResponse));
            when(guildQueryService.getPublicGuilds(any(), any())).thenReturn(guildPage);
            when(noticeService.getActiveNotices()).thenReturn(List.of(testNoticeResponse));
            when(seasonRankingService.getSeasonMvpData(any())).thenThrow(new RuntimeException("시즌 데이터 조회 실패"));

            // when
            HomeDataResponse response = bffHomeService.getHomeData(testUserId, null, 0, 20, 5, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getCurrentSeason()).isNull();
            assertThat(response.getSeasonMvpPlayers()).isEmpty();
            assertThat(response.getSeasonMvpGuilds()).isEmpty();
            // 다른 데이터는 정상 조회
            assertThat(response.getFeeds().getContent()).hasSize(1);
            assertThat(response.getRankings()).hasSize(1);
        }
    }
}
