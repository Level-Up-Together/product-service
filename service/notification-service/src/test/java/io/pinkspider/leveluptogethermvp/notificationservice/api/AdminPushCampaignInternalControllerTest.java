package io.pinkspider.leveluptogethermvp.notificationservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.notificationservice.application.AdminPushCampaignService;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.AdminPushCampaignPageResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.AdminPushCampaignRequest;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.AdminPushCampaignResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.AdminPushCampaignStatus;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.AdminPushTargetType;
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

@WebMvcTest(controllers = AdminPushCampaignInternalController.class,
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
class AdminPushCampaignInternalControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminPushCampaignService adminPushCampaignService;

    private AdminPushCampaignResponse campaign(Long id, AdminPushTargetType type, List<String> targets,
                                               AdminPushCampaignStatus status) {
        return new AdminPushCampaignResponse(
            id, "이벤트 안내", "오늘만 다이아 2배!", "/shop", type, targets,
            targets == null ? 120 : targets.size(),
            status == AdminPushCampaignStatus.COMPLETED ? 110 : 0,
            status == AdminPushCampaignStatus.COMPLETED ? 9 : 0,
            status == AdminPushCampaignStatus.COMPLETED ? 1 : 0,
            status, 1L,
            status == AdminPushCampaignStatus.PENDING ? null : LocalDateTime.of(2026, 9, 21, 11, 0, 0),
            status == AdminPushCampaignStatus.COMPLETED ? LocalDateTime.of(2026, 9, 21, 11, 0, 5) : null,
            null,
            LocalDateTime.of(2026, 9, 21, 10, 59, 59));
    }

    private static org.springframework.restdocs.payload.FieldDescriptor[] campaignFields(String prefix) {
        return new org.springframework.restdocs.payload.FieldDescriptor[] {
            fieldWithPath(prefix + "id").type(JsonFieldType.NUMBER).description("캠페인(이력) ID"),
            fieldWithPath(prefix + "title").type(JsonFieldType.STRING).description("푸시 제목"),
            fieldWithPath(prefix + "body").type(JsonFieldType.STRING).description("푸시 본문"),
            fieldWithPath(prefix + "action_url").type(JsonFieldType.STRING).optional()
                .description("탭 시 이동 경로 (없으면 null)"),
            fieldWithPath(prefix + "target_type").type(JsonFieldType.STRING).description("대상 유형 (ALL|USERS)"),
            fieldWithPath(prefix + "target_user_ids").type(JsonFieldType.ARRAY).optional()
                .description("USERS 대상 유저 ID 목록 — 목록 조회·ALL 은 null"),
            fieldWithPath(prefix + "target_count").type(JsonFieldType.NUMBER).description("발송 대상 유저 수"),
            fieldWithPath(prefix + "sent_count").type(JsonFieldType.NUMBER).description("알림 생성 성공 수"),
            fieldWithPath(prefix + "skipped_count").type(JsonFieldType.NUMBER)
                .description("스킵 수 (유저가 시스템 알림을 껐음)"),
            fieldWithPath(prefix + "failed_count").type(JsonFieldType.NUMBER).description("생성 실패 수"),
            fieldWithPath(prefix + "status").type(JsonFieldType.STRING)
                .description("상태 (PENDING|SENDING|COMPLETED|FAILED)"),
            fieldWithPath(prefix + "requested_by").type(JsonFieldType.NUMBER).description("요청 관리자 ID"),
            fieldWithPath(prefix + "started_at").type(JsonFieldType.STRING).optional().description("발송 시작 시각"),
            fieldWithPath(prefix + "completed_at").type(JsonFieldType.STRING).optional().description("발송 종료 시각"),
            fieldWithPath(prefix + "error_message").type(JsonFieldType.STRING).optional().description("실패 사유"),
            fieldWithPath(prefix + "created_at").type(JsonFieldType.STRING).optional().description("요청 시각"),
        };
    }

    @Test
    @DisplayName("POST /api/internal/push-campaigns : 푸시 발송 요청 (LUT-508)")
    void createCampaign() throws Exception {
        AdminPushCampaignRequest request = AdminPushCampaignRequest.builder()
            .title("이벤트 안내").body("오늘만 다이아 2배!").actionUrl("/shop")
            .targetType(AdminPushTargetType.USERS).userIds(List.of("user-1", "user-2")).build();
        when(adminPushCampaignService.create(any(AdminPushCampaignRequest.class), eq(1L)))
            .thenReturn(campaign(7L, AdminPushTargetType.USERS, List.of("user-1", "user-2"),
                AdminPushCampaignStatus.PENDING));

        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/internal/push-campaigns")
                .header("X-Admin-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("Internal-푸시알림-01. 발송 요청",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Internal-PushCampaign")
                        .description("LUT-508: 관리자 푸시 발송 — 이력 행 생성 + 대상 수 응답 후 비동기 발송 "
                            + "(전체=ACTIVE 유저 전원, 일부=요청 ID 중 활성 유저). X-Admin-Id 헤더 필수")
                        .requestFields(
                            fieldWithPath("title").type(JsonFieldType.STRING).description("푸시 제목 (100자)"),
                            fieldWithPath("body").type(JsonFieldType.STRING).description("푸시 본문 (500자)"),
                            fieldWithPath("action_url").type(JsonFieldType.STRING).optional()
                                .description("탭 시 이동할 앱 내 경로"),
                            fieldWithPath("target_type").type(JsonFieldType.STRING).description("ALL|USERS"),
                            fieldWithPath("user_ids").type(JsonFieldType.ARRAY).optional()
                                .description("USERS 일 때 대상 유저 ID 목록")
                        )
                        .responseFields(
                            concat(
                                new org.springframework.restdocs.payload.FieldDescriptor[] {
                                    fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                    fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                    fieldWithPath("value").type(JsonFieldType.OBJECT).description("생성된 캠페인"),
                                },
                                campaignFields("value.")))
                        .build()
                )
            )
        );

        resultActions
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.id").value(7))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.status").value("PENDING"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.target_count").value(2));
    }

    @Test
    @DisplayName("POST /api/internal/push-campaigns : 제목 누락은 400")
    void createCampaign_validation() throws Exception {
        AdminPushCampaignRequest request = AdminPushCampaignRequest.builder()
            .body("본문만").targetType(AdminPushTargetType.ALL).build();

        mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/internal/push-campaigns")
                .header("X-Admin-Id", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/internal/push-campaigns : 발송 이력 목록 (최신순)")
    void getCampaigns() throws Exception {
        when(adminPushCampaignService.getCampaigns(anyInt(), anyInt()))
            .thenReturn(new AdminPushCampaignPageResponse(
                List.of(campaign(8L, AdminPushTargetType.ALL, null, AdminPushCampaignStatus.COMPLETED),
                    campaign(7L, AdminPushTargetType.USERS, null, AdminPushCampaignStatus.COMPLETED)),
                1, 2, 0, 20, true, true));

        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/internal/push-campaigns")
                .param("page", "0").param("size", "20")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("Internal-푸시알림-02. 발송 이력 목록",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Internal-PushCampaign")
                        .description("LUT-508: 관리자 푸시 발송 이력 (최신순 페이징, 대상 유저 목록은 상세에서만)")
                        .queryParameters(
                            parameterWithName("page").type(SimpleType.INTEGER).optional().description("페이지 (0부터)"),
                            parameterWithName("size").type(SimpleType.INTEGER).optional().description("페이지 크기")
                        )
                        .responseFields(
                            concat(
                                new org.springframework.restdocs.payload.FieldDescriptor[] {
                                    fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                    fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                    fieldWithPath("value").type(JsonFieldType.OBJECT).description("페이지"),
                                    fieldWithPath("value.content").type(JsonFieldType.ARRAY).description("이력 목록"),
                                    fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지"),
                                    fieldWithPath("value.total_elements").type(JsonFieldType.NUMBER).description("전체 건수"),
                                    fieldWithPath("value.number").type(JsonFieldType.NUMBER).description("현재 페이지"),
                                    fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                                    fieldWithPath("value.first").type(JsonFieldType.BOOLEAN).description("첫 페이지"),
                                    fieldWithPath("value.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지"),
                                },
                                campaignFields("value.content[].")))
                        .build()
                )
            )
        );

        resultActions
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.content[0].id").value(8))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.content[0].sent_count").value(110));
    }

    @Test
    @DisplayName("GET /api/internal/push-campaigns/{id} : 발송 이력 상세 (대상 유저 포함)")
    void getCampaign() throws Exception {
        when(adminPushCampaignService.getCampaign(7L))
            .thenReturn(campaign(7L, AdminPushTargetType.USERS, List.of("user-1", "user-2"),
                AdminPushCampaignStatus.COMPLETED));

        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/internal/push-campaigns/{campaignId}", 7L)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("Internal-푸시알림-03. 발송 이력 상세",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Internal-PushCampaign")
                        .description("LUT-508: 관리자 푸시 발송 이력 상세 — USERS 캠페인은 대상 유저 ID 목록 포함")
                        .pathParameters(parameterWithName("campaignId").description("캠페인 ID"))
                        .responseFields(
                            concat(
                                new org.springframework.restdocs.payload.FieldDescriptor[] {
                                    fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                    fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                    fieldWithPath("value").type(JsonFieldType.OBJECT).description("캠페인"),
                                },
                                campaignFields("value.")))
                        .build()
                )
            )
        );

        resultActions
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.target_user_ids[1]").value("user-2"));
    }

    private static org.springframework.restdocs.payload.FieldDescriptor[] concat(
        org.springframework.restdocs.payload.FieldDescriptor[] a,
        org.springframework.restdocs.payload.FieldDescriptor[] b) {
        org.springframework.restdocs.payload.FieldDescriptor[] out =
            new org.springframework.restdocs.payload.FieldDescriptor[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
