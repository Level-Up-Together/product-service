package io.pinkspider.leveluptogethermvp.missionservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.global.component.LmObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.missionservice.api.exception.MissionIdempotencyExceptionHandler;
import io.pinkspider.leveluptogethermvp.missionservice.api.exception.RecommendedMissionException;
import io.pinkspider.leveluptogethermvp.missionservice.application.RecommendedMissionAdminService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionAdminResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionReorderRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
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
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = RecommendedMissionAdminInternalController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import({ControllerTestConfig.class, MissionIdempotencyExceptionHandler.class})
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("RecommendedMissionAdminInternalController (LUT-782)")
class RecommendedMissionAdminInternalControllerTest {

    private static final String TAG = "Recommended Mission - Admin Internal";

    @Autowired private MockMvc mockMvc;
    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean private RecommendedMissionAdminService adminService;

    private static RecommendedMissionAdminResponse item(long id, int order) {
        return new RecommendedMissionAdminResponse(
                id,
                "5분 독서",
                "Read for 5 minutes",
                "5分間読書",
                "اقرأ لمدة 5 دقائق",
                5L,
                MissionExecutionMode.TIMED,
                5,
                "📖",
                true,
                order,
                12,
                7,
                LocalDateTime.of(2026, 11, 11, 0, 0),
                null);
    }

    private static FieldDescriptor[] itemFields(String prefix) {
        return new FieldDescriptor[] {
            fieldWithPath(prefix + "id").type(JsonFieldType.NUMBER).description("추천 미션 ID"),
            fieldWithPath(prefix + "title_ko").type(JsonFieldType.STRING).description("이름(한국어)"),
            fieldWithPath(prefix + "title_en")
                    .type(JsonFieldType.STRING)
                    .description("이름(영어)")
                    .optional(),
            fieldWithPath(prefix + "title_ja")
                    .type(JsonFieldType.STRING)
                    .description("이름(일본어)")
                    .optional(),
            fieldWithPath(prefix + "title_ar")
                    .type(JsonFieldType.STRING)
                    .description("이름(아랍어)")
                    .optional(),
            fieldWithPath(prefix + "category_id")
                    .type(JsonFieldType.NUMBER)
                    .description("카테고리 ID")
                    .optional(),
            fieldWithPath(prefix + "execution_mode")
                    .type(JsonFieldType.STRING)
                    .description("TIMED(타이머)·SIMPLE(체크)"),
            fieldWithPath(prefix + "target_minutes")
                    .type(JsonFieldType.NUMBER)
                    .description("타이머 목표 시간(분), 체크는 null")
                    .optional(),
            fieldWithPath(prefix + "icon").type(JsonFieldType.STRING).description("카드 이모지"),
            fieldWithPath(prefix + "active").type(JsonFieldType.BOOLEAN).description("앱에 보이는가"),
            fieldWithPath(prefix + "sort_order").type(JsonFieldType.NUMBER).description("순서(1부터)"),
            fieldWithPath(prefix + "started_count")
                    .type(JsonFieldType.NUMBER)
                    .description("이 추천으로 시작한 미션 수 (유저가 지운 미션 제외)"),
            fieldWithPath(prefix + "completed_count")
                    .type(JsonFieldType.NUMBER)
                    .description("그중 한 번이라도 완료된 미션 수"),
            fieldWithPath(prefix + "created_at").type(JsonFieldType.STRING).description("작성 일시"),
            fieldWithPath(prefix + "modified_at")
                    .type(JsonFieldType.STRING)
                    .description("수정 일시")
                    .optional()
        };
    }

    private static FieldDescriptor[] withEnvelope(FieldDescriptor... fields) {
        FieldDescriptor[] all = new FieldDescriptor[fields.length + 2];
        all[0] = fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드");
        all[1] = fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지");
        System.arraycopy(fields, 0, all, 2, fields.length);
        return all;
    }

    @Test
    @DisplayName("GET /api/internal/recommended-missions : 전부, 순서대로 + 성과")
    void list() throws Exception {
        when(adminService.getAll()).thenReturn(List.of(item(1, 1), item(2, 2)));

        mockMvc.perform(RestDocumentationRequestBuilders.get("/api/internal/recommended-missions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value[0].started_count").value(12))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "추천미션-내부API-01. 목록",
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description(
                                                        "추천 미션 전부(꺼진 것 포함) 순서대로 + 성과 (LUT-782)")
                                                .responseFields(
                                                        withEnvelope(itemFields("value[].")))
                                                .build())));
    }

    @Test
    @DisplayName("GET /{id} 단건 · 없으면 404 050501")
    void getOne() throws Exception {
        when(adminService.get(1L)).thenReturn(item(1, 1));
        when(adminService.get(9L)).thenThrow(RecommendedMissionException.notFound());

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                "/api/internal/recommended-missions/{id}", 1L))
                .andExpect(status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "추천미션-내부API-02. 단건",
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description("추천 미션 1개 — 없으면 404(050501)")
                                                .pathParameters(
                                                        parameterWithName("id")
                                                                .description("추천 미션 ID"))
                                                .responseFields(withEnvelope(itemFields("value.")))
                                                .build())));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                "/api/internal/recommended-missions/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("050501"));
    }

    @Test
    @DisplayName("POST 작성 · PUT 수정 · 입력 오류 400")
    void createAndUpdate() throws Exception {
        RecommendedMissionRequest request =
                new RecommendedMissionRequest(
                        "10분 산책",
                        "Take a 10-minute walk",
                        null,
                        null,
                        1L,
                        MissionExecutionMode.TIMED,
                        10,
                        "🚶",
                        null);
        when(adminService.create(any(RecommendedMissionRequest.class))).thenReturn(item(7, 7));
        when(adminService.update(eq(7L), any(RecommendedMissionRequest.class)))
                .thenReturn(item(7, 7));

        FieldDescriptor[] requestFields = {
            fieldWithPath("title_ko").type(JsonFieldType.STRING).description("이름(한국어, 필수, 100자)"),
            fieldWithPath("title_en").type(JsonFieldType.STRING).description("이름(영어)").optional(),
            fieldWithPath("title_ja").type(JsonFieldType.STRING).description("이름(일본어)").optional(),
            fieldWithPath("title_ar").type(JsonFieldType.STRING).description("이름(아랍어)").optional(),
            fieldWithPath("category_id")
                    .type(JsonFieldType.NUMBER)
                    .description("카테고리 ID — 켜진 카테고리만(아니면 400 050502)")
                    .optional(),
            fieldWithPath("execution_mode")
                    .type(JsonFieldType.STRING)
                    .description("TIMED·SIMPLE (필수)"),
            fieldWithPath("target_minutes")
                    .type(JsonFieldType.NUMBER)
                    .description("타이머 목표 시간 1~240분 (TIMED 필수, SIMPLE 은 무시)")
                    .optional(),
            fieldWithPath("icon").type(JsonFieldType.STRING).description("카드 이모지 (필수, 16자)"),
            fieldWithPath("active")
                    .type(JsonFieldType.BOOLEAN)
                    .description("비우면 작성 = 켬, 수정 = 그대로")
                    .optional()
        };

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/internal/recommended-missions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "추천미션-내부API-03. 작성",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description("추천 미션 작성 — 맨 뒤 순서")
                                                .requestFields(requestFields)
                                                .responseFields(withEnvelope(itemFields("value.")))
                                                .build())));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.put(
                                        "/api/internal/recommended-missions/{id}", 7L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "추천미션-내부API-04. 수정",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description("추천 미션 수정 — 체크로 바꾸면 목표 시간을 버린다")
                                                .pathParameters(
                                                        parameterWithName("id")
                                                                .description("추천 미션 ID"))
                                                .requestFields(requestFields)
                                                .responseFields(withEnvelope(itemFields("value.")))
                                                .build())));

        when(adminService.create(any(RecommendedMissionRequest.class)))
                .thenThrow(RecommendedMissionException.targetMinutesRequired());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/internal/recommended-missions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("050502"));
    }

    @Test
    @DisplayName("DELETE · PATCH toggle-active · PUT reorder")
    void deleteToggleReorder() throws Exception {
        when(adminService.toggleActive(3L)).thenReturn(item(3, 3));
        when(adminService.reorder(List.of(2L, 1L))).thenReturn(List.of(item(2, 1), item(1, 2)));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.delete(
                                "/api/internal/recommended-missions/{id}", 3L))
                .andExpect(status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "추천미션-내부API-05. 삭제",
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description("추천 미션 삭제 — 이미 만든 유저 미션은 그대로")
                                                .pathParameters(
                                                        parameterWithName("id")
                                                                .description("추천 미션 ID"))
                                                .build())));
        verify(adminService).delete(3L);

        mockMvc.perform(
                        RestDocumentationRequestBuilders.patch(
                                "/api/internal/recommended-missions/{id}/toggle-active", 3L))
                .andExpect(status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "추천미션-내부API-06. 활성 토글",
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description("앱에 보이기·숨기기")
                                                .pathParameters(
                                                        parameterWithName("id")
                                                                .description("추천 미션 ID"))
                                                .responseFields(withEnvelope(itemFields("value.")))
                                                .build())));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.put(
                                        "/api/internal/recommended-missions/reorder")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new RecommendedMissionReorderRequest(
                                                        List.of(2L, 1L)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value[0].id").value(2))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "추천미션-내부API-07. 순서 바꾸기",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description(
                                                        "전체 ID 를 새 순서대로 — 빠짐·중복·없는 ID 는"
                                                                + " 400(050502)")
                                                .requestFields(
                                                        fieldWithPath("ordered_ids")
                                                                .type(JsonFieldType.ARRAY)
                                                                .description("추천 미션 ID 전부, 새 순서대로"))
                                                .responseFields(
                                                        withEnvelope(itemFields("value[].")))
                                                .build())));
    }
}
