package io.pinkspider.leveluptogethermvp.statsservice.kpi.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
                                .param("from", "2026-10-01")
                                .param("to", "2026-10-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.snapshot_days").value(22))
                .andExpect(jsonPath("$.value.retention_rows").value(100));
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
                .andExpect(jsonPath("$.value[0].revenue_krw").value(9800.00));
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
                .andExpect(jsonPath("$.value[0].rate").value(0.5));
        mockMvc.perform(
                        get("/api/internal/kpi/retention")
                                .param("cohort_from", "2026-10-01")
                                .param("cohort_to", "2026-10-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").isEmpty());
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
                .andExpect(jsonPath("$.value[0].ltv_krw").value(2450.00));
        mockMvc.perform(
                        get("/api/internal/kpi/revenue/cohorts")
                                .param("from_month", "202608")
                                .param("to_month", "2026-10"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        get("/api/internal/kpi/daily")
                                .param("from", "20261020")
                                .param("to", "2026-10-22"))
                .andExpect(status().isBadRequest());
    }
}
