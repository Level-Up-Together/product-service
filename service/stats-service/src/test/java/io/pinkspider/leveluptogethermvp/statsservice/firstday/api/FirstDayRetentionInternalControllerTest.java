package io.pinkspider.leveluptogethermvp.statsservice.firstday.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.statsservice.api.exception.StatsInvalidParamException;
import io.pinkspider.leveluptogethermvp.statsservice.api.exception.StatsRequestExceptionHandler;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.application.FirstDayRetentionQueryService;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.application.FirstDayRetentionService;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto.FirstDayRetentionResponse;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto.FirstDayRetentionResponse.GroupStat;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto.FirstDayRetentionResponse.Verdict;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto.FirstDayRetentionRunResponse;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.FirstDayGroup;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.RetentionVerdictCode;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.VerdictComparison;
import java.time.LocalDate;
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
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = FirstDayRetentionInternalController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import({ControllerTestConfig.class, StatsRequestExceptionHandler.class})
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("FirstDayRetentionInternalController 테스트 (LUT-779)")
class FirstDayRetentionInternalControllerTest {

    private static final String TAG = "KPI - Admin Internal";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private FirstDayRetentionQueryService queryService;
    @MockitoBean private FirstDayRetentionService retentionService;

    @Test
    @DisplayName("GET /api/internal/kpi/first-day-retention : 그룹별 리텐션과 판정")
    void summary() throws Exception {
        when(queryService.summary(eq("4w"), eq("IOS"), any()))
                .thenReturn(
                        new FirstDayRetentionResponse(
                                "4w",
                                "IOS",
                                LocalDate.of(2026, 10, 13),
                                LocalDate.of(2026, 11, 9),
                                LocalDate.of(2026, 10, 1),
                                820,
                                41.2,
                                List.of(
                                        new GroupStat(
                                                FirstDayGroup.NOT_COMPLETED,
                                                482,
                                                22.0,
                                                482,
                                                8.1,
                                                400,
                                                null,
                                                0),
                                        new GroupStat(
                                                FirstDayGroup.SOLO,
                                                251,
                                                48.0,
                                                251,
                                                24.3,
                                                210,
                                                null,
                                                0),
                                        new GroupStat(
                                                FirstDayGroup.SOCIAL,
                                                87,
                                                61.0,
                                                87,
                                                39.1,
                                                70,
                                                null,
                                                0)),
                                List.of(
                                        new Verdict(
                                                VerdictComparison.SOLO_VS_NOT_COMPLETED,
                                                RetentionVerdictCode.SOLO_RETAINS,
                                                16.2,
                                                true),
                                        new Verdict(
                                                VerdictComparison.SOCIAL_VS_SOLO,
                                                RetentionVerdictCode.SOCIAL_BOOSTS,
                                                14.8,
                                                true))));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                        "/api/internal/kpi/first-day-retention")
                                .param("range", "4w")
                                .param("platform", "IOS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.signups").value(820))
                .andExpect(jsonPath("$.value.groups[2].group").value("SOCIAL"))
                .andExpect(jsonPath("$.value.verdicts[0].code").value("SOLO_RETAINS"))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "KPI-내부API-첫날리텐션-01. 조회",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description(
                                                        "첫날(가입 후 24시간) 행동별 D1·D7·D30 리텐션과 D7 판정 —"
                                                                + " 어드민 리텐션 화면(LUT-780). 리텐션은 지표가"
                                                                + " 확정된 유저만, 확정 유저가 없으면 null."
                                                                + " 허용값 밖 400 100101")
                                                .queryParameters(
                                                        parameterWithName("range")
                                                                .type(SimpleType.STRING)
                                                                .description(
                                                                        "가입 기간 4w·8w·12w (기본 4w) —"
                                                                                + " 끝은 오늘 − 2일")
                                                                .optional(),
                                                        parameterWithName("platform")
                                                                .type(SimpleType.STRING)
                                                                .description(
                                                                        "ALL·IOS·ANDROID·WEB (기본"
                                                                            + " ALL). 가입 기기를 모르는"
                                                                            + " 유저는 ALL 에만")
                                                                .optional())
                                                .responseFields(
                                                        fieldWithPath("code")
                                                                .type(JsonFieldType.STRING)
                                                                .description("응답 코드"),
                                                        fieldWithPath("message")
                                                                .type(JsonFieldType.STRING)
                                                                .description("응답 메시지"),
                                                        fieldWithPath("value.range")
                                                                .type(JsonFieldType.STRING)
                                                                .description("기간"),
                                                        fieldWithPath("value.platform")
                                                                .type(JsonFieldType.STRING)
                                                                .description("플랫폼"),
                                                        fieldWithPath("value.range_start")
                                                                .type(JsonFieldType.STRING)
                                                                .description("가입일 시작(KST)"),
                                                        fieldWithPath("value.range_end")
                                                                .type(JsonFieldType.STRING)
                                                                .description(
                                                                        "가입일 끝(KST) — 첫날이 모두 끝난 마지막"
                                                                                + " 날"),
                                                        fieldWithPath("value.data_start_date")
                                                                .type(JsonFieldType.STRING)
                                                                .description(
                                                                        "접속 기록 시작일 — 그 전 가입자는 대상"
                                                                                + " 아님")
                                                                .optional(),
                                                        fieldWithPath("value.signups")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description(
                                                                        "가입자(테스트·운영자 제외, 탈퇴자 포함)"),
                                                        fieldWithPath(
                                                                        "value.first_day_completion_rate")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description(
                                                                        "첫날 미션 완료율(%), 가입자 0 이면"
                                                                                + " null")
                                                                .optional(),
                                                        fieldWithPath("value.groups[].group")
                                                                .type(JsonFieldType.STRING)
                                                                .description(
                                                                        "NOT_COMPLETED·SOLO·SOCIAL"),
                                                        fieldWithPath("value.groups[].users")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description("그룹 인원"),
                                                        fieldWithPath("value.groups[].d1")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description(
                                                                        "D1 리텐션(%), 확정 유저 없으면 null")
                                                                .optional(),
                                                        fieldWithPath("value.groups[].d1_users")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description("D1 판정 가능 인원"),
                                                        fieldWithPath("value.groups[].d7")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description("D7 리텐션(%)")
                                                                .optional(),
                                                        fieldWithPath("value.groups[].d7_users")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description("D7 판정 가능 인원"),
                                                        fieldWithPath("value.groups[].d30")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description("D30 리텐션(%)")
                                                                .optional(),
                                                        fieldWithPath("value.groups[].d30_users")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description("D30 판정 가능 인원"),
                                                        fieldWithPath("value.verdicts[].comparison")
                                                                .type(JsonFieldType.STRING)
                                                                .description(
                                                                        "SOLO_VS_NOT_COMPLETED ·"
                                                                            + " SOCIAL_VS_SOLO"),
                                                        fieldWithPath("value.verdicts[].code")
                                                                .type(JsonFieldType.STRING)
                                                                .description(
                                                                        "SOLO_RETAINS·SOLO_WEAK·SOCIAL_BOOSTS·"
                                                                            + "SOCIAL_NO_DIFF·SOCIAL_HURTS·"
                                                                            + "NOT_ENOUGH_DATA"),
                                                        fieldWithPath("value.verdicts[].diff_pp")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description(
                                                                        "D7 차이 %p(뒤 그룹 − 앞 그룹), 0명"
                                                                                + " 그룹이 있으면 null")
                                                                .optional(),
                                                        fieldWithPath(
                                                                        "value.verdicts[].significant")
                                                                .type(JsonFieldType.BOOLEAN)
                                                                .description("두 비율 z-검정 95% 유의"))
                                                .build())));
    }

    @Test
    @DisplayName("POST /api/internal/kpi/first-day-retention/rebuild : 처음부터 다시 만들기")
    void rebuild() throws Exception {
        when(retentionService.rebuildAll(any(), any()))
                .thenReturn(new FirstDayRetentionRunResponse(LocalDate.of(2026, 10, 1), 120, 118));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                "/api/internal/kpi/first-day-retention/rebuild"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.classified").value(120))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "KPI-내부API-첫날리텐션-02. 다시 만들기",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description(
                                                        "표를 비우고 접속 기록 시작일부터 다시 분류·채움 (정의 변경 때)")
                                                .responseFields(
                                                        fieldWithPath("code")
                                                                .type(JsonFieldType.STRING)
                                                                .description("응답 코드"),
                                                        fieldWithPath("message")
                                                                .type(JsonFieldType.STRING)
                                                                .description("응답 메시지"),
                                                        fieldWithPath("value.data_start_date")
                                                                .type(JsonFieldType.STRING)
                                                                .description("접속 기록 시작일")
                                                                .optional(),
                                                        fieldWithPath("value.classified")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description("그룹을 정한 유저 수"),
                                                        fieldWithPath("value.retention_updated")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description("D1·D7·D30 을 채운 유저 수"))
                                                .build())));
    }

    @Test
    @DisplayName("허용값 밖 파라미터는 400 100101")
    void invalidParam() throws Exception {
        when(queryService.summary(eq("5w"), eq("ALL"), any()))
                .thenThrow(new StatsInvalidParamException("range", "4w, 8w, 12w"));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                        "/api/internal/kpi/first-day-retention")
                                .param("range", "5w"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code").value(StatsRequestExceptionHandler.CODE_INVALID_PARAM));
    }
}
