package io.pinkspider.leveluptogethermvp.gamificationservice.season.api;

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

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import io.pinkspider.global.component.LmObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.application.SeasonAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonAdminResponse;
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

@WebMvcTest(controllers = SeasonAdminInternalController.class,
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
class SeasonAdminInternalControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean
    private SeasonAdminService seasonAdminService;

    private SeasonAdminResponse createSeasonResponse(Long id, String title) {
        return SeasonAdminResponse.builder()
            .id(id)
            .title(title)
            .description("시즌 설명")
            .startAt(LocalDateTime.of(2025, 1, 1, 0, 0))
            .endAt(LocalDateTime.of(2025, 3, 31, 23, 59))
            .isActive(true)
            .rewardTitleId(1L)
            .rewardTitleName("시즌 챔피언")
            .sortOrder(0)
            .status("ENDED")
            .statusName("종료")
            .createdBy("admin")
            .modifiedBy("admin")
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("GET /api/internal/seasons : 시즌 목록 페이징 조회")
    void searchSeasons() throws Exception {
        // given
        SeasonAdminPageResponse response = new SeasonAdminPageResponse(
            List.of(createSeasonResponse(1L, "2025 S1 시즌")),
            1, 1L, 0, 20, true, true
        );
        when(seasonAdminService.searchSeasons(any(), any())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/internal/seasons")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("시즌-내부-01. 시즌 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Season - Admin Internal")
                        .description("시즌 목록 페이징 조회 (Admin 내부 API)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("응답 데이터"),
                            fieldWithPath("value.content[]").type(JsonFieldType.ARRAY).description("시즌 목록"),
                            fieldWithPath("value.content[].id").type(JsonFieldType.NUMBER).description("시즌 ID"),
                            fieldWithPath("value.content[].title").type(JsonFieldType.STRING).description("시즌 타이틀"),
                            fieldWithPath("value.content[].description").type(JsonFieldType.STRING).description("시즌 설명").optional(),
                            fieldWithPath("value.content[].start_at").type(JsonFieldType.STRING).description("시작 일시"),
                            fieldWithPath("value.content[].end_at").type(JsonFieldType.STRING).description("종료 일시"),
                            fieldWithPath("value.content[].is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부"),
                            fieldWithPath("value.content[].reward_title_id").type(JsonFieldType.NUMBER).description("보상 칭호 ID").optional(),
                            fieldWithPath("value.content[].reward_title_name").type(JsonFieldType.STRING).description("보상 칭호명").optional(),
                            fieldWithPath("value.content[].sort_order").type(JsonFieldType.NUMBER).description("정렬 순서"),
                            fieldWithPath("value.content[].status").type(JsonFieldType.STRING).description("시즌 상태"),
                            fieldWithPath("value.content[].status_name").type(JsonFieldType.STRING).description("시즌 상태명"),
                            fieldWithPath("value.content[].created_by").type(JsonFieldType.STRING).description("생성자").optional(),
                            fieldWithPath("value.content[].modified_by").type(JsonFieldType.STRING).description("수정자").optional(),
                            fieldWithPath("value.content[].created_at").type(JsonFieldType.STRING).description("생성 일시").optional(),
                            fieldWithPath("value.content[].modified_at").type(JsonFieldType.STRING).description("수정 일시").optional(),
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
    @DisplayName("GET /api/internal/seasons/all : 전체 시즌 목록 조회")
    void getAllSeasons() throws Exception {
        // given
        List<SeasonAdminResponse> response = List.of(
            createSeasonResponse(1L, "2025 S1 시즌"),
            createSeasonResponse(2L, "2025 S2 시즌")
        );
        when(seasonAdminService.getAllSeasons()).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/internal/seasons/all")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("시즌-내부-02. 전체 시즌 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Season - Admin Internal")
                        .description("전체 시즌 목록 조회 (페이징 없음)")
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/internal/seasons/current : 현재 시즌 조회")
    void getCurrentSeason() throws Exception {
        // given
        SeasonAdminResponse response = SeasonAdminResponse.builder()
            .id(1L)
            .title("2025 S1 시즌")
            .startAt(LocalDateTime.now().minusDays(30))
            .endAt(LocalDateTime.now().plusDays(60))
            .isActive(true)
            .status("ACTIVE")
            .statusName("진행 중")
            .build();
        when(seasonAdminService.getCurrentSeason()).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/internal/seasons/current")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("시즌-내부-03. 현재 시즌 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Season - Admin Internal")
                        .description("현재 활성 시즌 조회")
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/internal/seasons/upcoming : 예정 시즌 목록 조회")
    void getUpcomingSeasons() throws Exception {
        // given
        List<SeasonAdminResponse> response = List.of(
            SeasonAdminResponse.builder()
                .id(2L)
                .title("2025 S2 시즌")
                .startAt(LocalDateTime.now().plusDays(30))
                .endAt(LocalDateTime.now().plusDays(120))
                .isActive(true)
                .status("UPCOMING")
                .statusName("예정")
                .build()
        );
        when(seasonAdminService.getUpcomingSeasons()).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/internal/seasons/upcoming")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("시즌-내부-04. 예정 시즌 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Season - Admin Internal")
                        .description("예정된 시즌 목록 조회")
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/internal/seasons/{id} : 시즌 단건 조회")
    void getSeason() throws Exception {
        // given
        SeasonAdminResponse response = createSeasonResponse(1L, "2025 S1 시즌");
        when(seasonAdminService.getSeason(1L)).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/internal/seasons/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("시즌-내부-05. 시즌 단건 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Season - Admin Internal")
                        .description("시즌 ID로 단건 조회")
                        .pathParameters(
                            parameterWithName("id").type(SimpleType.NUMBER).description("시즌 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/internal/seasons : 시즌 생성")
    void createSeason() throws Exception {
        // given
        SeasonAdminResponse response = createSeasonResponse(1L, "2025 S1 시즌");
        when(seasonAdminService.createSeason(any(SeasonAdminRequest.class))).thenReturn(response);

        SeasonAdminRequest request = new SeasonAdminRequest(
            "2025 S1 시즌",
            "첫 번째 시즌",
            LocalDateTime.of(2025, 1, 1, 0, 0),
            LocalDateTime.of(2025, 3, 31, 23, 59),
            true,
            null,
            null,
            0,
            "admin",
            null
        );

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/internal/seasons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("시즌-내부-06. 시즌 생성",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Season - Admin Internal")
                        .description("새 시즌 생성")
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PUT /api/internal/seasons/{id} : 시즌 수정")
    void updateSeason() throws Exception {
        // given
        SeasonAdminResponse response = createSeasonResponse(1L, "수정된 시즌");
        when(seasonAdminService.updateSeason(anyLong(), any(SeasonAdminRequest.class))).thenReturn(response);

        SeasonAdminRequest request = new SeasonAdminRequest(
            "수정된 시즌",
            "수정된 설명",
            LocalDateTime.of(2025, 1, 1, 0, 0),
            LocalDateTime.of(2025, 3, 31, 23, 59),
            true,
            null,
            null,
            0,
            null,
            "admin"
        );

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.put("/api/internal/seasons/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("시즌-내부-07. 시즌 수정",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Season - Admin Internal")
                        .description("시즌 정보 수정")
                        .pathParameters(
                            parameterWithName("id").type(SimpleType.NUMBER).description("시즌 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/internal/seasons/{id} : 시즌 삭제")
    void deleteSeason() throws Exception {
        // given
        doNothing().when(seasonAdminService).deleteSeason(anyLong());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/internal/seasons/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("시즌-내부-08. 시즌 삭제",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Season - Admin Internal")
                        .description("시즌 삭제")
                        .pathParameters(
                            parameterWithName("id").type(SimpleType.NUMBER).description("시즌 ID")
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
    @DisplayName("PATCH /api/internal/seasons/{id}/toggle : 시즌 활성/비활성 토글")
    void toggleActive() throws Exception {
        // given
        SeasonAdminResponse response = createSeasonResponse(1L, "2025 S1 시즌");
        when(seasonAdminService.toggleActive(anyLong())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.patch("/api/internal/seasons/{id}/toggle", 1L)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("시즌-내부-09. 시즌 활성 토글",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Season - Admin Internal")
                        .description("시즌 활성/비활성 상태 전환")
                        .pathParameters(
                            parameterWithName("id").type(SimpleType.NUMBER).description("시즌 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
