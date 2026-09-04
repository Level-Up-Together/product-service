package io.pinkspider.leveluptogethermvp.gamificationservice.statistics.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.gamificationservice.statistics.application.RecordStatisticsService;
import io.pinkspider.leveluptogethermvp.gamificationservice.statistics.domain.dto.RecordSummaryResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.statistics.domain.dto.RecordSummaryResponse.GradeReached;
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

@WebMvcTest(controllers = RecordStatisticsController.class,
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
class RecordStatisticsControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private RecordStatisticsService recordStatisticsService;

    private static final String MOCK_USER_ID = "test-user-123";

    @Test
    @DisplayName("GET /api/v1/statistics/summary : 전체 기간 기록 요약 (LUT-454)")
    void getRecordSummaryTest() throws Exception {
        when(recordStatisticsService.getRecordSummary(anyString()))
            .thenReturn(new RecordSummaryResponse(
                120, 30, 5, 21, 15, TitleRarity.RARE,
                List.of(
                    new GradeReached(TitleRarity.UNCOMMON, LocalDateTime.of(2026, 1, 10, 0, 0)),
                    new GradeReached(TitleRarity.RARE, LocalDateTime.of(2026, 5, 2, 0, 0)))));

        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/statistics/summary")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("기록통계-01. 전체 기간 기록 요약",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("RecordStatistics")
                        .description("내 전체 기간 기록 요약 (JWT 토큰 인증 필요) — 누적 달성·스트릭·"
                            + "현재 등급·등급 도달 이력. 무료/구독 게이팅은 프론트 담당")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("기록 요약"),
                            fieldWithPath("value.total_mission_completions").type(JsonFieldType.NUMBER)
                                .description("누적 미션 달성 수"),
                            fieldWithPath("value.total_guild_mission_completions").type(JsonFieldType.NUMBER)
                                .description("누적 길드 미션 달성 수"),
                            fieldWithPath("value.current_streak").type(JsonFieldType.NUMBER)
                                .description("현재 연속 수행일"),
                            fieldWithPath("value.max_streak").type(JsonFieldType.NUMBER)
                                .description("최장 연속 수행일"),
                            fieldWithPath("value.current_level").type(JsonFieldType.NUMBER)
                                .description("현재 레벨"),
                            fieldWithPath("value.current_grade").type(JsonFieldType.STRING)
                                .description("현재 등급 (COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC)"),
                            fieldWithPath("value.grade_history").type(JsonFieldType.ARRAY)
                                .description("등급 도달 이력 (도달한 등급만, 도달 순)"),
                            fieldWithPath("value.grade_history[].grade").type(JsonFieldType.STRING)
                                .description("도달 등급"),
                            fieldWithPath("value.grade_history[].reached_at").type(JsonFieldType.STRING)
                                .description("최초 도달 시각 (ISO 8601, UTC)")
                        )
                        .build()
                )
            )
        );

        resultActions
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.total_mission_completions").value(120))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.max_streak").value(21))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.current_grade").value("RARE"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.grade_history.length()").value(2));
    }
}
