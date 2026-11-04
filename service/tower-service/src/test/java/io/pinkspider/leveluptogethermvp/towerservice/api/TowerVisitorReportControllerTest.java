package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerVisitorException;
import io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorReportService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorReportResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TowerVisitorReportController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("TowerVisitorReportController 테스트 (LUT-705)")
class TowerVisitorReportControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TowerVisitorReportService reportService;

    @Test
    @DisplayName(
            "POST /api/v1/tower/instances/{instanceId}/visitor-reports : 같은 방 방문객 신고(로그인) · 대상 아님"
                    + " 404 150504")
    void report() throws Exception {
        when(reportService.report(eq("user-1"), eq(9L), any()))
                .thenReturn(new TowerVisitorReportResponse(77L, false));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/instances/{instanceId}/visitor-reports", 9L)
                                .with(user("user-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"occupant_id\":12,\"reason\":\"광고\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.report_id").value(77))
                .andDo(
                        document(
                                "tower-visitor-report",
                                pathParameters(parameterWithName("instanceId").description("방 ID")),
                                requestFields(
                                        fieldWithPath("occupant_id")
                                                .description(
                                                        "신고할 방문객의 점유자 ID (아바타·채팅의 occupant_id)"),
                                        fieldWithPath("reason")
                                                .description("사유 (선택, 500자)")
                                                .optional()),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.report_id").description("신고 ID"),
                                        fieldWithPath("value.duplicate")
                                                .description(
                                                        "24시간 안에 같은 방문객을 이미 신고해 새로 남기지 않았는가"))));

        when(reportService.report(eq("user-1"), eq(10L), any()))
                .thenThrow(TowerVisitorException.reportTargetNotFound());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/instances/{instanceId}/visitor-reports", 10L)
                                .with(user("user-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"occupant_id\":12}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("150504"));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/instances/{instanceId}/visitor-reports", 10L)
                                .with(user("user-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
