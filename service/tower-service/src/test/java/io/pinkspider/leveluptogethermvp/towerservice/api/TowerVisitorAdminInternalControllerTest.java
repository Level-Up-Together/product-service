package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerVisitorException;
import io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorReportService;
import io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorSanctionService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerAdminPage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorReportAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorSanctionResponse;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TowerVisitorAdminInternalController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("TowerVisitorAdminInternalController 테스트 (LUT-705)")
class TowerVisitorAdminInternalControllerTest {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 4, 12, 0);

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TowerVisitorSanctionService sanctionService;

    @MockitoBean private TowerVisitorReportService reportService;

    private static TowerVisitorSanctionResponse sanction(boolean active) {
        return new TowerVisitorSanctionResponse(
                3L,
                "DEVICE",
                "dev-1",
                "CHAT",
                "도배",
                AT.plusDays(1),
                active ? null : AT,
                active,
                "admin-1",
                AT);
    }

    private static FieldDescriptor[] sanctionFields(String prefix) {
        return new FieldDescriptor[] {
            fieldWithPath(prefix + "id").description("제재 ID"),
            fieldWithPath(prefix + "target_type").description("IP / DEVICE"),
            fieldWithPath(prefix + "target_value").description("IP 또는 기기 ID"),
            fieldWithPath(prefix + "sanction_type")
                    .description("CHAT(채팅·리액션 금지) / ENTRY(입장 금지, 채팅도 막힘)"),
            fieldWithPath(prefix + "reason").description("사유").optional(),
            fieldWithPath(prefix + "expires_at").description("끝나는 시각(UTC), 없으면 해제할 때까지").optional(),
            fieldWithPath(prefix + "lifted_at").description("해제한 시각(UTC)").optional(),
            fieldWithPath(prefix + "active").description("지금 효력이 있는가"),
            fieldWithPath(prefix + "created_by").description("등록한 어드민").optional(),
            fieldWithPath(prefix + "created_at").description("등록 시각(UTC)").optional()
        };
    }

    private static FieldDescriptor[] pageFields() {
        return new FieldDescriptor[] {
            fieldWithPath("value.total_elements").description("전체 건수"),
            fieldWithPath("value.total_pages").description("전체 페이지 수"),
            fieldWithPath("value.page").description("페이지(0부터)"),
            fieldWithPath("value.size").description("페이지 크기(최대 100)")
        };
    }

    @Test
    @DisplayName("GET /api/internal/tower/visitor-reports : 방문객 신고(원문·IP·기기 ID, 최신 먼저)")
    void reports() throws Exception {
        when(reportService.list(0, 20))
                .thenReturn(
                        new TowerAdminPage<>(
                                List.of(
                                        new TowerVisitorReportAdminResponse(
                                                5L,
                                                "user-1",
                                                9L,
                                                1,
                                                12L,
                                                4821,
                                                "a1b2c3d4e5f60718",
                                                "1.2.3.4",
                                                "dev-1",
                                                "Mozilla/5.0",
                                                "광고",
                                                "[{\"sent_at\":\"2026-11-04T12:00\",\"instance_id\":9,\"content\":\"광고"
                                                    + " www.x.com\",\"result\":\"REJECTED\"}]",
                                                AT)),
                                1,
                                1,
                                0,
                                20));
        mockMvc.perform(RestDocumentationRequestBuilders.get("/api/internal/tower/visitor-reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.content[0].visitor_no").value(4821))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-visitor-reports-internal",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description("방문객 신고 목록 (LUT-705) — 최신 먼저, 원문·IP·기기 ID 포함"),
                                queryParameters(
                                        parameterWithName("page")
                                                .description("페이지(0부터, 기본 0)")
                                                .optional(),
                                        parameterWithName("size")
                                                .description("페이지 크기(기본 20, 최대 100)")
                                                .optional()),
                                responseFields(
                                                fieldWithPath("code").description("응답 코드"),
                                                fieldWithPath("message").description("응답 메시지"),
                                                fieldWithPath("value.content[].id")
                                                        .description("신고 ID"),
                                                fieldWithPath("value.content[].reporter_user_id")
                                                        .description("신고자"),
                                                fieldWithPath("value.content[].instance_id")
                                                        .description("방 ID"),
                                                fieldWithPath("value.content[].floor_number")
                                                        .description("층"),
                                                fieldWithPath("value.content[].target_occupant_id")
                                                        .description("신고 당시 방문객 점유자 ID"),
                                                fieldWithPath("value.content[].visitor_no")
                                                        .description("방문객 이름 번호")
                                                        .optional(),
                                                fieldWithPath("value.content[].visitor_tag")
                                                        .description("방문객 세션 공개 식별자(같은 세션 신고 묶기용)")
                                                        .optional(),
                                                fieldWithPath("value.content[].client_ip")
                                                        .description("마지막 입장 IP (제재 대상값)")
                                                        .optional(),
                                                fieldWithPath("value.content[].device_id")
                                                        .description("기기 ID (제재 대상값)")
                                                        .optional(),
                                                fieldWithPath("value.content[].user_agent")
                                                        .description("User-Agent")
                                                        .optional(),
                                                fieldWithPath("value.content[].reason")
                                                        .description("사유")
                                                        .optional(),
                                                fieldWithPath("value.content[].recent_messages")
                                                        .description(
                                                                "신고 시점 최근 원문 채팅(JSON 배열 문자열, 최신 먼저"
                                                                    + " — sent_at·instance_id·content·result)")
                                                        .optional(),
                                                fieldWithPath("value.content[].created_at")
                                                        .description("신고 시각(UTC)")
                                                        .optional())
                                        .and(pageFields())));
    }

    @Test
    @DisplayName(
            "GET·POST·DELETE /api/internal/tower/visitor-sanctions : 기기·IP 제재 목록·등록·해제 · 없는 제재 404"
                    + " 150506")
    void sanctions() throws Exception {
        when(sanctionService.list(0, 20))
                .thenReturn(new TowerAdminPage<>(List.of(sanction(true)), 1, 1, 0, 20));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                "/api/internal/tower/visitor-sanctions"))
                .andExpect(status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-visitor-sanctions-internal",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description("방문객 제재 목록 (LUT-705)"),
                                queryParameters(
                                        parameterWithName("page")
                                                .description("페이지(0부터, 기본 0)")
                                                .optional(),
                                        parameterWithName("size")
                                                .description("페이지 크기(기본 20, 최대 100)")
                                                .optional()),
                                responseFields(
                                                fieldWithPath("code").description("응답 코드"),
                                                fieldWithPath("message").description("응답 메시지"))
                                        .and(sanctionFields("value.content[]"))
                                        .and(pageFields())));

        when(sanctionService.create(any())).thenReturn(sanction(true));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/internal/tower/visitor-sanctions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"target_type\":\"DEVICE\",\"target_value\":\"dev-1\",\"sanction_type\":\"CHAT\","
                                            + "\"reason\":\"도배\",\"duration_minutes\":1440,\"created_by\":\"admin-1\"}"))
                .andExpect(status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-visitor-sanction-create-internal",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description(
                                                "방문객 제재 등록 (LUT-705) — IP 또는 기기 ID × CHAT/ENTRY, 기간"
                                                        + " 또는 해제할 때까지"),
                                requestFields(
                                        fieldWithPath("target_type").description("IP / DEVICE"),
                                        fieldWithPath("target_value")
                                                .description(
                                                        "IP 또는 기기 ID (신고의 client_ip / device_id)"),
                                        fieldWithPath("sanction_type").description("CHAT / ENTRY"),
                                        fieldWithPath("reason").description("사유 (선택)").optional(),
                                        fieldWithPath("duration_minutes")
                                                .description("기간(분, 1~525600). 없으면 해제할 때까지")
                                                .optional(),
                                        fieldWithPath("created_by")
                                                .description("등록한 어드민 (선택)")
                                                .optional()),
                                responseFields(
                                                fieldWithPath("code").description("응답 코드"),
                                                fieldWithPath("message").description("응답 메시지"))
                                        .and(sanctionFields("value."))));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/internal/tower/visitor-sanctions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"target_type\":\"IP\",\"sanction_type\":\"CHAT\"}"))
                .andExpect(status().isBadRequest());

        when(sanctionService.lift(3L)).thenReturn(sanction(false));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.delete(
                                "/api/internal/tower/visitor-sanctions/{sanctionId}", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.active").value(false))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-visitor-sanction-lift-internal",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description("방문객 제재 해제 (LUT-705) — 없으면 404"),
                                pathParameters(
                                        parameterWithName("sanctionId").description("제재 ID"))));
        when(sanctionService.lift(eq(4L))).thenThrow(TowerVisitorException.sanctionNotFound());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.delete(
                                "/api/internal/tower/visitor-sanctions/{sanctionId}", 4L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("150506"));
    }
}
