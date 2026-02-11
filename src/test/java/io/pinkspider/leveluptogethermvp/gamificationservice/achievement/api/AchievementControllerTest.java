package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.gamificationservice.stats.application.UserStatsService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.UserAchievementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.stats.domain.dto.UserStatsResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.UserTitleResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import java.time.LocalDate;
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

@WebMvcTest(controllers = AchievementController.class,
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
class AchievementControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private AchievementService achievementService;

    @MockitoBean
    private TitleService titleService;

    @MockitoBean
    private UserStatsService userStatsService;

    private static final String MOCK_USER_ID = "test-user-123";

    @Test
    @DisplayName("GET /api/v1/achievements : 전체 업적 목록")
    void getAllAchievementsTest() throws Exception {
        // given
        List<AchievementResponse> responses = List.of(
            AchievementResponse.builder()
                .id(1L)
                .name("첫 미션 완료")
                .description("첫 번째 미션을 완료하세요")
                .categoryCode("MISSION")
                .requiredCount(1)
                .rewardExp(100)
                .iconUrl("https://example.com/icon1.png")
                .build(),
            AchievementResponse.builder()
                .id(2L)
                .name("7일 연속 활동")
                .description("7일 연속 활동하세요")
                .categoryCode("STREAK")
                .requiredCount(7)
                .rewardExp(200)
                .build()
        );

        when(achievementService.getAllAchievements()).thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/achievements")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("업적-01. 전체 업적 목록",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Achievement")
                        .description("전체 업적 목록 조회")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("업적 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("업적 ID"),
                            fieldWithPath("value[].name").type(JsonFieldType.STRING).description("업적 이름"),
                            fieldWithPath("value[].description").type(JsonFieldType.STRING).description("업적 설명").optional(),
                            fieldWithPath("value[].category_code").type(JsonFieldType.STRING).description("업적 카테고리 코드 (MISSION, STREAK, GUILD, LEVEL, SPECIAL)"),
                            fieldWithPath("value[].mission_category_id").type(JsonFieldType.NUMBER).description("미션 카테고리 ID (MISSION 카테고리일 때)").optional(),
                            fieldWithPath("value[].mission_category_name").type(JsonFieldType.STRING).description("미션 카테고리명").optional(),
                            fieldWithPath("value[].icon_url").type(JsonFieldType.STRING).description("아이콘 URL").optional(),
                            fieldWithPath("value[].required_count").type(JsonFieldType.NUMBER).description("필요 횟수"),
                            fieldWithPath("value[].reward_exp").type(JsonFieldType.NUMBER).description("보상 경험치").optional(),
                            fieldWithPath("value[].reward_title_id").type(JsonFieldType.NUMBER).description("보상 칭호 ID").optional(),
                            fieldWithPath("value[].is_hidden").type(JsonFieldType.BOOLEAN).description("숨김 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/achievements/category/{category} : 카테고리별 업적 조회")
    void getAchievementsByCategoryTest() throws Exception {
        // given
        List<AchievementResponse> responses = List.of(
            AchievementResponse.builder()
                .id(1L)
                .name("첫 미션 완료")
                .categoryCode("MISSION")
                .requiredCount(1)
                .rewardExp(100)
                .build()
        );

        when(achievementService.getAchievementsByCategoryCode(anyString()))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/achievements/category/{category}", "MISSION")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("업적-02. 카테고리별 업적 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Achievement")
                        .description("카테고리별 업적 목록 조회")
                        .pathParameters(
                            parameterWithName("category").type(SimpleType.STRING).description("업적 카테고리 (MISSION, STREAK, GUILD, LEVEL, SPECIAL)")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/achievements/my : 내 업적 목록")
    void getMyAchievementsTest() throws Exception {
        // given
        List<UserAchievementResponse> responses = List.of(
            UserAchievementResponse.builder()
                .id(1L)
                .achievementId(1L)
                .name("첫 미션 완료")
                .description("첫 번째 미션을 완료하세요")
                .categoryCode("MISSION")
                .currentCount(1)
                .requiredCount(1)
                .progressPercent(100.0)
                .isCompleted(true)
                .isRewardClaimed(true)
                .completedAt(LocalDateTime.now())
                .rewardExp(100)
                .build()
        );

        when(achievementService.getUserAchievements(anyString())).thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/achievements/my")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("업적-03. 내 업적 목록",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Achievement")
                        .description("내 업적 진행 상태 조회 (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("내 업적 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("사용자 업적 ID"),
                            fieldWithPath("value[].achievement_id").type(JsonFieldType.NUMBER).description("업적 ID"),
                            fieldWithPath("value[].name").type(JsonFieldType.STRING).description("업적 이름"),
                            fieldWithPath("value[].description").type(JsonFieldType.STRING).description("업적 설명").optional(),
                            fieldWithPath("value[].category_code").type(JsonFieldType.STRING).description("카테고리 코드"),
                            fieldWithPath("value[].mission_category_id").type(JsonFieldType.NUMBER).description("미션 카테고리 ID").optional(),
                            fieldWithPath("value[].mission_category_name").type(JsonFieldType.STRING).description("미션 카테고리명").optional(),
                            fieldWithPath("value[].icon_url").type(JsonFieldType.STRING).description("아이콘 URL").optional(),
                            fieldWithPath("value[].current_count").type(JsonFieldType.NUMBER).description("현재 진행 값"),
                            fieldWithPath("value[].required_count").type(JsonFieldType.NUMBER).description("필요 횟수"),
                            fieldWithPath("value[].progress_percent").type(JsonFieldType.NUMBER).description("진행률 (%)"),
                            fieldWithPath("value[].is_completed").type(JsonFieldType.BOOLEAN).description("완료 여부"),
                            fieldWithPath("value[].is_reward_claimed").type(JsonFieldType.BOOLEAN).description("보상 수령 여부"),
                            fieldWithPath("value[].completed_at").type(JsonFieldType.STRING).description("완료 일시").optional(),
                            fieldWithPath("value[].reward_exp").type(JsonFieldType.NUMBER).description("보상 경험치").optional(),
                            fieldWithPath("value[].reward_title_id").type(JsonFieldType.NUMBER).description("보상 칭호 ID").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/achievements/{achievementId}/claim : 보상 수령")
    void claimRewardTest() throws Exception {
        // given
        UserAchievementResponse response = UserAchievementResponse.builder()
            .id(1L)
            .achievementId(1L)
            .name("첫 미션 완료")
            .categoryCode("MISSION")
            .currentCount(1)
            .requiredCount(1)
            .progressPercent(100.0)
            .isCompleted(true)
            .isRewardClaimed(true)
            .completedAt(LocalDateTime.now())
            .rewardExp(100)
            .build();

        when(achievementService.claimReward(anyString(), anyLong())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/achievements/{achievementId}/claim", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("업적-04. 보상 수령",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Achievement")
                        .description("완료된 업적의 보상 수령 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("achievementId").type(SimpleType.NUMBER).description("업적 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/achievements/titles : 전체 칭호 목록")
    void getAllTitlesTest() throws Exception {
        // given
        List<TitleResponse> responses = List.of(
            TitleResponse.builder()
                .id(1L)
                .name("신입")
                .displayName("신입")
                .description("이제 막 시작한")
                .rarity(TitleRarity.COMMON)
                .positionType(TitlePosition.LEFT)
                .colorCode("#808080")
                .acquisitionType(TitleAcquisitionType.LEVEL)
                .acquisitionCondition("레벨 1 달성")
                .build(),
            TitleResponse.builder()
                .id(26L)
                .name("모험가")
                .displayName("모험가")
                .description("모험을 시작한 자")
                .rarity(TitleRarity.COMMON)
                .positionType(TitlePosition.RIGHT)
                .colorCode("#808080")
                .acquisitionType(TitleAcquisitionType.LEVEL)
                .acquisitionCondition("레벨 1 달성")
                .build()
        );

        when(titleService.getAllTitles()).thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/achievements/titles")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("업적-05. 전체 칭호 목록",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Achievement - Title")
                        .description("전체 칭호 목록 조회 (LEFT: 형용사형, RIGHT: 명사형)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("칭호 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("칭호 ID"),
                            fieldWithPath("value[].name").type(JsonFieldType.STRING).description("칭호 이름"),
                            fieldWithPath("value[].name_en").type(JsonFieldType.STRING).description("칭호 이름 (영어)").optional(),
                            fieldWithPath("value[].name_ar").type(JsonFieldType.STRING).description("칭호 이름 (아랍어)").optional(),
                            fieldWithPath("value[].display_name").type(JsonFieldType.STRING).description("표시 이름").optional(),
                            fieldWithPath("value[].description").type(JsonFieldType.STRING).description("칭호 설명").optional(),
                            fieldWithPath("value[].description_en").type(JsonFieldType.STRING).description("칭호 설명 (영어)").optional(),
                            fieldWithPath("value[].description_ar").type(JsonFieldType.STRING).description("칭호 설명 (아랍어)").optional(),
                            fieldWithPath("value[].rarity").type(JsonFieldType.STRING).description("희귀도 (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY)"),
                            fieldWithPath("value[].position_type").type(JsonFieldType.STRING).description("위치 타입 (LEFT: 형용사형, RIGHT: 명사형)"),
                            fieldWithPath("value[].color_code").type(JsonFieldType.STRING).description("색상 코드").optional(),
                            fieldWithPath("value[].icon_url").type(JsonFieldType.STRING).description("아이콘 URL").optional(),
                            fieldWithPath("value[].acquisition_type").type(JsonFieldType.STRING).description("획득 방법 (LEVEL, ACHIEVEMENT, MISSION, GUILD, EVENT, SPECIAL)").optional(),
                            fieldWithPath("value[].acquisition_condition").type(JsonFieldType.STRING).description("획득 조건 설명").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/achievements/titles/my : 내 칭호 목록")
    void getMyTitlesTest() throws Exception {
        // given
        List<UserTitleResponse> responses = List.of(
            UserTitleResponse.builder()
                .id(1L)
                .titleId(1L)
                .name("신입")
                .displayName("신입")
                .description("이제 막 시작한")
                .rarity(TitleRarity.COMMON)
                .positionType(TitlePosition.LEFT)
                .colorCode("#808080")
                .acquisitionType(TitleAcquisitionType.LEVEL)
                .acquisitionCondition("레벨 1 달성")
                .isEquipped(true)
                .equippedPosition(TitlePosition.LEFT)
                .acquiredAt(LocalDateTime.now())
                .build(),
            UserTitleResponse.builder()
                .id(2L)
                .titleId(26L)
                .name("모험가")
                .displayName("모험가")
                .description("모험을 시작한 자")
                .rarity(TitleRarity.COMMON)
                .positionType(TitlePosition.RIGHT)
                .colorCode("#808080")
                .acquisitionType(TitleAcquisitionType.LEVEL)
                .acquisitionCondition("레벨 1 달성")
                .isEquipped(true)
                .equippedPosition(TitlePosition.RIGHT)
                .acquiredAt(LocalDateTime.now())
                .build()
        );

        when(titleService.getUserTitles(anyString())).thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/achievements/titles/my")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("업적-06. 내 칭호 목록",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Achievement - Title")
                        .description("내가 보유한 칭호 목록 (LEFT: 형용사형, RIGHT: 명사형) (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("내 칭호 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("사용자 칭호 ID"),
                            fieldWithPath("value[].title_id").type(JsonFieldType.NUMBER).description("칭호 ID"),
                            fieldWithPath("value[].name").type(JsonFieldType.STRING).description("칭호 이름"),
                            fieldWithPath("value[].name_en").type(JsonFieldType.STRING).description("칭호 이름 (영어)").optional(),
                            fieldWithPath("value[].name_ar").type(JsonFieldType.STRING).description("칭호 이름 (아랍어)").optional(),
                            fieldWithPath("value[].display_name").type(JsonFieldType.STRING).description("표시 이름").optional(),
                            fieldWithPath("value[].description").type(JsonFieldType.STRING).description("설명").optional(),
                            fieldWithPath("value[].description_en").type(JsonFieldType.STRING).description("설명 (영어)").optional(),
                            fieldWithPath("value[].description_ar").type(JsonFieldType.STRING).description("설명 (아랍어)").optional(),
                            fieldWithPath("value[].rarity").type(JsonFieldType.STRING).description("희귀도"),
                            fieldWithPath("value[].position_type").type(JsonFieldType.STRING).description("위치 타입 (LEFT: 형용사형, RIGHT: 명사형)"),
                            fieldWithPath("value[].color_code").type(JsonFieldType.STRING).description("색상 코드").optional(),
                            fieldWithPath("value[].icon_url").type(JsonFieldType.STRING).description("아이콘 URL").optional(),
                            fieldWithPath("value[].acquisition_type").type(JsonFieldType.STRING).description("획득 방법").optional(),
                            fieldWithPath("value[].acquisition_condition").type(JsonFieldType.STRING).description("획득 조건").optional(),
                            fieldWithPath("value[].is_equipped").type(JsonFieldType.BOOLEAN).description("장착 여부"),
                            fieldWithPath("value[].equipped_position").type(JsonFieldType.STRING).description("장착 위치 (LEFT, RIGHT)").optional(),
                            fieldWithPath("value[].acquired_at").type(JsonFieldType.STRING).description("획득 일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/achievements/titles/{titleId}/equip : 칭호 장착")
    void equipTitleTest() throws Exception {
        // given
        UserTitleResponse response = UserTitleResponse.builder()
            .id(1L)
            .titleId(1L)
            .name("초보자")
            .displayName("초보자")
            .rarity(TitleRarity.COMMON)
            .isEquipped(true)
            .acquiredAt(LocalDateTime.now())
            .build();

        when(titleService.equipTitle(anyString(), anyLong())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/achievements/titles/{titleId}/equip", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("업적-07. 칭호 장착",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Achievement - Title")
                        .description("칭호 장착 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("titleId").type(SimpleType.NUMBER).description("칭호 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/achievements/titles/unequip-all : 모든 칭호 해제")
    void unequipAllTitlesTest() throws Exception {
        // given
        doNothing().when(titleService).unequipAllTitles(anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/achievements/titles/unequip-all")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("업적-08. 모든 칭호 해제",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Achievement - Title")
                        .description("현재 장착된 모든 칭호 해제 (LEFT, RIGHT 모두) (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/achievements/stats/my : 내 통계")
    void getMyStatsTest() throws Exception {
        // given
        UserStatsResponse response = UserStatsResponse.builder()
            .userId(MOCK_USER_ID)
            .totalMissionCompletions(50)
            .totalMissionFullCompletions(10)
            .totalGuildMissionCompletions(15)
            .currentStreak(7)
            .maxStreak(15)
            .lastActivityDate(LocalDate.now())
            .totalAchievementsCompleted(10)
            .totalTitlesAcquired(5)
            .rankingPoints(3500L)
            .build();

        when(userStatsService.getUserStats(anyString())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/achievements/stats/my")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("업적-09. 내 통계",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Achievement - Stats")
                        .description("내 활동 통계 조회 (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("통계 정보"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.total_mission_completions").type(JsonFieldType.NUMBER).description("총 미션 완료 수"),
                            fieldWithPath("value.total_mission_full_completions").type(JsonFieldType.NUMBER).description("총 미션 전체 완료 수"),
                            fieldWithPath("value.total_guild_mission_completions").type(JsonFieldType.NUMBER).description("총 길드 미션 완료 수"),
                            fieldWithPath("value.current_streak").type(JsonFieldType.NUMBER).description("현재 연속 활동일"),
                            fieldWithPath("value.max_streak").type(JsonFieldType.NUMBER).description("최대 연속 활동일"),
                            fieldWithPath("value.last_activity_date").type(JsonFieldType.STRING).description("마지막 활동일").optional(),
                            fieldWithPath("value.total_achievements_completed").type(JsonFieldType.NUMBER).description("총 업적 완료 수"),
                            fieldWithPath("value.total_titles_acquired").type(JsonFieldType.NUMBER).description("총 칭호 획득 수"),
                            fieldWithPath("value.ranking_points").type(JsonFieldType.NUMBER).description("랭킹 포인트"),
                            fieldWithPath("value.max_completed_mission_duration").type(JsonFieldType.NUMBER).description("최대 미션 수행 시간 (분)").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/achievements/mission-category/{missionCategoryId} : 미션 카테고리별 업적 조회")
    void getAchievementsByMissionCategoryIdTest() throws Exception {
        // given
        List<AchievementResponse> responses = List.of(
            AchievementResponse.builder()
                .id(1L)
                .name("건강 미션 마스터")
                .categoryCode("MISSION")
                .missionCategoryId(1L)
                .requiredCount(10)
                .rewardExp(500)
                .build()
        );

        when(achievementService.getAchievementsByMissionCategoryId(anyLong()))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/achievements/mission-category/{missionCategoryId}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/achievements/my/completed : 완료한 업적 조회")
    void getMyCompletedAchievementsTest() throws Exception {
        // given
        List<UserAchievementResponse> responses = List.of(
            UserAchievementResponse.builder()
                .id(1L)
                .achievementId(1L)
                .name("첫 미션 완료")
                .categoryCode("MISSION")
                .currentCount(1)
                .requiredCount(1)
                .progressPercent(100.0)
                .isCompleted(true)
                .completedAt(LocalDateTime.now())
                .build()
        );

        when(achievementService.getCompletedAchievements(anyString())).thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/achievements/my/completed")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/achievements/my/in-progress : 진행 중인 업적 조회")
    void getMyInProgressAchievementsTest() throws Exception {
        // given
        List<UserAchievementResponse> responses = List.of(
            UserAchievementResponse.builder()
                .id(2L)
                .achievementId(2L)
                .name("7일 연속 활동")
                .categoryCode("STREAK")
                .currentCount(3)
                .requiredCount(7)
                .progressPercent(42.86)
                .isCompleted(false)
                .build()
        );

        when(achievementService.getInProgressAchievements(anyString())).thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/achievements/my/in-progress")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/achievements/my/claimable : 수령 가능한 보상 조회")
    void getClaimableAchievementsTest() throws Exception {
        // given
        List<UserAchievementResponse> responses = List.of(
            UserAchievementResponse.builder()
                .id(1L)
                .achievementId(1L)
                .name("첫 미션 완료")
                .categoryCode("MISSION")
                .isCompleted(true)
                .isRewardClaimed(false)
                .rewardExp(100)
                .build()
        );

        when(achievementService.getClaimableAchievements(anyString())).thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/achievements/my/claimable")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/achievements/titles/position/{position} : 포지션별 칭호 조회")
    void getTitlesByPositionTest() throws Exception {
        // given
        List<TitleResponse> responses = List.of(
            TitleResponse.builder()
                .id(1L)
                .name("신입")
                .rarity(TitleRarity.COMMON)
                .positionType(TitlePosition.LEFT)
                .build()
        );

        when(titleService.getTitlesByPosition(any(TitlePosition.class))).thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/achievements/titles/position/{position}", "LEFT")
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/achievements/titles/unequip/{position} : 포지션별 칭호 해제")
    void unequipTitleByPositionTest() throws Exception {
        // given
        doNothing().when(titleService).unequipTitle(anyString(), any(TitlePosition.class));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/achievements/titles/unequip/{position}", "LEFT")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/achievements/titles/my/position/{position} : 내 포지션별 칭호 조회")
    void getMyTitlesByPositionTest() throws Exception {
        // given
        List<UserTitleResponse> responses = List.of(
            UserTitleResponse.builder()
                .id(1L)
                .titleId(1L)
                .name("신입")
                .rarity(TitleRarity.COMMON)
                .positionType(TitlePosition.LEFT)
                .isEquipped(true)
                .acquiredAt(LocalDateTime.now())
                .build()
        );

        when(titleService.getUserTitlesByPosition(anyString(), any(TitlePosition.class)))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/achievements/titles/my/position/{position}", "LEFT")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/achievements/titles/my/equipped : 장착된 칭호 조회")
    void getEquippedTitlesTest() throws Exception {
        // given
        UserTitleResponse leftTitle = UserTitleResponse.builder()
            .id(1L)
            .titleId(1L)
            .name("신입")
            .rarity(TitleRarity.COMMON)
            .positionType(TitlePosition.LEFT)
            .isEquipped(true)
            .equippedPosition(TitlePosition.LEFT)
            .build();

        when(titleService.getEquippedTitleByPosition(anyString(), any(TitlePosition.class)))
            .thenReturn(java.util.Optional.of(leftTitle));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/achievements/titles/my/equipped")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/achievements/titles/my/equipped/{position} : 포지션별 장착 칭호 조회")
    void getEquippedTitleByPositionTest() throws Exception {
        // given
        UserTitleResponse response = UserTitleResponse.builder()
            .id(1L)
            .titleId(1L)
            .name("신입")
            .rarity(TitleRarity.COMMON)
            .positionType(TitlePosition.LEFT)
            .isEquipped(true)
            .build();

        when(titleService.getEquippedTitleByPosition(anyString(), any(TitlePosition.class)))
            .thenReturn(java.util.Optional.of(response));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/achievements/titles/my/equipped/{position}", "LEFT")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
