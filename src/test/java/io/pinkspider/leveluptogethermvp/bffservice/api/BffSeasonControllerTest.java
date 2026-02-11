package io.pinkspider.leveluptogethermvp.bffservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.SeasonDetailResponse;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.SeasonMyRankingResponse;
import io.pinkspider.leveluptogethermvp.bffservice.application.BffSeasonService;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpGuildResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpPlayerResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonRankRewardResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.application.SeasonRankingService;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.enums.SeasonStatus;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import java.time.LocalDateTime;
import java.util.List;
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

@WebMvcTest(controllers = BffSeasonController.class,
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class
    }
)
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BffSeasonControllerTest {

    private static final String MOCK_USER_ID = "test-user-123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    private BffSeasonService bffSeasonService;

    @MockitoBean
    private SeasonRankingService seasonRankingService;

    // ========== Mock 데이터 생성 헬퍼 메서드 ==========

    private SeasonResponse createMockSeasonResponse() {
        return new SeasonResponse(
            1L,
            "2025 윈터 시즌",
            "겨울 시즌 이벤트입니다.",
            LocalDateTime.of(2025, 1, 1, 0, 0),
            LocalDateTime.of(2025, 3, 31, 23, 59),
            100L,
            "윈터 챔피언",
            SeasonStatus.ACTIVE,
            "진행중"
        );
    }

    private SeasonRankRewardResponse createMockRankRewardResponse(int rank) {
        return new SeasonRankRewardResponse(
            (long) rank,
            1L,
            rank,
            rank,
            rank + "위",
            null,
            null,
            "전체 랭킹",
            100L + rank,
            "시즌 " + rank + "등",
            "LEGENDARY",
            rank,
            true
        );
    }

    private SeasonMvpPlayerResponse createMockPlayerRankingResponse(int rank) {
        return SeasonMvpPlayerResponse.of(
            "user-" + rank,
            "플레이어" + rank,
            "https://example.com/profile" + rank + ".jpg",
            10 + rank,
            "모험가",
            TitleRarity.RARE,
            null,
            null,
            null,
            null,
            10000L - (rank * 100L),
            rank
        );
    }

    private SeasonMvpGuildResponse createMockGuildRankingResponse(int rank) {
        return SeasonMvpGuildResponse.of(
            (long) rank,
            "길드" + rank,
            "https://example.com/guild" + rank + ".jpg",
            5 + rank,
            10 + rank,
            50000L - (rank * 1000L),
            rank
        );
    }

    private SeasonMyRankingResponse createMockMyRankingResponse() {
        return SeasonMyRankingResponse.of(
            5,
            8500L,
            3,
            45000L,
            1L,
            "테스트 길드"
        );
    }

    private MissionCategoryResponse createMockCategoryResponse(Long id, String name) {
        return MissionCategoryResponse.builder()
            .id(id)
            .name(name)
            .nameEn(name + " EN")
            .icon("📚")
            .isActive(true)
            .build();
    }

    private SeasonDetailResponse createMockSeasonDetailResponse() {
        return SeasonDetailResponse.of(
            createMockSeasonResponse(),
            List.of(
                createMockRankRewardResponse(1),
                createMockRankRewardResponse(2),
                createMockRankRewardResponse(3)
            ),
            List.of(
                createMockPlayerRankingResponse(1),
                createMockPlayerRankingResponse(2),
                createMockPlayerRankingResponse(3)
            ),
            List.of(
                createMockGuildRankingResponse(1),
                createMockGuildRankingResponse(2),
                createMockGuildRankingResponse(3)
            ),
            createMockMyRankingResponse(),
            List.of(
                createMockCategoryResponse(1L, "운동"),
                createMockCategoryResponse(2L, "공부")
            )
        );
    }

    // ========== 테스트 케이스 ==========

    @Test
    @DisplayName("GET /api/v1/bff/season/{seasonId} : 시즌 상세 데이터 조회")
    void getSeasonDetailTest() throws Exception {
        // given
        SeasonDetailResponse mockResponse = createMockSeasonDetailResponse();

        when(bffSeasonService.getSeasonDetail(anyLong(), anyString(), any(), any()))
            .thenReturn(mockResponse);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/bff/season/{seasonId}", 1L)
                .with(user(MOCK_USER_ID))
                .param("categoryName", "운동")
                .header("Accept-Language", "ko")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("BFF-06. 시즌 상세 데이터 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("BFF - Season")
                        .description("시즌 상세 화면에 필요한 모든 데이터를 한 번에 조회 (시즌 정보, 순위별 보상, 플레이어/길드 랭킹, 내 랭킹, 카테고리)")
                        .pathParameters(
                            parameterWithName("seasonId").type(SimpleType.INTEGER)
                                .description("시즌 ID")
                        )
                        .queryParameters(
                            parameterWithName("categoryName").type(SimpleType.STRING)
                                .description("카테고리명 (선택적, null이면 전체 랭킹)").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            // Season 정보
                            fieldWithPath("value.season").type(JsonFieldType.OBJECT).description("시즌 정보"),
                            fieldWithPath("value.season.id").type(JsonFieldType.NUMBER).description("시즌 ID"),
                            fieldWithPath("value.season.title").type(JsonFieldType.STRING).description("시즌 제목"),
                            fieldWithPath("value.season.description").type(JsonFieldType.STRING).description("시즌 설명").optional(),
                            fieldWithPath("value.season.start_at").type(JsonFieldType.STRING).description("시작일시"),
                            fieldWithPath("value.season.end_at").type(JsonFieldType.STRING).description("종료일시"),
                            fieldWithPath("value.season.reward_title_id").type(JsonFieldType.NUMBER).description("보상 칭호 ID").optional(),
                            fieldWithPath("value.season.reward_title_name").type(JsonFieldType.STRING).description("보상 칭호명").optional(),
                            fieldWithPath("value.season.status").type(JsonFieldType.STRING).description("시즌 상태 (PENDING, ACTIVE, ENDED)"),
                            fieldWithPath("value.season.status_name").type(JsonFieldType.STRING).description("시즌 상태명"),
                            // 순위별 보상
                            fieldWithPath("value.rank_rewards[]").type(JsonFieldType.ARRAY).description("순위별 보상 목록"),
                            fieldWithPath("value.rank_rewards[].id").type(JsonFieldType.NUMBER).description("보상 ID"),
                            fieldWithPath("value.rank_rewards[].season_id").type(JsonFieldType.NUMBER).description("시즌 ID"),
                            fieldWithPath("value.rank_rewards[].rank_start").type(JsonFieldType.NUMBER).description("시작 순위"),
                            fieldWithPath("value.rank_rewards[].rank_end").type(JsonFieldType.NUMBER).description("종료 순위"),
                            fieldWithPath("value.rank_rewards[].rank_range_display").type(JsonFieldType.STRING).description("순위 범위 표시명"),
                            fieldWithPath("value.rank_rewards[].category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.rank_rewards[].category_name").type(JsonFieldType.STRING).description("카테고리명").optional(),
                            fieldWithPath("value.rank_rewards[].ranking_type_display").type(JsonFieldType.STRING).description("랭킹 유형 표시명"),
                            fieldWithPath("value.rank_rewards[].title_id").type(JsonFieldType.NUMBER).description("보상 칭호 ID").optional(),
                            fieldWithPath("value.rank_rewards[].title_name").type(JsonFieldType.STRING).description("보상 칭호명").optional(),
                            fieldWithPath("value.rank_rewards[].title_rarity").type(JsonFieldType.STRING).description("칭호 희귀도").optional(),
                            fieldWithPath("value.rank_rewards[].sort_order").type(JsonFieldType.NUMBER).description("정렬 순서"),
                            fieldWithPath("value.rank_rewards[].is_active").type(JsonFieldType.BOOLEAN).description("활성 여부"),
                            // 플레이어 랭킹
                            fieldWithPath("value.player_rankings[]").type(JsonFieldType.ARRAY).description("플레이어 랭킹 (TOP 10)"),
                            fieldWithPath("value.player_rankings[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.player_rankings[].nickname").type(JsonFieldType.STRING).description("닉네임"),
                            fieldWithPath("value.player_rankings[].profile_image_url").type(JsonFieldType.STRING)
                                .description("프로필 이미지 URL")
                                .optional(),
                            fieldWithPath("value.player_rankings[].level").type(JsonFieldType.NUMBER).description("레벨"),
                            fieldWithPath("value.player_rankings[].title").type(JsonFieldType.STRING).description("칭호 (LEFT + RIGHT 조합)").optional(),
                            fieldWithPath("value.player_rankings[].title_rarity").type(JsonFieldType.STRING).description("칭호 등급 - 최고 등급").optional(),
                            fieldWithPath("value.player_rankings[].left_title").type(JsonFieldType.STRING).description("왼쪽 칭호 (형용사형)").optional(),
                            fieldWithPath("value.player_rankings[].left_title_rarity").type(JsonFieldType.STRING).description("왼쪽 칭호 등급").optional(),
                            fieldWithPath("value.player_rankings[].right_title").type(JsonFieldType.STRING).description("오른쪽 칭호 (명사형)").optional(),
                            fieldWithPath("value.player_rankings[].right_title_rarity").type(JsonFieldType.STRING).description("오른쪽 칭호 등급").optional(),
                            fieldWithPath("value.player_rankings[].season_exp").type(JsonFieldType.NUMBER).description("시즌 경험치"),
                            fieldWithPath("value.player_rankings[].rank").type(JsonFieldType.NUMBER).description("순위"),
                            // 길드 랭킹
                            fieldWithPath("value.guild_rankings[]").type(JsonFieldType.ARRAY).description("길드 랭킹 (TOP 10)"),
                            fieldWithPath("value.guild_rankings[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.guild_rankings[].name").type(JsonFieldType.STRING).description("길드명"),
                            fieldWithPath("value.guild_rankings[].image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("value.guild_rankings[].level").type(JsonFieldType.NUMBER).description("길드 레벨"),
                            fieldWithPath("value.guild_rankings[].member_count").type(JsonFieldType.NUMBER).description("멤버 수"),
                            fieldWithPath("value.guild_rankings[].season_exp").type(JsonFieldType.NUMBER).description("시즌 경험치"),
                            fieldWithPath("value.guild_rankings[].rank").type(JsonFieldType.NUMBER).description("순위"),
                            // 내 랭킹
                            fieldWithPath("value.my_ranking").type(JsonFieldType.OBJECT).description("내 랭킹 정보"),
                            fieldWithPath("value.my_ranking.player_rank").type(JsonFieldType.NUMBER).description("내 플레이어 순위").optional(),
                            fieldWithPath("value.my_ranking.player_season_exp").type(JsonFieldType.NUMBER).description("내 시즌 경험치"),
                            fieldWithPath("value.my_ranking.guild_rank").type(JsonFieldType.NUMBER).description("내 길드 순위").optional(),
                            fieldWithPath("value.my_ranking.guild_season_exp").type(JsonFieldType.NUMBER).description("내 길드 시즌 경험치").optional(),
                            fieldWithPath("value.my_ranking.guild_id").type(JsonFieldType.NUMBER).description("내 길드 ID").optional(),
                            fieldWithPath("value.my_ranking.guild_name").type(JsonFieldType.STRING).description("내 길드명").optional(),
                            // 카테고리
                            fieldWithPath("value.categories[]").type(JsonFieldType.ARRAY).description("미션 카테고리 목록"),
                            fieldWithPath("value.categories[].id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                            fieldWithPath("value.categories[].name").type(JsonFieldType.STRING).description("카테고리명"),
                            fieldWithPath("value.categories[].name_en").type(JsonFieldType.STRING).description("카테고리명 (영문)").optional(),
                            fieldWithPath("value.categories[].name_ar").type(JsonFieldType.STRING).description("카테고리명 (아랍어)").optional(),
                            fieldWithPath("value.categories[].description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                            fieldWithPath("value.categories[].description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영문)").optional(),
                            fieldWithPath("value.categories[].description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                            fieldWithPath("value.categories[].icon").type(JsonFieldType.STRING).description("아이콘").optional(),
                            fieldWithPath("value.categories[].display_order").type(JsonFieldType.NUMBER).description("표시 순서").optional(),
                            fieldWithPath("value.categories[].is_active").type(JsonFieldType.BOOLEAN).description("활성 여부"),
                            fieldWithPath("value.categories[].created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value.categories[].modified_at").type(JsonFieldType.STRING).description("수정일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/bff/season/current : 현재 활성 시즌 상세 데이터 조회")
    void getCurrentSeasonDetailTest() throws Exception {
        // given
        SeasonDetailResponse mockResponse = createMockSeasonDetailResponse();

        when(bffSeasonService.getCurrentSeasonDetail(anyString(), any(), any()))
            .thenReturn(mockResponse);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/bff/season/current")
                .with(user(MOCK_USER_ID))
                .header("Accept-Language", "ko")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("BFF-07. 현재 활성 시즌 상세 데이터 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("BFF - Season")
                        .description("현재 활성화된 시즌의 상세 데이터를 조회합니다. 활성 시즌이 없는 경우 에러를 반환합니다.")
                        .queryParameters(
                            parameterWithName("categoryName").type(SimpleType.STRING)
                                .description("카테고리명 (선택적, null이면 전체 랭킹)").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value.season").type(JsonFieldType.OBJECT).description("시즌 정보"),
                            fieldWithPath("value.season.id").type(JsonFieldType.NUMBER).description("시즌 ID"),
                            fieldWithPath("value.season.title").type(JsonFieldType.STRING).description("시즌 제목"),
                            fieldWithPath("value.season.description").type(JsonFieldType.STRING).description("시즌 설명").optional(),
                            fieldWithPath("value.season.start_at").type(JsonFieldType.STRING).description("시작일시"),
                            fieldWithPath("value.season.end_at").type(JsonFieldType.STRING).description("종료일시"),
                            fieldWithPath("value.season.reward_title_id").type(JsonFieldType.NUMBER).description("보상 칭호 ID").optional(),
                            fieldWithPath("value.season.reward_title_name").type(JsonFieldType.STRING).description("보상 칭호명").optional(),
                            fieldWithPath("value.season.status").type(JsonFieldType.STRING).description("시즌 상태"),
                            fieldWithPath("value.season.status_name").type(JsonFieldType.STRING).description("시즌 상태명"),
                            fieldWithPath("value.rank_rewards[]").type(JsonFieldType.ARRAY).description("순위별 보상 목록"),
                            fieldWithPath("value.rank_rewards[].id").type(JsonFieldType.NUMBER).description("보상 ID"),
                            fieldWithPath("value.rank_rewards[].season_id").type(JsonFieldType.NUMBER).description("시즌 ID"),
                            fieldWithPath("value.rank_rewards[].rank_start").type(JsonFieldType.NUMBER).description("시작 순위"),
                            fieldWithPath("value.rank_rewards[].rank_end").type(JsonFieldType.NUMBER).description("종료 순위"),
                            fieldWithPath("value.rank_rewards[].rank_range_display").type(JsonFieldType.STRING).description("순위 범위 표시명"),
                            fieldWithPath("value.rank_rewards[].category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.rank_rewards[].category_name").type(JsonFieldType.STRING).description("카테고리명").optional(),
                            fieldWithPath("value.rank_rewards[].ranking_type_display").type(JsonFieldType.STRING).description("랭킹 유형 표시명"),
                            fieldWithPath("value.rank_rewards[].title_id").type(JsonFieldType.NUMBER).description("보상 칭호 ID").optional(),
                            fieldWithPath("value.rank_rewards[].title_name").type(JsonFieldType.STRING).description("보상 칭호명").optional(),
                            fieldWithPath("value.rank_rewards[].title_rarity").type(JsonFieldType.STRING).description("칭호 희귀도").optional(),
                            fieldWithPath("value.rank_rewards[].sort_order").type(JsonFieldType.NUMBER).description("정렬 순서"),
                            fieldWithPath("value.rank_rewards[].is_active").type(JsonFieldType.BOOLEAN).description("활성 여부"),
                            fieldWithPath("value.player_rankings[]").type(JsonFieldType.ARRAY).description("플레이어 랭킹"),
                            fieldWithPath("value.player_rankings[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.player_rankings[].nickname").type(JsonFieldType.STRING).description("닉네임"),
                            fieldWithPath("value.player_rankings[].profile_image_url").type(JsonFieldType.STRING)
                                .description("프로필 이미지 URL")
                                .optional(),
                            fieldWithPath("value.player_rankings[].level").type(JsonFieldType.NUMBER).description("레벨"),
                            fieldWithPath("value.player_rankings[].title").type(JsonFieldType.STRING).description("칭호 (LEFT + RIGHT 조합)").optional(),
                            fieldWithPath("value.player_rankings[].title_rarity").type(JsonFieldType.STRING).description("칭호 등급 - 최고 등급").optional(),
                            fieldWithPath("value.player_rankings[].left_title").type(JsonFieldType.STRING).description("왼쪽 칭호 (형용사형)").optional(),
                            fieldWithPath("value.player_rankings[].left_title_rarity").type(JsonFieldType.STRING).description("왼쪽 칭호 등급").optional(),
                            fieldWithPath("value.player_rankings[].right_title").type(JsonFieldType.STRING).description("오른쪽 칭호 (명사형)").optional(),
                            fieldWithPath("value.player_rankings[].right_title_rarity").type(JsonFieldType.STRING).description("오른쪽 칭호 등급").optional(),
                            fieldWithPath("value.player_rankings[].season_exp").type(JsonFieldType.NUMBER).description("시즌 경험치"),
                            fieldWithPath("value.player_rankings[].rank").type(JsonFieldType.NUMBER).description("순위"),
                            fieldWithPath("value.guild_rankings[]").type(JsonFieldType.ARRAY).description("길드 랭킹"),
                            fieldWithPath("value.guild_rankings[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.guild_rankings[].name").type(JsonFieldType.STRING).description("길드명"),
                            fieldWithPath("value.guild_rankings[].image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("value.guild_rankings[].level").type(JsonFieldType.NUMBER).description("길드 레벨"),
                            fieldWithPath("value.guild_rankings[].member_count").type(JsonFieldType.NUMBER).description("멤버 수"),
                            fieldWithPath("value.guild_rankings[].season_exp").type(JsonFieldType.NUMBER).description("시즌 경험치"),
                            fieldWithPath("value.guild_rankings[].rank").type(JsonFieldType.NUMBER).description("순위"),
                            fieldWithPath("value.my_ranking").type(JsonFieldType.OBJECT).description("내 랭킹 정보"),
                            fieldWithPath("value.my_ranking.player_rank").type(JsonFieldType.NUMBER).description("내 플레이어 순위").optional(),
                            fieldWithPath("value.my_ranking.player_season_exp").type(JsonFieldType.NUMBER).description("내 시즌 경험치"),
                            fieldWithPath("value.my_ranking.guild_rank").type(JsonFieldType.NUMBER).description("내 길드 순위").optional(),
                            fieldWithPath("value.my_ranking.guild_season_exp").type(JsonFieldType.NUMBER).description("내 길드 시즌 경험치").optional(),
                            fieldWithPath("value.my_ranking.guild_id").type(JsonFieldType.NUMBER).description("내 길드 ID").optional(),
                            fieldWithPath("value.my_ranking.guild_name").type(JsonFieldType.STRING).description("내 길드명").optional(),
                            fieldWithPath("value.categories[]").type(JsonFieldType.ARRAY).description("미션 카테고리 목록"),
                            fieldWithPath("value.categories[].id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                            fieldWithPath("value.categories[].name").type(JsonFieldType.STRING).description("카테고리명"),
                            fieldWithPath("value.categories[].name_en").type(JsonFieldType.STRING).description("카테고리명 (영문)").optional(),
                            fieldWithPath("value.categories[].name_ar").type(JsonFieldType.STRING).description("카테고리명 (아랍어)").optional(),
                            fieldWithPath("value.categories[].description").type(JsonFieldType.STRING).description("카테고리 설명").optional(),
                            fieldWithPath("value.categories[].description_en").type(JsonFieldType.STRING).description("카테고리 설명 (영문)").optional(),
                            fieldWithPath("value.categories[].description_ar").type(JsonFieldType.STRING).description("카테고리 설명 (아랍어)").optional(),
                            fieldWithPath("value.categories[].icon").type(JsonFieldType.STRING).description("아이콘").optional(),
                            fieldWithPath("value.categories[].display_order").type(JsonFieldType.NUMBER).description("표시 순서").optional(),
                            fieldWithPath("value.categories[].is_active").type(JsonFieldType.BOOLEAN).description("활성 여부"),
                            fieldWithPath("value.categories[].created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value.categories[].modified_at").type(JsonFieldType.STRING).description("수정일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/bff/season/current : 활성 시즌이 없는 경우 에러 반환")
    void getCurrentSeasonDetail_noActiveSeason() throws Exception {
        // given
        when(bffSeasonService.getCurrentSeasonDetail(anyString(), any(), any()))
            .thenThrow(new CustomException("NO_ACTIVE_SEASON", "현재 활성화된 시즌이 없습니다."));

        // when & then
        mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/bff/season/current")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError());
    }

    @Test
    @DisplayName("GET /api/v1/bff/season/{seasonId} : 존재하지 않는 시즌 조회 시 에러 반환")
    void getSeasonDetail_notFound() throws Exception {
        // given
        when(bffSeasonService.getSeasonDetail(anyLong(), anyString(), any(), any()))
            .thenThrow(new CustomException("SEASON_NOT_FOUND", "시즌을 찾을 수 없습니다."));

        // when & then
        mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/bff/season/{seasonId}", 999L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError());
    }

    @Test
    @DisplayName("DELETE /api/v1/bff/season/cache : 시즌 캐시 삭제")
    void evictSeasonCacheTest() throws Exception {
        // given
        doNothing().when(seasonRankingService).evictAllSeasonCaches();

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/bff/season/cache")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("BFF-08. 시즌 캐시 삭제",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("BFF - Season")
                        .description("시즌 관련 Redis 캐시를 삭제합니다. (currentSeason, seasonMvpData)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.STRING).description("삭제 완료 메시지")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
        verify(seasonRankingService).evictAllSeasonCaches();
    }
}
