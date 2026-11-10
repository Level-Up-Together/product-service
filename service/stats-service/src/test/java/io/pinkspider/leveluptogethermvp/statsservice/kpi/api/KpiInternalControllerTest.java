package io.pinkspider.leveluptogethermvp.statsservice.kpi.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
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
import io.pinkspider.leveluptogethermvp.statsservice.kpi.application.KpiQueryService;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.application.KpiRebuildService;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.CohortRetentionResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.CohortRevenueResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.DailyKpiSnapshotResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.KpiRebuildResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.enums.RetentionSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = KpiInternalController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("KpiInternalController 테스트 (LUT-601)")
class KpiInternalControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private KpiRebuildService rebuildService;
    @MockitoBean private KpiQueryService queryService;

    @Test
    @DisplayName("POST /api/internal/kpi/rebuild : 범위 재계산 결과")
    void rebuild() throws Exception {
        when(rebuildService.rebuild(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 22)))
                .thenReturn(
                        new KpiRebuildResponse(
                                LocalDate.of(2026, 10, 1),
                                LocalDate.of(2026, 10, 22),
                                22,
                                100,
                                10));

        mockMvc.perform(
                        post("/api/internal/kpi/rebuild")
                                .queryParam("from", "2026-10-01")
                                .queryParam("to", "2026-10-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.snapshot_days").value(22))
                .andExpect(jsonPath("$.value.retention_rows").value(100))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-kpi-rebuild",
                                ResourceSnippetParameters.builder()
                                        .tag("KPI - Admin Internal")
                                        .description(
                                                "KPI 소급 재계산 (LUT-601) — 일별 스냅샷·코호트 리텐션·매출을 범위로 다시"
                                                    + " 만든다(멱등). 어제까지, 최대 400일. 형식 오류 400 100101 ·"
                                                    + " 누락 100102"),
                                queryParameters(
                                        parameterWithName("from").description("시작일 (ISO, KST 날짜)"),
                                        parameterWithName("to")
                                                .description("종료일 (ISO, 어제까지로 자른다)")),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.from").description("실제 처리한 시작일"),
                                        fieldWithPath("value.to").description("실제 처리한 종료일"),
                                        fieldWithPath("value.snapshot_days")
                                                .description("다시 만든 일별 스냅샷 일수"),
                                        fieldWithPath("value.retention_rows")
                                                .description("다시 만든 리텐션 행 수"),
                                        fieldWithPath("value.revenue_rows")
                                                .description("다시 만든 코호트 매출 행 수"))));
    }

    @Test
    @DisplayName("GET /api/internal/kpi/daily : 일별 스냅샷 목록")
    void daily() throws Exception {
        when(queryService.daily(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22)))
                .thenReturn(
                        List.of(
                                new DailyKpiSnapshotResponse(
                                        LocalDate.of(2026, 10, 22),
                                        1,
                                        0,
                                        140,
                                        9,
                                        24,
                                        45,
                                        5,
                                        3,
                                        1,
                                        9,
                                        12,
                                        5,
                                        1,
                                        2,
                                        0,
                                        1,
                                        0,
                                        2,
                                        new BigDecimal("9800.00"),
                                        "{\"KRW\":9800.00}")));

        mockMvc.perform(
                        get("/api/internal/kpi/daily")
                                .param("from", "2026-10-20")
                                .param("to", "2026-10-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value[0].snapshot_date").value("2026-10-22"))
                .andExpect(jsonPath("$.value[0].mau").value(45))
                .andExpect(jsonPath("$.value[0].revenue_krw").value(9800.00))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-kpi-daily",
                                ResourceSnippetParameters.builder()
                                        .tag("KPI - Admin Internal")
                                        .description(
                                                "일별 KPI 스냅샷 (LUT-601) — 날짜 순. 어드민 KPI"
                                                        + " 화면(LUT-602/603)이 그대로 읽는다"),
                                queryParameters(
                                        parameterWithName("from").description("시작일 (ISO, KST 날짜)"),
                                        parameterWithName("to").description("종료일 (ISO)")),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value[].snapshot_date")
                                                .description("날짜 (KST)"),
                                        fieldWithPath("value[].signups").description("가입"),
                                        fieldWithPath("value[].withdrawals").description("탈퇴"),
                                        fieldWithPath("value[].active_users")
                                                .description("누적 활성 유저"),
                                        fieldWithPath("value[].dau")
                                                .description("DAU (요청 기준, LUT-598)"),
                                        fieldWithPath("value[].wau").description("WAU"),
                                        fieldWithPath("value[].mau").description("MAU"),
                                        fieldWithPath("value[].dau_web").description("DAU 웹"),
                                        fieldWithPath("value[].dau_ios").description("DAU iOS"),
                                        fieldWithPath("value[].dau_android")
                                                .description("DAU Android"),
                                        fieldWithPath("value[].attendance_dau")
                                                .description("출석 기준 DAU"),
                                        fieldWithPath("value[].mission_completions")
                                                .description("미션 완료 수"),
                                        fieldWithPath("value[].mission_active_users")
                                                .description("미션 완료 유저 수"),
                                        fieldWithPath("value[].new_subscriptions")
                                                .description("새 구독"),
                                        fieldWithPath("value[].active_subscriptions")
                                                .description("활성 구독"),
                                        fieldWithPath("value[].churned_subscriptions")
                                                .description("이탈 구독"),
                                        fieldWithPath("value[].trial_starts")
                                                .description("무료 체험 시작"),
                                        fieldWithPath("value[].trial_conversions")
                                                .description("체험 → 유료 전환"),
                                        fieldWithPath("value[].paying_users").description("결제 유저"),
                                        fieldWithPath("value[].revenue_krw")
                                                .description("매출 (KRW 환산, 환불 음수, 테스트 결제 제외)"),
                                        fieldWithPath("value[].revenue_by_currency")
                                                .description("통화별 매출 원본 (JSON 문자열)"))));
    }

    @Test
    @DisplayName("GET /api/internal/kpi/retention : source 선택 필터")
    void retention() throws Exception {
        when(queryService.retention(
                        eq(LocalDate.of(2026, 10, 1)),
                        eq(LocalDate.of(2026, 10, 2)),
                        eq(RetentionSource.ATTENDANCE)))
                .thenReturn(
                        List.of(
                                new CohortRetentionResponse(
                                        LocalDate.of(2026, 10, 1),
                                        "D1",
                                        "ATTENDANCE",
                                        4,
                                        2,
                                        new BigDecimal("0.5000"))));
        when(queryService.retention(any(), any(), isNull())).thenReturn(List.of());

        mockMvc.perform(
                        get("/api/internal/kpi/retention")
                                .param("cohort_from", "2026-10-01")
                                .param("cohort_to", "2026-10-02")
                                .param("source", "ATTENDANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value[0].rate").value(0.5))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-kpi-retention",
                                ResourceSnippetParameters.builder()
                                        .tag("KPI - Admin Internal")
                                        .description(
                                                "코호트 리텐션 (LUT-601) — 창이 닫힌 지표만. source 없으면 전부"),
                                queryParameters(
                                        parameterWithName("cohort_from")
                                                .description("코호트 시작일 (ISO)"),
                                        parameterWithName("cohort_to").description("코호트 종료일 (ISO)"),
                                        parameterWithName("source")
                                                .description(
                                                        "REQUEST / ATTENDANCE (없으면 둘 다, 그 밖의 값 400"
                                                                + " 100101)")
                                                .optional()),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value[].cohort_date")
                                                .description("가입일 (KST)"),
                                        fieldWithPath("value[].metric")
                                                .description("D1·D3·D7·D14·D30·W1·W2_4"),
                                        fieldWithPath("value[].source")
                                                .description("REQUEST / ATTENDANCE"),
                                        fieldWithPath("value[].cohort_size").description("코호트 인원"),
                                        fieldWithPath("value[].retained").description("돌아온 인원"),
                                        fieldWithPath("value[].rate")
                                                .description(
                                                        "retained ÷ cohort_size (소수 4자리, 분모 0 이면"
                                                                + " null)")
                                                .type(JsonFieldType.NUMBER)
                                                .optional())));
        mockMvc.perform(
                        get("/api/internal/kpi/retention")
                                .param("cohort_from", "2026-10-01")
                                .param("cohort_to", "2026-10-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").isEmpty());
    }

    @Test
    @DisplayName("GET /api/internal/kpi/signups/by-channel : 월 × 채널 가입자")
    void signupsByChannel() throws Exception {
        when(queryService.signupsByChannel(YearMonth.of(2026, 9), YearMonth.of(2026, 10)))
                .thenReturn(
                        List.of(
                                new io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto
                                        .source.SignupsByChannel("2026-10", "insta", 12)));

        mockMvc.perform(
                        get("/api/internal/kpi/signups/by-channel")
                                .param("from_month", "2026-09")
                                .param("to_month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value[0].month").value("2026-10"))
                .andExpect(jsonPath("$.value[0].channel").value("insta"))
                .andExpect(jsonPath("$.value[0].signups").value(12))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-kpi-signups-by-channel",
                                ResourceSnippetParameters.builder()
                                        .tag("KPI - Admin Internal")
                                        .description(
                                                "월 × 가입 채널 가입자 (LUT-602) — 어드민 CAC 분모. user_db 직접"
                                                        + " 집계, 테스트 계정 제외"),
                                queryParameters(
                                        parameterWithName("from_month")
                                                .description("시작 월 (yyyy-MM, KST)"),
                                        parameterWithName("to_month").description("끝 월 (yyyy-MM)")),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value[].month").description("월 (yyyy-MM)"),
                                        fieldWithPath("value[].channel")
                                                .description("가입 채널 (null 이면 unknown)"),
                                        fieldWithPath("value[].signups").description("가입자 수"))));
    }

    @Test
    @DisplayName("GET /api/internal/kpi/revenue/cohorts : yyyy-MM 범위, 형식 오류는 400")
    void revenueCohorts() throws Exception {
        when(queryService.revenueCohorts(YearMonth.of(2026, 8), YearMonth.of(2026, 10)))
                .thenReturn(
                        List.of(
                                new CohortRevenueResponse(
                                        "2026-08",
                                        1,
                                        4,
                                        1,
                                        new BigDecimal("9800.00"),
                                        new BigDecimal("2450.00"))));

        mockMvc.perform(
                        get("/api/internal/kpi/revenue/cohorts")
                                .param("from_month", "2026-08")
                                .param("to_month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value[0].ltv_krw").value(2450.00))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-kpi-revenue-cohorts",
                                ResourceSnippetParameters.builder()
                                        .tag("KPI - Admin Internal")
                                        .description(
                                                "코호트 매출·LTV (LUT-601) — 가입 월 × month_n"
                                                        + " {0,1,3,6,12}, 월말이 지난 점만"),
                                queryParameters(
                                        parameterWithName("from_month")
                                                .description("시작 가입 월 (yyyy-MM)"),
                                        parameterWithName("to_month")
                                                .description("끝 가입 월 (yyyy-MM)")),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value[].cohort_month")
                                                .description("가입 월 (yyyy-MM)"),
                                        fieldWithPath("value[].month_n").description("가입 후 개월 수"),
                                        fieldWithPath("value[].cohort_size").description("코호트 인원"),
                                        fieldWithPath("value[].paying_users").description("결제 유저"),
                                        fieldWithPath("value[].cumulative_revenue_krw")
                                                .description("누적 매출 (KRW)"),
                                        fieldWithPath("value[].ltv_krw")
                                                .description("누적 ÷ cohort_size (분모 0 이면 null)")
                                                .type(JsonFieldType.NUMBER)
                                                .optional())));
        // LUT-616: 형식 오류는 플랫폼 500 이 아니라 stats 어드바이스가 400 + 100101 로 응답
        mockMvc.perform(
                        get("/api/internal/kpi/revenue/cohorts")
                                .param("from_month", "202608")
                                .param("to_month", "2026-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("100101"));
        mockMvc.perform(
                        get("/api/internal/kpi/daily")
                                .param("from", "20261020")
                                .param("to", "2026-10-22"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("100101"));
    }

    @Test
    @DisplayName("LUT-616: 모르는 source 값·필수 파라미터 누락도 400 (100101 / 100102)")
    void invalidParamsAreBadRequest() throws Exception {
        mockMvc.perform(
                        get("/api/internal/kpi/retention")
                                .param("cohort_from", "2026-10-01")
                                .param("cohort_to", "2026-10-22")
                                .param("source", "CLICK"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("100101"));
        mockMvc.perform(get("/api/internal/kpi/daily").param("to", "2026-10-22"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("100102"));
        mockMvc.perform(post("/api/internal/kpi/rebuild").param("from", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("100102"));
    }
}
