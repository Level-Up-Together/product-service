package io.pinkspider.leveluptogethermvp.statsservice.activity.api;

import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.statsservice.activity.application.UserActivityFlushService;
import java.time.LocalDate;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = UserActivityInternalController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("UserActivityInternalController 테스트 (LUT-598)")
class UserActivityInternalControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserActivityFlushService flushService;

    @Test
    @DisplayName("POST /api/internal/stats/activity/flush : 날짜의 활동 집합을 적재하고 결과를 돌려준다")
    void flush() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 22);
        when(flushService.flush(date)).thenReturn(3);
        when(flushService.countActiveUsers(date)).thenReturn(12L);

        mockMvc.perform(post("/api/internal/stats/activity/flush").queryParam("date", "2026-10-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.date").value("2026-10-22"))
                .andExpect(jsonPath("$.value.flushed_rows").value(3))
                .andExpect(jsonPath("$.value.active_users").value(12))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-stats-activity-flush",
                                ResourceSnippetParameters.builder()
                                        .tag("Stats Activity - Admin Internal")
                                        .description(
                                                "활동 집합 수동 적재 (LUT-598) — Redis active:{날짜} →"
                                                    + " user_daily_activity upsert(멱등). 스케줄러(00:20"
                                                    + " KST)와 같은 일. 형식 오류 400 100101 · 누락 100102"),
                                queryParameters(
                                        parameterWithName("date").description("적재할 날짜 (ISO, KST)")),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.date").description("적재한 날짜"),
                                        fieldWithPath("value.flushed_rows")
                                                .description("이번에 넣거나 갱신한 행 수"),
                                        fieldWithPath("value.active_users")
                                                .description("그날 활동 유저 수"))));
    }

    @Test
    @DisplayName("date 가 ISO 날짜가 아니면 400")
    void invalidDate() throws Exception {
        mockMvc.perform(post("/api/internal/stats/activity/flush").param("date", "20261022"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("100101")); // LUT-616
        mockMvc.perform(post("/api/internal/stats/activity/flush"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("100102"));
    }
}
