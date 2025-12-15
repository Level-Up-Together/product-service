package io.pinkspider.leveluptogethermvp.userservice.achievement.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
import io.pinkspider.leveluptogethermvp.userservice.achievement.application.RankingService;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.RankingResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = RankingController.class,
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
class RankingControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private RankingService rankingService;

    private static final String MOCK_USER_ID = "test-user-123";

    private List<RankingResponse> createMockRankings() {
        return List.of(
            RankingResponse.builder()
                .rank(1L)
                .userId("user-001")
                .rankingPoints(15000L)
                .totalMissionCompletions(100)
                .maxStreak(30)
                .totalAchievementsCompleted(25)
                .nickname("최강자")
                .userLevel(50)
                .equippedTitleName("레전드")
                .build(),
            RankingResponse.builder()
                .rank(2L)
                .userId("user-002")
                .rankingPoints(12000L)
                .totalMissionCompletions(80)
                .maxStreak(25)
                .totalAchievementsCompleted(20)
                .nickname("도전왕")
                .userLevel(45)
                .equippedTitleName("챔피언")
                .build()
        );
    }

    @Test
    @DisplayName("GET /api/v1/rankings : 종합 랭킹 조회")
    void getOverallRankingTest() throws Exception {
        // given
        Page<RankingResponse> page = new PageImpl<>(
            createMockRankings(), PageRequest.of(0, 20), 2);

        when(rankingService.getOverallRanking(any(Pageable.class))).thenReturn(page);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/rankings")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("랭킹-01. 종합 랭킹 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Ranking")
                        .description("종합 랭킹 조회")
                        .queryParameters(
                            parameterWithName("page").type(SimpleType.NUMBER).description("페이지 번호 (0부터 시작)").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER).description("페이지 크기").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("페이징된 랭킹"),
                            fieldWithPath("value.content[]").type(JsonFieldType.ARRAY).description("랭킹 목록"),
                            fieldWithPath("value.content[].rank").type(JsonFieldType.NUMBER).description("순위"),
                            fieldWithPath("value.content[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.content[].ranking_points").type(JsonFieldType.NUMBER).description("랭킹 포인트"),
                            fieldWithPath("value.content[].total_mission_completions").type(JsonFieldType.NUMBER).description("총 미션 완료 수"),
                            fieldWithPath("value.content[].max_streak").type(JsonFieldType.NUMBER).description("최대 연속 활동일"),
                            fieldWithPath("value.content[].total_achievements_completed").type(JsonFieldType.NUMBER).description("총 업적 완료 수"),
                            fieldWithPath("value.content[].nickname").type(JsonFieldType.STRING).description("닉네임").optional(),
                            fieldWithPath("value.content[].user_level").type(JsonFieldType.NUMBER).description("사용자 레벨").optional(),
                            fieldWithPath("value.content[].equipped_title_name").type(JsonFieldType.STRING).description("장착 칭호").optional(),
                            fieldWithPath("value.pageable").type(JsonFieldType.OBJECT).description("페이징 정보").optional(),
                            fieldWithPath("value.pageable.page_number").type(JsonFieldType.NUMBER).description("페이지 번호").optional(),
                            fieldWithPath("value.pageable.page_size").type(JsonFieldType.NUMBER).description("페이지 크기").optional(),
                            fieldWithPath("value.pageable.sort").type(JsonFieldType.OBJECT).description("정렬 정보").optional(),
                            fieldWithPath("value.pageable.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음").optional(),
                            fieldWithPath("value.pageable.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬됨").optional(),
                            fieldWithPath("value.pageable.sort.unsorted").type(JsonFieldType.BOOLEAN).description("미정렬").optional(),
                            fieldWithPath("value.pageable.offset").type(JsonFieldType.NUMBER).description("오프셋").optional(),
                            fieldWithPath("value.pageable.paged").type(JsonFieldType.BOOLEAN).description("페이징 여부").optional(),
                            fieldWithPath("value.pageable.unpaged").type(JsonFieldType.BOOLEAN).description("비페이징 여부").optional(),
                            fieldWithPath("value.total_elements").type(JsonFieldType.NUMBER).description("전체 요소 수").optional(),
                            fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수").optional(),
                            fieldWithPath("value.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부").optional(),
                            fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기").optional(),
                            fieldWithPath("value.number").type(JsonFieldType.NUMBER).description("현재 페이지 번호").optional(),
                            fieldWithPath("value.sort").type(JsonFieldType.OBJECT).description("정렬 정보").optional(),
                            fieldWithPath("value.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음").optional(),
                            fieldWithPath("value.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬됨").optional(),
                            fieldWithPath("value.sort.unsorted").type(JsonFieldType.BOOLEAN).description("미정렬").optional(),
                            fieldWithPath("value.first").type(JsonFieldType.BOOLEAN).description("첫 페이지 여부").optional(),
                            fieldWithPath("value.number_of_elements").type(JsonFieldType.NUMBER).description("현재 페이지 요소 수").optional(),
                            fieldWithPath("value.empty").type(JsonFieldType.BOOLEAN).description("비어있음 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/rankings/missions : 미션 완료 랭킹")
    void getMissionRankingTest() throws Exception {
        // given
        Page<RankingResponse> page = new PageImpl<>(
            createMockRankings(), PageRequest.of(0, 20), 2);

        when(rankingService.getMissionCompletionRanking(any(Pageable.class))).thenReturn(page);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/rankings/missions")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("랭킹-02. 미션 완료 랭킹",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Ranking")
                        .description("미션 완료 수 기준 랭킹")
                        .queryParameters(
                            parameterWithName("page").type(SimpleType.NUMBER).description("페이지 번호").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER).description("페이지 크기").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/rankings/streaks : 연속 활동 랭킹")
    void getStreakRankingTest() throws Exception {
        // given
        Page<RankingResponse> page = new PageImpl<>(
            createMockRankings(), PageRequest.of(0, 20), 2);

        when(rankingService.getStreakRanking(any(Pageable.class))).thenReturn(page);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/rankings/streaks")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("랭킹-03. 연속 활동 랭킹",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Ranking")
                        .description("연속 활동일 기준 랭킹")
                        .queryParameters(
                            parameterWithName("page").type(SimpleType.NUMBER).description("페이지 번호").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER).description("페이지 크기").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/rankings/achievements : 업적 달성 랭킹")
    void getAchievementRankingTest() throws Exception {
        // given
        Page<RankingResponse> page = new PageImpl<>(
            createMockRankings(), PageRequest.of(0, 20), 2);

        when(rankingService.getAchievementRanking(any(Pageable.class))).thenReturn(page);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/rankings/achievements")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("랭킹-04. 업적 달성 랭킹",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Ranking")
                        .description("업적 달성 수 기준 랭킹")
                        .queryParameters(
                            parameterWithName("page").type(SimpleType.NUMBER).description("페이지 번호").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER).description("페이지 크기").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/rankings/my : 내 랭킹 조회")
    void getMyRankingTest() throws Exception {
        // given
        RankingResponse response = RankingResponse.builder()
            .rank(15L)
            .userId(MOCK_USER_ID)
            .rankingPoints(5000L)
            .totalMissionCompletions(50)
            .maxStreak(15)
            .totalAchievementsCompleted(10)
            .nickname("테스트유저")
            .userLevel(25)
            .equippedTitleName("도전자")
            .build();

        when(rankingService.getMyRanking(anyString())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/rankings/my")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("랭킹-05. 내 랭킹 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Ranking")
                        .description("내 랭킹 정보 조회 (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("내 랭킹 정보"),
                            fieldWithPath("value.rank").type(JsonFieldType.NUMBER).description("순위"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.ranking_points").type(JsonFieldType.NUMBER).description("랭킹 포인트"),
                            fieldWithPath("value.total_mission_completions").type(JsonFieldType.NUMBER).description("총 미션 완료 수"),
                            fieldWithPath("value.max_streak").type(JsonFieldType.NUMBER).description("최대 연속 활동일"),
                            fieldWithPath("value.total_achievements_completed").type(JsonFieldType.NUMBER).description("총 업적 완료 수"),
                            fieldWithPath("value.nickname").type(JsonFieldType.STRING).description("닉네임").optional(),
                            fieldWithPath("value.user_level").type(JsonFieldType.NUMBER).description("사용자 레벨").optional(),
                            fieldWithPath("value.equipped_title_name").type(JsonFieldType.STRING).description("장착 칭호").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/rankings/nearby : 주변 랭킹 조회")
    void getNearbyRankingTest() throws Exception {
        // given
        List<RankingResponse> responses = List.of(
            RankingResponse.builder().rank(13L).userId("user-013").rankingPoints(5200L).build(),
            RankingResponse.builder().rank(14L).userId("user-014").rankingPoints(5100L).build(),
            RankingResponse.builder().rank(15L).userId(MOCK_USER_ID).rankingPoints(5000L).build(),
            RankingResponse.builder().rank(16L).userId("user-016").rankingPoints(4900L).build(),
            RankingResponse.builder().rank(17L).userId("user-017").rankingPoints(4800L).build()
        );

        when(rankingService.getNearbyRanking(anyString(), anyInt())).thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/rankings/nearby")
                .with(user(MOCK_USER_ID))
                .param("range", "5")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("랭킹-06. 주변 랭킹 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Ranking")
                        .description("내 위아래 N명의 랭킹 조회 (JWT 토큰 인증 필요)")
                        .queryParameters(
                            parameterWithName("range").type(SimpleType.NUMBER).description("위아래 표시할 인원 수 (기본값: 5)").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("주변 랭킹 목록"),
                            fieldWithPath("value[].rank").type(JsonFieldType.NUMBER).description("순위"),
                            fieldWithPath("value[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value[].ranking_points").type(JsonFieldType.NUMBER).description("랭킹 포인트"),
                            fieldWithPath("value[].total_mission_completions").type(JsonFieldType.NUMBER).description("총 미션 완료 수").optional(),
                            fieldWithPath("value[].max_streak").type(JsonFieldType.NUMBER).description("최대 연속 활동일").optional(),
                            fieldWithPath("value[].total_achievements_completed").type(JsonFieldType.NUMBER).description("총 업적 완료 수").optional(),
                            fieldWithPath("value[].nickname").type(JsonFieldType.STRING).description("닉네임").optional(),
                            fieldWithPath("value[].user_level").type(JsonFieldType.NUMBER).description("사용자 레벨").optional(),
                            fieldWithPath("value[].equipped_title_name").type(JsonFieldType.STRING).description("장착 칭호").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
