package io.pinkspider.leveluptogethermvp.statsservice.missionstats.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ParameterDescriptorWithType;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.statsservice.api.exception.StatsInvalidParamException;
import io.pinkspider.leveluptogethermvp.statsservice.api.exception.StatsRequestExceptionHandler;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.application.MissionStatsService;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.application.MissionStatsUserService;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsSummaryResponse;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsSummaryResponse.Breakdown;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsSummaryResponse.CompletionRate;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsSummaryResponse.Completions;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsSummaryResponse.Created;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsSummaryResponse.Daily;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsUsersResponse;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsUsersResponse.Bucket;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsUsersResponse.UserRow;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.enums.CompletionBucket;
import java.time.LocalDate;
import java.util.ArrayList;
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
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = MissionStatsInternalController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import({ControllerTestConfig.class, StatsRequestExceptionHandler.class})
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("MissionStatsInternalController 테스트 (LUT-790)")
class MissionStatsInternalControllerTest {

    private static final String TAG = "Mission Stats - Admin Internal";
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 2);

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MissionStatsService summaryService;
    @MockitoBean private MissionStatsUserService userService;

    @Test
    @DisplayName("GET /api/internal/mission-stats/summary : 생성·완료·완료율·일별·분류별")
    void summary() throws Exception {
        when(summaryService.summary(FROM, TO))
                .thenReturn(
                        new MissionStatsSummaryResponse(
                                FROM,
                                TO,
                                new Created(42, 3, 10, 32),
                                new Completions(120, 6, 35, 3.4),
                                new CompletionRate(180, 117, 65.0),
                                List.of(
                                        new Daily(FROM, 20, 61, 90, 59, 65.6),
                                        new Daily(TO, 22, 59, 90, 58, 64.4)),
                                List.of(
                                        new Breakdown(
                                                "PERSONAL", null, null, 30, 80, 120, 78, 65.0),
                                        new Breakdown("GUILD", null, null, 4, 15, 20, 14, 70.0),
                                        new Breakdown(
                                                "MISSION_BOOK", null, null, 8, 25, 40, 25, 62.5)),
                                List.of(
                                        new Breakdown("TIMED", null, null, 30, 70, 110, 68, 61.8),
                                        new Breakdown("SIMPLE", null, null, 12, 50, 70, 49, 70.0)),
                                List.of(
                                        new Breakdown("3", "운동", "Workout", 12, 40, 60, 39, 65.0),
                                        new Breakdown(null, null, null, 2, 0, 0, 0, null))));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get("/api/internal/mission-stats/summary")
                                .param("from", "2026-11-01")
                                .param("to", "2026-11-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.created.total").value(42))
                .andExpect(jsonPath("$.value.completions.auto_completed").value(6))
                .andExpect(jsonPath("$.value.completion_rate.rate").value(65.0))
                .andExpect(jsonPath("$.value.daily[1].scheduled_completed").value(58))
                .andExpect(jsonPath("$.value.by_category[0].name_en").value("Workout"))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "미션통계-내부API-01. 요약",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description(
                                                        "미션 통계 요약 — 어드민 미션 통계 화면(LUT-791)."
                                                                + " 완료 수는 완료 시각(KST) 기준으로 KPI"
                                                                + " 미션 완료 수와 같고, 완료율은 예정일 기준(월간"
                                                                + " 리포트와 같은 정의). 테스트·운영자 제외 규칙도"
                                                                + " KPI 와 같다. 기간 최대 400일(넘으면 400"
                                                                + " 100101), from>to 는 바꿔서 처리")
                                                .queryParameters(periodParams())
                                                .responseFields(summaryFields())
                                                .build())));
    }

    @Test
    @DisplayName("GET /api/internal/mission-stats/users : 사용자별 완료 수·분포")
    void users() throws Exception {
        when(userService.users(FROM, TO, 0, 20, "completions,desc", "rumi"))
                .thenReturn(
                        new MissionStatsUsersResponse(
                                FROM,
                                TO,
                                "rumi",
                                "completions,desc",
                                0,
                                20,
                                1,
                                1,
                                List.of(
                                        new Bucket(CompletionBucket.ONE, 1, 1, 12),
                                        new Bucket(CompletionBucket.TWO_TO_FOUR, 2, 4, 15),
                                        new Bucket(CompletionBucket.FIVE_TO_NINE, 5, 9, 5),
                                        new Bucket(CompletionBucket.TEN_OR_MORE, 10, null, 3)),
                                List.of(
                                        new UserRow(
                                                "6f1c2a10-0000-4000-8000-000000000001",
                                                "Rumi",
                                                12,
                                                false,
                                                14,
                                                380,
                                                TO))));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get("/api/internal/mission-stats/users")
                                .param("from", "2026-11-01")
                                .param("to", "2026-11-02")
                                .param("page", "0")
                                .param("size", "20")
                                .param("sort", "completions,desc")
                                .param("keyword", "rumi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.users[0].timer_minutes").value(380))
                .andExpect(jsonPath("$.value.distribution[3].bucket").value("TEN_OR_MORE"))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "미션통계-내부API-02. 사용자별",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description(
                                                        "기간 안에 미션을 1번 이상 완료한 유저별 완료 수 —"
                                                                + " 검색·정렬·페이지. 분포는 검색어와 상관없이"
                                                                + " 전체. 허용값 밖 400 100101")
                                                .queryParameters(
                                                        parameterWithName("from")
                                                                .type(SimpleType.STRING)
                                                                .description("시작일(KST, 포함)"),
                                                        parameterWithName("to")
                                                                .type(SimpleType.STRING)
                                                                .description("끝 날(KST, 포함)"),
                                                        parameterWithName("page")
                                                                .type(SimpleType.INTEGER)
                                                                .description("페이지, 0부터 (기본 0)")
                                                                .optional(),
                                                        parameterWithName("size")
                                                                .type(SimpleType.INTEGER)
                                                                .description("페이지 크기 1~100 (기본 20)")
                                                                .optional(),
                                                        parameterWithName("sort")
                                                                .type(SimpleType.STRING)
                                                                .description(
                                                                        "completions·timer_minutes·"
                                                                            + "last_completed_at·nickname·level[,asc|desc]"
                                                                            + " (기본 completions,desc)."
                                                                            + " 값이 없는 유저는 맨 뒤")
                                                                .optional(),
                                                        parameterWithName("keyword")
                                                                .type(SimpleType.STRING)
                                                                .description(
                                                                        "닉네임 일부(대소문자 무시) 또는 유저 ID")
                                                                .optional())
                                                .responseFields(usersFields())
                                                .build())));
    }

    @Test
    @DisplayName("기간 형식 오류·400일 초과·필수 누락은 400")
    void badRequests() throws Exception {
        mockMvc.perform(
                        RestDocumentationRequestBuilders.get("/api/internal/mission-stats/summary")
                                .param("from", "2026/11/01")
                                .param("to", "2026-11-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("100101"));

        when(userService.users(eq(FROM), eq(TO), eq(0), eq(20), isNull(), isNull()))
                .thenThrow(new StatsInvalidParamException("to", "from 부터 400일 이내"));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.get("/api/internal/mission-stats/users")
                                .param("from", "2026-11-01")
                                .param("to", "2026-11-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("100101"));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get("/api/internal/mission-stats/users")
                                .param("from", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("100102"));
    }

    private static ParameterDescriptorWithType[] periodParams() {
        return new ParameterDescriptorWithType[] {
            parameterWithName("from")
                    .type(SimpleType.STRING)
                    .description("시작일(KST, 포함) yyyy-MM-dd"),
            parameterWithName("to").type(SimpleType.STRING).description("끝 날(KST, 포함) yyyy-MM-dd")
        };
    }

    private static FieldDescriptor[] breakdown(String path, String what) {
        return new FieldDescriptor[] {
            fieldWithPath(path + "[].key")
                    .type(JsonFieldType.STRING)
                    .description(what + " — 카테고리는 ID 문자열, 카테고리 없음은 null")
                    .optional(),
            fieldWithPath(path + "[].name")
                    .type(JsonFieldType.STRING)
                    .description("카테고리 이름(지금 이름). 종류·수행 방식·지워진 카테고리는 null")
                    .optional(),
            fieldWithPath(path + "[].name_en")
                    .type(JsonFieldType.STRING)
                    .description("카테고리 영어 이름")
                    .optional(),
            fieldWithPath(path + "[].created").type(JsonFieldType.NUMBER).description("생성 수"),
            fieldWithPath(path + "[].completed")
                    .type(JsonFieldType.NUMBER)
                    .description("완료 수(완료 시각 기준)"),
            fieldWithPath(path + "[].scheduled")
                    .type(JsonFieldType.NUMBER)
                    .description("예정 수(예정일 기준)"),
            fieldWithPath(path + "[].scheduled_completed")
                    .type(JsonFieldType.NUMBER)
                    .description("예정 중 완료 수"),
            fieldWithPath(path + "[].completion_rate")
                    .type(JsonFieldType.NUMBER)
                    .description("완료율(%) = scheduled_completed ÷ scheduled, 예정 0 이면 null")
                    .optional()
        };
    }

    private static FieldDescriptor[] summaryFields() {
        List<FieldDescriptor> fields =
                new ArrayList<>(
                        List.of(
                                fieldWithPath("code")
                                        .type(JsonFieldType.STRING)
                                        .description("응답 코드"),
                                fieldWithPath("message")
                                        .type(JsonFieldType.STRING)
                                        .description("응답 메시지"),
                                fieldWithPath("value.from")
                                        .type(JsonFieldType.STRING)
                                        .description("적용한 시작일(KST)"),
                                fieldWithPath("value.to")
                                        .type(JsonFieldType.STRING)
                                        .description("적용한 끝 날(KST)"),
                                fieldWithPath("value.created.total")
                                        .type(JsonFieldType.NUMBER)
                                        .description(
                                                "만든 미션 — 미션북 가져오기·길드 미션 포함, 어드민이 직접 만든"
                                                        + " 시스템 미션 제외"),
                                fieldWithPath("value.created.deleted")
                                        .type(JsonFieldType.NUMBER)
                                        .description("그중 지금은 지운 미션(total 에 포함)"),
                                fieldWithPath("value.created.pinned")
                                        .type(JsonFieldType.NUMBER)
                                        .description("그중 고정 미션"),
                                fieldWithPath("value.created.regular")
                                        .type(JsonFieldType.NUMBER)
                                        .description("그중 일반 미션"),
                                fieldWithPath("value.completions.total")
                                        .type(JsonFieldType.NUMBER)
                                        .description("완료 수 — KPI 미션 완료 수와 같은 정의"),
                                fieldWithPath("value.completions.auto_completed")
                                        .type(JsonFieldType.NUMBER)
                                        .description("그중 4시간 자동 종료"),
                                fieldWithPath("value.completions.users")
                                        .type(JsonFieldType.NUMBER)
                                        .description("완료한 유저 수(기간 전체 중복 제거)"),
                                fieldWithPath("value.completions.avg_per_user")
                                        .type(JsonFieldType.NUMBER)
                                        .description("1인당 평균 완료 수, 유저 0 이면 null")
                                        .optional(),
                                fieldWithPath("value.completion_rate.scheduled")
                                        .type(JsonFieldType.NUMBER)
                                        .description("예정 수 — 월간 리포트와 같은 정의"),
                                fieldWithPath("value.completion_rate.completed")
                                        .type(JsonFieldType.NUMBER)
                                        .description("예정 중 완료 수"),
                                fieldWithPath("value.completion_rate.rate")
                                        .type(JsonFieldType.NUMBER)
                                        .description("완료율(%), 예정 0 이면 null")
                                        .optional(),
                                fieldWithPath("value.daily[].date")
                                        .type(JsonFieldType.STRING)
                                        .description("날짜(KST) — 기간의 모든 날"),
                                fieldWithPath("value.daily[].created")
                                        .type(JsonFieldType.NUMBER)
                                        .description("생성 수"),
                                fieldWithPath("value.daily[].completed")
                                        .type(JsonFieldType.NUMBER)
                                        .description("완료 수(완료 시각 기준)"),
                                fieldWithPath("value.daily[].scheduled")
                                        .type(JsonFieldType.NUMBER)
                                        .description("예정 수(예정일 기준)"),
                                fieldWithPath("value.daily[].scheduled_completed")
                                        .type(JsonFieldType.NUMBER)
                                        .description("예정 중 완료 수"),
                                fieldWithPath("value.daily[].completion_rate")
                                        .type(JsonFieldType.NUMBER)
                                        .description("완료율(%), 예정 0 이면 null")
                                        .optional()));
        fields.addAll(List.of(breakdown("value.by_type", "PERSONAL·GUILD·MISSION_BOOK")));
        fields.addAll(List.of(breakdown("value.by_mode", "TIMED·SIMPLE")));
        fields.addAll(List.of(breakdown("value.by_category", "카테고리 ID")));
        return fields.toArray(FieldDescriptor[]::new);
    }

    private static FieldDescriptor[] usersFields() {
        return new FieldDescriptor[] {
            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
            fieldWithPath("value.from").type(JsonFieldType.STRING).description("적용한 시작일(KST)"),
            fieldWithPath("value.to").type(JsonFieldType.STRING).description("적용한 끝 날(KST)"),
            fieldWithPath("value.keyword")
                    .type(JsonFieldType.STRING)
                    .description("적용한 검색어(앞뒤 공백 제거), 없으면 null")
                    .optional(),
            fieldWithPath("value.sort").type(JsonFieldType.STRING).description("적용한 정렬"),
            fieldWithPath("value.page").type(JsonFieldType.NUMBER).description("페이지(0부터)"),
            fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
            fieldWithPath("value.total_users")
                    .type(JsonFieldType.NUMBER)
                    .description("검색어 적용 뒤 유저 수"),
            fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
            fieldWithPath("value.distribution[].bucket")
                    .type(JsonFieldType.STRING)
                    .description("ONE·TWO_TO_FOUR·FIVE_TO_NINE·TEN_OR_MORE"),
            fieldWithPath("value.distribution[].min")
                    .type(JsonFieldType.NUMBER)
                    .description("구간 최소 완료 수"),
            fieldWithPath("value.distribution[].max")
                    .type(JsonFieldType.NUMBER)
                    .description("구간 최대 완료 수(포함), 마지막 구간은 null")
                    .optional(),
            fieldWithPath("value.distribution[].users")
                    .type(JsonFieldType.NUMBER)
                    .description("구간 유저 수"),
            fieldWithPath("value.users[].user_id").type(JsonFieldType.STRING).description("유저 ID"),
            fieldWithPath("value.users[].nickname")
                    .type(JsonFieldType.STRING)
                    .description("닉네임(탈퇴 유저는 익명화된 값), 유저 행이 없으면 null")
                    .optional(),
            fieldWithPath("value.users[].level")
                    .type(JsonFieldType.NUMBER)
                    .description("레벨, 없으면 null")
                    .optional(),
            fieldWithPath("value.users[].withdrawn")
                    .type(JsonFieldType.BOOLEAN)
                    .description("탈퇴 여부"),
            fieldWithPath("value.users[].completions")
                    .type(JsonFieldType.NUMBER)
                    .description("기간 완료 수(완료 시각 기준)"),
            fieldWithPath("value.users[].timer_minutes")
                    .type(JsonFieldType.NUMBER)
                    .description("시간 측정 미션의 시작~완료 분 합"),
            fieldWithPath("value.users[].last_completed_date")
                    .type(JsonFieldType.STRING)
                    .description("기간 안 마지막 완료일(KST)")
                    .optional()
        };
    }
}
