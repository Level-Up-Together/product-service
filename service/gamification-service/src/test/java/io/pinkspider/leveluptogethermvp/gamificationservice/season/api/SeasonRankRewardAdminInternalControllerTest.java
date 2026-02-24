package io.pinkspider.leveluptogethermvp.gamificationservice.season.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import io.pinkspider.global.component.LmObjectMapper;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.application.SeasonRankRewardAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.CreateSeasonRankRewardAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonRankRewardAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonRewardHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonRewardHistoryAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonRewardStatsAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.UpdateSeasonRankRewardAdminRequest;
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

@WebMvcTest(controllers = SeasonRankRewardAdminInternalController.class,
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
class SeasonRankRewardAdminInternalControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean
    private SeasonRankRewardAdminService rankRewardAdminService;

    private SeasonRankRewardAdminResponse createRankRewardResponse(Long id, Long seasonId) {
        return new SeasonRankRewardAdminResponse(
            id,
            seasonId,
            1,
            3,
            "1-3위",
            null,
            null,
            "전체",
            10L,
            "시즌 챔피언",
            "LEGENDARY",
            "RIGHT",
            0,
            true
        );
    }

    @Test
    @DisplayName("GET /api/internal/seasons/{seasonId}/rank-rewards : 시즌 순위 보상 목록 조회")
    void getRankRewards() throws Exception {
        // given
        List<SeasonRankRewardAdminResponse> response = List.of(
            createRankRewardResponse(1L, 1L),
            createRankRewardResponse(2L, 1L)
        );
        when(rankRewardAdminService.getSeasonRankRewards(anyLong())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/internal/seasons/{seasonId}/rank-rewards", 1L)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("시즌-순위보상-01. 순위 보상 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Season Rank Reward - Admin Internal")
                        .description("시즌 순위 보상 목록 조회 (Admin 내부 API)")
                        .pathParameters(
                            parameterWithName("seasonId").type(SimpleType.NUMBER).description("시즌 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("순위 보상 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("보상 ID"),
                            fieldWithPath("value[].season_id").type(JsonFieldType.NUMBER).description("시즌 ID"),
                            fieldWithPath("value[].rank_start").type(JsonFieldType.NUMBER).description("시작 순위"),
                            fieldWithPath("value[].rank_end").type(JsonFieldType.NUMBER).description("종료 순위"),
                            fieldWithPath("value[].rank_range_display").type(JsonFieldType.STRING).description("순위 구간 표시"),
                            fieldWithPath("value[].category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value[].category_name").type(JsonFieldType.STRING).description("카테고리명").optional(),
                            fieldWithPath("value[].ranking_type_display").type(JsonFieldType.STRING).description("랭킹 유형 표시").optional(),
                            fieldWithPath("value[].title_id").type(JsonFieldType.NUMBER).description("칭호 ID"),
                            fieldWithPath("value[].title_name").type(JsonFieldType.STRING).description("칭호명"),
                            fieldWithPath("value[].title_rarity").type(JsonFieldType.STRING).description("칭호 등급").optional(),
                            fieldWithPath("value[].title_position_type").type(JsonFieldType.STRING).description("칭호 위치 타입").optional(),
                            fieldWithPath("value[].sort_order").type(JsonFieldType.NUMBER).description("정렬 순서"),
                            fieldWithPath("value[].is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/internal/seasons/{seasonId}/rank-rewards : 순위 보상 단건 생성")
    void createRankReward() throws Exception {
        // given
        SeasonRankRewardAdminResponse response = createRankRewardResponse(1L, 1L);
        when(rankRewardAdminService.createRankReward(anyLong(), any(CreateSeasonRankRewardAdminRequest.class)))
            .thenReturn(response);

        CreateSeasonRankRewardAdminRequest request = new CreateSeasonRankRewardAdminRequest(
            1, 3, null, null, null, "시즌 챔피언", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 0
        );

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/internal/seasons/{seasonId}/rank-rewards", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("시즌-순위보상-02. 순위 보상 생성",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Season Rank Reward - Admin Internal")
                        .description("시즌 순위 보상 단건 생성")
                        .pathParameters(
                            parameterWithName("seasonId").type(SimpleType.NUMBER).description("시즌 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/internal/seasons/{seasonId}/rank-rewards/bulk : 순위 보상 벌크 생성")
    void createBulkRankRewards() throws Exception {
        // given
        List<SeasonRankRewardAdminResponse> response = List.of(
            createRankRewardResponse(1L, 1L),
            createRankRewardResponse(2L, 1L)
        );
        when(rankRewardAdminService.createBulkRankRewards(anyLong(), any())).thenReturn(response);

        List<CreateSeasonRankRewardAdminRequest> requests = List.of(
            new CreateSeasonRankRewardAdminRequest(1, 1, null, null, null, "골드 챔피언", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 0),
            new CreateSeasonRankRewardAdminRequest(2, 3, null, null, null, "실버 챔피언", TitleRarity.EPIC, TitlePosition.RIGHT, 1)
        );

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/internal/seasons/{seasonId}/rank-rewards/bulk", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requests))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("시즌-순위보상-03. 순위 보상 벌크 생성",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Season Rank Reward - Admin Internal")
                        .description("시즌 순위 보상 다건 일괄 생성")
                        .pathParameters(
                            parameterWithName("seasonId").type(SimpleType.NUMBER).description("시즌 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PUT /api/internal/seasons/{seasonId}/rank-rewards/{rewardId} : 순위 보상 수정")
    void updateRankReward() throws Exception {
        // given
        SeasonRankRewardAdminResponse response = createRankRewardResponse(1L, 1L);
        when(rankRewardAdminService.updateRankReward(anyLong(), any(UpdateSeasonRankRewardAdminRequest.class)))
            .thenReturn(response);

        UpdateSeasonRankRewardAdminRequest request = new UpdateSeasonRankRewardAdminRequest(
            1, 3, null, null, 10L, "수정된 챔피언", TitleRarity.LEGENDARY, TitlePosition.RIGHT, 0
        );

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.put("/api/internal/seasons/{seasonId}/rank-rewards/{rewardId}", 1L, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("시즌-순위보상-04. 순위 보상 수정",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Season Rank Reward - Admin Internal")
                        .description("시즌 순위 보상 수정")
                        .pathParameters(
                            parameterWithName("seasonId").type(SimpleType.NUMBER).description("시즌 ID"),
                            parameterWithName("rewardId").type(SimpleType.NUMBER).description("보상 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/internal/seasons/{seasonId}/rank-rewards/{rewardId} : 순위 보상 삭제")
    void deleteRankReward() throws Exception {
        // given
        doNothing().when(rankRewardAdminService).deleteRankReward(anyLong());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/internal/seasons/{seasonId}/rank-rewards/{rewardId}", 1L, 1L)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("시즌-순위보상-05. 순위 보상 삭제",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Season Rank Reward - Admin Internal")
                        .description("시즌 순위 보상 삭제 (비활성 처리)")
                        .pathParameters(
                            parameterWithName("seasonId").type(SimpleType.NUMBER).description("시즌 ID"),
                            parameterWithName("rewardId").type(SimpleType.NUMBER).description("보상 ID")
                        )
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
    @DisplayName("GET /api/internal/seasons/{seasonId}/rank-rewards/history : 보상 지급 이력 조회")
    void getRewardHistory() throws Exception {
        // given
        SeasonRewardHistoryAdminResponse historyResponse = new SeasonRewardHistoryAdminResponse(
            1L, 1L, "user-001", 1, 5000L, 10L, "시즌 챔피언", null, null,
            "SUCCESS", "성공", null, LocalDateTime.now()
        );
        SeasonRewardHistoryAdminPageResponse response = new SeasonRewardHistoryAdminPageResponse(
            List.of(historyResponse), 1, 1L, 0, 20, true, true
        );
        when(rankRewardAdminService.getRewardHistory(anyLong(), any())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/internal/seasons/{seasonId}/rank-rewards/history", 1L)
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("시즌-순위보상-06. 보상 지급 이력 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Season Rank Reward - Admin Internal")
                        .description("시즌 보상 지급 이력 페이징 조회")
                        .pathParameters(
                            parameterWithName("seasonId").type(SimpleType.NUMBER).description("시즌 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("응답 데이터"),
                            fieldWithPath("value.content[]").type(JsonFieldType.ARRAY).description("이력 목록"),
                            fieldWithPath("value.content[].id").type(JsonFieldType.NUMBER).description("이력 ID"),
                            fieldWithPath("value.content[].season_id").type(JsonFieldType.NUMBER).description("시즌 ID"),
                            fieldWithPath("value.content[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.content[].final_rank").type(JsonFieldType.NUMBER).description("최종 순위"),
                            fieldWithPath("value.content[].total_exp").type(JsonFieldType.NUMBER).description("총 경험치"),
                            fieldWithPath("value.content[].title_id").type(JsonFieldType.NUMBER).description("지급 칭호 ID").optional(),
                            fieldWithPath("value.content[].title_name").type(JsonFieldType.STRING).description("지급 칭호명").optional(),
                            fieldWithPath("value.content[].category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.content[].category_name").type(JsonFieldType.STRING).description("카테고리명").optional(),
                            fieldWithPath("value.content[].status").type(JsonFieldType.STRING).description("보상 상태"),
                            fieldWithPath("value.content[].status_description").type(JsonFieldType.STRING).description("보상 상태 설명"),
                            fieldWithPath("value.content[].error_message").type(JsonFieldType.STRING).description("오류 메시지").optional(),
                            fieldWithPath("value.content[].created_at").type(JsonFieldType.STRING).description("생성 일시"),
                            fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                            fieldWithPath("value.total_elements").type(JsonFieldType.NUMBER).description("전체 데이터 수"),
                            fieldWithPath("value.number").type(JsonFieldType.NUMBER).description("현재 페이지 번호"),
                            fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.first").type(JsonFieldType.BOOLEAN).description("첫 번째 페이지 여부"),
                            fieldWithPath("value.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/internal/seasons/{seasonId}/rank-rewards/stats : 보상 통계 조회")
    void getRewardStats() throws Exception {
        // given
        SeasonRewardStatsAdminResponse response = new SeasonRewardStatsAdminResponse(
            1L, 5, 90, 3, 2, 100, true
        );
        when(rankRewardAdminService.getRewardStats(anyLong())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/internal/seasons/{seasonId}/rank-rewards/stats", 1L)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("시즌-순위보상-07. 보상 통계 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Season Rank Reward - Admin Internal")
                        .description("시즌 보상 지급 통계 조회")
                        .pathParameters(
                            parameterWithName("seasonId").type(SimpleType.NUMBER).description("시즌 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("응답 데이터"),
                            fieldWithPath("value.season_id").type(JsonFieldType.NUMBER).description("시즌 ID"),
                            fieldWithPath("value.pending_count").type(JsonFieldType.NUMBER).description("대기 건수"),
                            fieldWithPath("value.success_count").type(JsonFieldType.NUMBER).description("성공 건수"),
                            fieldWithPath("value.failed_count").type(JsonFieldType.NUMBER).description("실패 건수"),
                            fieldWithPath("value.skipped_count").type(JsonFieldType.NUMBER).description("건너뜀 건수"),
                            fieldWithPath("value.total_count").type(JsonFieldType.NUMBER).description("총 건수"),
                            fieldWithPath("value.is_processed").type(JsonFieldType.BOOLEAN).description("처리 완료 여부")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
