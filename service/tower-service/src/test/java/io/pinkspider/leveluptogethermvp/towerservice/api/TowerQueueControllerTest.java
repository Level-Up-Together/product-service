package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.application.queue.TowerQueueService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerQueueStatus;
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
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TowerQueueController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("TowerQueueController 테스트 (LUT-701)")
class TowerQueueControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TowerQueueService queueService;

    @Test
    @DisplayName(
            "GET /api/v1/tower/queue/{ticket} : 대기 상태 (WAITING → 자리가 나면 ADMITTED) · 없거나 끝난 표 404"
                    + " 150206")
    void queueStatus() throws Exception {
        when(queueService.status(eq("t-1"), any()))
                .thenReturn(new TowerQueueStatus("t-1", "ADMITTED", null, 0, 2, 120L, 28));

        mockMvc.perform(RestDocumentationRequestBuilders.get("/api/v1/tower/queue/{ticket}", "t-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.status").value("ADMITTED"))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-queue-status",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower")
                                        .description(
                                                "입장 대기 상태 (LUT-701) — WAITING → ADMITTED. 20초 동안"
                                                    + " 조회가 없으면 줄에서 뺀다. 없거나 끝난 표 404 150206. 비로그인"
                                                    + " 허용"),
                                pathParameters(
                                        parameterWithName("ticket")
                                                .description("입장 응답 queue.ticket")),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.ticket").description("대기표"),
                                        fieldWithPath("value.status")
                                                .description(
                                                        "WAITING(줄에서 대기) / ADMITTED(입장 가능 — 창 안에"
                                                                + " queue_ticket 으로 입장 요청)"),
                                        fieldWithPath("value.position")
                                                .description("내 순번 (WAITING 만)")
                                                .optional(),
                                        fieldWithPath("value.estimated_wait_seconds")
                                                .description("예상 대기 시간(초, 순번 × 추정 간격)"),
                                        fieldWithPath("value.floor_number").description("층 번호"),
                                        fieldWithPath("value.population")
                                                .description("층 전체 인원 (NPC 제외)"),
                                        fieldWithPath("value.admit_expires_in_seconds")
                                                .description("ADMITTED 의 남은 입장 시간(초, 30초 창)")
                                                .optional())));

        when(queueService.status(eq("gone"), any()))
                .thenThrow(TowerEntryException.queueTicketNotFound());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                "/api/v1/tower/queue/{ticket}", "gone"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("150206"));
    }

    @Test
    @DisplayName("DELETE /api/v1/tower/queue/{ticket} : 대기 그만하기 — 즉시 줄에서 뺀다")
    void leave() throws Exception {
        mockMvc.perform(
                        RestDocumentationRequestBuilders.delete(
                                "/api/v1/tower/queue/{ticket}", "t-1"))
                .andExpect(status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "tower-queue-leave",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower")
                                        .description("대기 그만하기 (LUT-701) — 즉시 줄에서 뺀다. 비로그인 허용"),
                                pathParameters(parameterWithName("ticket").description("대기표"))));
        verify(queueService).leave(eq("t-1"), any());
    }
}
