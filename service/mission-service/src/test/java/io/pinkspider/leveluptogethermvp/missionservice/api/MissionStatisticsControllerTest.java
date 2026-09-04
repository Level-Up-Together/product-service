package io.pinkspider.leveluptogethermvp.missionservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionStatisticsService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyStatisticsResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyStatisticsResponse.CategoryCount;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyStatisticsResponse.DailyCount;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyStatisticsResponse.DayOfWeekStat;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyStatisticsResponse.HourCount;
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

@WebMvcTest(controllers = MissionStatisticsController.class,
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
class MissionStatisticsControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private MissionStatisticsService missionStatisticsService;

    private static final String MOCK_USER_ID = "test-user-123";

    @Test
    @DisplayName("GET /api/v1/missions/statistics/monthly : 월간 기록 리포트 (LUT-454)")
    void getMonthlyStatisticsTest() throws Exception {
        when(missionStatisticsService.getMonthlyStatistics(anyString(), any(), any()))
            .thenReturn(new MonthlyStatisticsResponse(
                "2026-09", 10, 3, 30.0, 2, 30, 2,
                List.of(new CategoryCount("운동", 2, 66.7), new CategoryCount("독서", 1, 33.3)),
                List.of(new DayOfWeekStat("TUESDAY", 2, 1, 5, 20.0)),
                List.of(new HourCount(19, 2), new HourCount(7, 1)),
                List.of(new DailyCount("2026-09-01", 2), new DailyCount("2026-09-02", 1))));

        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/missions/statistics/monthly")
                .param("year_month", "2026-09")
                .header("X-Timezone", "Asia/Seoul")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("기록통계-02. 월간 기록 리포트",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("RecordStatistics")
                        .description("월간 기록 리포트 (JWT 토큰 인증 필요) — 달성률/스트릭/카테고리·"
                            + "요일·시간대 분포. 무료 유저는 최근 30일 범위의 월만 조회 가능"
                            + "(과거 월은 050301 구독 필요). year_month 생략 시 당월, "
                            + "날짜 버킷팅은 X-Timezone 기준")
                        .queryParameters(
                            parameterWithName("year_month").optional()
                                .description("조회 월 (yyyy-MM) — 생략 시 당월"))
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("월간 리포트"),
                            fieldWithPath("value.year_month").type(JsonFieldType.STRING)
                                .description("조회 월 (yyyy-MM)"),
                            fieldWithPath("value.scheduled_count").type(JsonFieldType.NUMBER)
                                .description("예정 수행 수 (일반+고정 미션, 예정일 기준)"),
                            fieldWithPath("value.completed_count").type(JsonFieldType.NUMBER)
                                .description("완료 수행 수 (예정일 기준)"),
                            fieldWithPath("value.achievement_rate").type(JsonFieldType.NUMBER)
                                .description("달성률 % (완료/예정, 소수 1자리)"),
                            fieldWithPath("value.active_days").type(JsonFieldType.NUMBER)
                                .description("수행일 수 (1건 이상 완료한 날)"),
                            fieldWithPath("value.month_days").type(JsonFieldType.NUMBER)
                                .description("해당 월 일수"),
                            fieldWithPath("value.longest_streak").type(JsonFieldType.NUMBER)
                                .description("월 내 최장 연속 수행일"),
                            fieldWithPath("value.category_distribution").type(JsonFieldType.ARRAY)
                                .description("카테고리별 완료 분포 (완료 수 내림차순)"),
                            fieldWithPath("value.category_distribution[].category_name")
                                .type(JsonFieldType.STRING).description("카테고리명"),
                            fieldWithPath("value.category_distribution[].count")
                                .type(JsonFieldType.NUMBER).description("완료 수"),
                            fieldWithPath("value.category_distribution[].ratio")
                                .type(JsonFieldType.NUMBER).description("전체 완료 대비 비율 %"),
                            fieldWithPath("value.day_of_week_stats").type(JsonFieldType.ARRAY)
                                .description("요일별 달성 통계 (월~일)"),
                            fieldWithPath("value.day_of_week_stats[].day_of_week")
                                .type(JsonFieldType.STRING).description("요일 (MONDAY~SUNDAY)"),
                            fieldWithPath("value.day_of_week_stats[].completed_count")
                                .type(JsonFieldType.NUMBER).description("완료 수행 수"),
                            fieldWithPath("value.day_of_week_stats[].active_day_count")
                                .type(JsonFieldType.NUMBER).description("수행한 해당 요일 수"),
                            fieldWithPath("value.day_of_week_stats[].occurrence_count")
                                .type(JsonFieldType.NUMBER).description("해당 월의 요일 수"),
                            fieldWithPath("value.day_of_week_stats[].achievement_rate")
                                .type(JsonFieldType.NUMBER).description("요일 달성률 % (수행일/요일 수)"),
                            fieldWithPath("value.hour_distribution").type(JsonFieldType.ARRAY)
                                .description("시간대별 완료 분포 (0~23시 전체)"),
                            fieldWithPath("value.hour_distribution[].hour")
                                .type(JsonFieldType.NUMBER).description("시 (0~23, X-Timezone 기준)"),
                            fieldWithPath("value.hour_distribution[].count")
                                .type(JsonFieldType.NUMBER).description("완료 수행 수"),
                            fieldWithPath("value.daily_completions").type(JsonFieldType.ARRAY)
                                .description("일자별 완료 수 (완료가 있는 날만)"),
                            fieldWithPath("value.daily_completions[].date")
                                .type(JsonFieldType.STRING).description("일자 (yyyy-MM-dd)"),
                            fieldWithPath("value.daily_completions[].count")
                                .type(JsonFieldType.NUMBER).description("완료 수행 수")
                        )
                        .build()
                )
            )
        );

        resultActions
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.year_month").value("2026-09"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.achievement_rate").value(30.0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.category_distribution[0].category_name")
                .value("운동"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.longest_streak").value(2));
    }
}
