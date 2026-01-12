package io.pinkspider.leveluptogethermvp.userservice.home.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.adminservice.api.dto.SeasonMvpData;
import io.pinkspider.leveluptogethermvp.adminservice.api.dto.SeasonMvpGuildResponse;
import io.pinkspider.leveluptogethermvp.adminservice.api.dto.SeasonMvpPlayerResponse;
import io.pinkspider.leveluptogethermvp.adminservice.api.dto.SeasonResponse;
import io.pinkspider.leveluptogethermvp.adminservice.application.SeasonRankingService;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.HomeBannerResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.MvpGuildResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.api.dto.TodayPlayerResponse;
import io.pinkspider.leveluptogethermvp.userservice.home.application.HomeService;
import io.pinkspider.leveluptogethermvp.adminservice.domain.enums.BannerType;
import io.pinkspider.leveluptogethermvp.adminservice.domain.enums.LinkType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = HomeController.class,
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class
    }
)
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class HomeControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    private HomeService homeService;

    @MockitoBean
    private SeasonRankingService seasonRankingService;

    private HomeBannerResponse createMockBannerResponse(BannerType type) {
        return HomeBannerResponse.builder()
            .id(1L)
            .bannerType(type)
            .bannerTypeDisplayName(type.getDisplayName())
            .title("테스트 배너")
            .description("테스트 배너 설명입니다.")
            .imageUrl("https://example.com/banner.jpg")
            .linkType(LinkType.INTERNAL)
            .linkUrl("/guild/1")
            .guildId(1L)
            .sortOrder(1)
            .startAt(LocalDateTime.now().minusDays(1))
            .endAt(LocalDateTime.now().plusDays(30))
            .createdAt(LocalDateTime.now())
            .build();
    }

    private TodayPlayerResponse createMockTodayPlayerResponse(int rank) {
        return TodayPlayerResponse.builder()
            .userId("user-" + rank)
            .nickname("플레이어" + rank)
            .profileImageUrl("https://example.com/profile" + rank + ".jpg")
            .level(10 + rank)
            .title("모험가")
            .earnedExp(1000L - (rank * 100L))
            .rank(rank)
            .build();
    }

    @Test
    @DisplayName("GET /api/v1/home/banners : 활성화된 배너 목록 조회")
    void getBannersTest() throws Exception {
        // given
        List<HomeBannerResponse> banners = List.of(
            createMockBannerResponse(BannerType.GUILD_RECRUIT),
            createMockBannerResponse(BannerType.EVENT)
        );

        when(homeService.getActiveBanners()).thenReturn(banners);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/home/banners")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("홈-01. 활성 배너 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Home")
                        .description("활성화된 모든 배너 목록 조회 (길드 모집, 이벤트, 공지, 광고)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("배너 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("배너 ID"),
                            fieldWithPath("value[].banner_type").type(JsonFieldType.STRING).description("배너 유형"),
                            fieldWithPath("value[].banner_type_display_name").type(JsonFieldType.STRING).description("배너 유형 표시명"),
                            fieldWithPath("value[].title").type(JsonFieldType.STRING).description("배너 제목"),
                            fieldWithPath("value[].description").type(JsonFieldType.STRING).description("배너 설명").optional(),
                            fieldWithPath("value[].image_url").type(JsonFieldType.STRING).description("배너 이미지 URL").optional(),
                            fieldWithPath("value[].link_type").type(JsonFieldType.STRING).description("링크 유형").optional(),
                            fieldWithPath("value[].link_url").type(JsonFieldType.STRING).description("링크 URL").optional(),
                            fieldWithPath("value[].guild_id").type(JsonFieldType.NUMBER).description("연결된 길드 ID").optional(),
                            fieldWithPath("value[].sort_order").type(JsonFieldType.NUMBER).description("정렬 순서"),
                            fieldWithPath("value[].start_at").type(JsonFieldType.STRING).description("시작일시").optional(),
                            fieldWithPath("value[].end_at").type(JsonFieldType.STRING).description("종료일시").optional(),
                            fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("생성일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/home/banners/guild-recruit : 길드 모집 배너 목록 조회")
    void getGuildRecruitBannersTest() throws Exception {
        // given
        List<HomeBannerResponse> banners = List.of(
            createMockBannerResponse(BannerType.GUILD_RECRUIT)
        );

        when(homeService.getActiveBannersByType(BannerType.GUILD_RECRUIT)).thenReturn(banners);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/home/banners/guild-recruit")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("홈-02. 길드 모집 배너 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Home")
                        .description("길드 모집 배너만 조회")
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/home/banners/type/{bannerType} : 특정 유형 배너 목록 조회")
    void getBannersByTypeTest() throws Exception {
        // given
        List<HomeBannerResponse> banners = List.of(
            createMockBannerResponse(BannerType.EVENT)
        );

        when(homeService.getActiveBannersByType(any(BannerType.class))).thenReturn(banners);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/home/banners/type/{bannerType}", "EVENT")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("홈-03. 특정 유형 배너 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Home")
                        .description("특정 유형의 배너만 조회 (GUILD_RECRUIT, EVENT, NOTICE, AD)")
                        .pathParameters(
                            parameterWithName("bannerType").type(SimpleType.STRING)
                                .description("배너 유형 (GUILD_RECRUIT, EVENT, NOTICE, AD)")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/home/today-players : 오늘의 플레이어 목록 조회")
    void getTodayPlayersTest() throws Exception {
        // given
        List<TodayPlayerResponse> players = List.of(
            createMockTodayPlayerResponse(1),
            createMockTodayPlayerResponse(2),
            createMockTodayPlayerResponse(3)
        );

        when(homeService.getTodayPlayers(isNull())).thenReturn(players);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/home/today-players")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("홈-04. 오늘의 플레이어 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Home")
                        .description("오늘 가장 많은 경험치를 획득한 상위 5명의 플레이어 목록")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("플레이어 목록"),
                            fieldWithPath("value[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value[].nickname").type(JsonFieldType.STRING).description("닉네임"),
                            fieldWithPath("value[].profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value[].level").type(JsonFieldType.NUMBER).description("레벨"),
                            fieldWithPath("value[].title").type(JsonFieldType.STRING).description("장착 칭호").optional(),
                            fieldWithPath("value[].title_rarity").type(JsonFieldType.STRING).description("칭호 등급 (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC)").optional(),
                            fieldWithPath("value[].earned_exp").type(JsonFieldType.NUMBER).description("오늘 획득한 경험치"),
                            fieldWithPath("value[].rank").type(JsonFieldType.NUMBER).description("순위")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/home/today-players?categoryId=1 : 카테고리별 MVP 플레이어 조회")
    void getTodayPlayersByCategoryTest() throws Exception {
        // given
        List<TodayPlayerResponse> players = List.of(
            createMockTodayPlayerResponse(1),
            createMockTodayPlayerResponse(2)
        );

        when(homeService.getTodayPlayersByCategory(eq(1L), isNull())).thenReturn(players);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/home/today-players")
                .param("categoryId", "1")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("홈-05. 카테고리별 MVP 플레이어 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Home")
                        .description("특정 카테고리에서 오늘 가장 많은 경험치를 획득한 플레이어 목록")
                        .queryParameters(
                            parameterWithName("categoryId").type(SimpleType.NUMBER)
                                .description("카테고리 ID (선택, 없으면 전체)")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/home/today-players : 오늘의 플레이어가 없을 경우 빈 목록 반환")
    void getTodayPlayersEmptyTest() throws Exception {
        // given
        when(homeService.getTodayPlayers(isNull())).thenReturn(List.of());

        // when & then
        mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/home/today-players")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/home/mvp-guilds : MVP 길드 목록 조회")
    void getMvpGuildsTest() throws Exception {
        // given
        List<MvpGuildResponse> guilds = List.of(
            MvpGuildResponse.of(1L, "테스트길드1", "https://example.com/guild1.jpg", 5, 10, 5000L, 1),
            MvpGuildResponse.of(2L, "테스트길드2", "https://example.com/guild2.jpg", 3, 5, 3000L, 2)
        );

        when(homeService.getMvpGuilds()).thenReturn(guilds);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/home/mvp-guilds")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("홈-06. MVP 길드 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Home")
                        .description("오늘 가장 많은 경험치를 획득한 상위 5개의 길드 목록")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("길드 목록"),
                            fieldWithPath("value[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value[].name").type(JsonFieldType.STRING).description("길드명"),
                            fieldWithPath("value[].image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("value[].level").type(JsonFieldType.NUMBER).description("길드 레벨"),
                            fieldWithPath("value[].member_count").type(JsonFieldType.NUMBER).description("멤버 수"),
                            fieldWithPath("value[].earned_exp").type(JsonFieldType.NUMBER).description("오늘 획득한 경험치"),
                            fieldWithPath("value[].rank").type(JsonFieldType.NUMBER).description("순위")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/home/season : 현재 시즌 MVP 정보 조회")
    void getSeasonMvpDataTest() throws Exception {
        // given
        SeasonResponse season = new SeasonResponse(
            1L,
            "시즌 1",
            "첫 번째 시즌입니다.",
            LocalDateTime.now().minusDays(10),
            LocalDateTime.now().plusDays(20),
            1L,
            "시즌 챔피언",
            io.pinkspider.leveluptogethermvp.adminservice.domain.enums.SeasonStatus.ACTIVE,
            "진행 중"
        );

        List<SeasonMvpPlayerResponse> players = List.of(
            SeasonMvpPlayerResponse.of("user-1", "플레이어1", "https://example.com/profile1.jpg", 15, "모험가", null, 50000L, 1),
            SeasonMvpPlayerResponse.of("user-2", "플레이어2", "https://example.com/profile2.jpg", 12, "탐험가", null, 40000L, 2)
        );

        List<SeasonMvpGuildResponse> guilds = List.of(
            SeasonMvpGuildResponse.of(1L, "테스트길드1", "https://example.com/guild1.jpg", 5, 10, 100000L, 1)
        );

        SeasonMvpData seasonMvpData = SeasonMvpData.of(season, players, guilds);

        when(seasonRankingService.getSeasonMvpData(isNull())).thenReturn(Optional.of(seasonMvpData));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/home/season")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("홈-07. 현재 시즌 MVP 정보 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Home")
                        .description("현재 활성 시즌 정보 및 시즌 MVP 플레이어/길드 랭킹")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("시즌 MVP 데이터 (활성 시즌이 없으면 null)").optional(),
                            fieldWithPath("value.current_season").type(JsonFieldType.OBJECT).description("현재 시즌 정보"),
                            fieldWithPath("value.current_season.id").type(JsonFieldType.NUMBER).description("시즌 ID"),
                            fieldWithPath("value.current_season.title").type(JsonFieldType.STRING).description("시즌 제목"),
                            fieldWithPath("value.current_season.description").type(JsonFieldType.STRING).description("시즌 설명").optional(),
                            fieldWithPath("value.current_season.start_at").type(JsonFieldType.STRING).description("시즌 시작일시"),
                            fieldWithPath("value.current_season.end_at").type(JsonFieldType.STRING).description("시즌 종료일시"),
                            fieldWithPath("value.current_season.reward_title_id").type(JsonFieldType.NUMBER).description("보상 칭호 ID").optional(),
                            fieldWithPath("value.current_season.reward_title_name").type(JsonFieldType.STRING).description("보상 칭호명").optional(),
                            fieldWithPath("value.current_season.status").type(JsonFieldType.STRING).description("시즌 상태 (UPCOMING, ACTIVE, ENDED)"),
                            fieldWithPath("value.current_season.status_name").type(JsonFieldType.STRING).description("시즌 상태 표시명"),
                            fieldWithPath("value.season_mvp_players[]").type(JsonFieldType.ARRAY).description("시즌 MVP 플레이어 목록"),
                            fieldWithPath("value.season_mvp_players[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.season_mvp_players[].nickname").type(JsonFieldType.STRING).description("닉네임"),
                            fieldWithPath("value.season_mvp_players[].profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value.season_mvp_players[].level").type(JsonFieldType.NUMBER).description("레벨"),
                            fieldWithPath("value.season_mvp_players[].title").type(JsonFieldType.STRING).description("장착 칭호").optional(),
                            fieldWithPath("value.season_mvp_players[].title_rarity").type(JsonFieldType.STRING).description("칭호 등급").optional(),
                            fieldWithPath("value.season_mvp_players[].season_exp").type(JsonFieldType.NUMBER).description("시즌 획득 경험치"),
                            fieldWithPath("value.season_mvp_players[].rank").type(JsonFieldType.NUMBER).description("순위"),
                            fieldWithPath("value.season_mvp_guilds[]").type(JsonFieldType.ARRAY).description("시즌 MVP 길드 목록"),
                            fieldWithPath("value.season_mvp_guilds[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.season_mvp_guilds[].name").type(JsonFieldType.STRING).description("길드명"),
                            fieldWithPath("value.season_mvp_guilds[].image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("value.season_mvp_guilds[].level").type(JsonFieldType.NUMBER).description("길드 레벨"),
                            fieldWithPath("value.season_mvp_guilds[].member_count").type(JsonFieldType.NUMBER).description("멤버 수"),
                            fieldWithPath("value.season_mvp_guilds[].season_exp").type(JsonFieldType.NUMBER).description("시즌 획득 경험치"),
                            fieldWithPath("value.season_mvp_guilds[].rank").type(JsonFieldType.NUMBER).description("순위")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/home/season : 활성 시즌이 없을 경우 null 반환")
    void getSeasonMvpDataEmptyTest() throws Exception {
        // given
        when(seasonRankingService.getSeasonMvpData(isNull())).thenReturn(Optional.empty());

        // when & then
        mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/home/season")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk());
    }
}
