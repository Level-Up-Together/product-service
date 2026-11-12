package io.pinkspider.leveluptogethermvp.missionservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.missionservice.api.exception.RecommendedMissionException;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionIdempotencyService;
import io.pinkspider.leveluptogethermvp.missionservice.application.RecommendedMissionService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionCompleteResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionStartResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
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
        controllers = RecommendedMissionController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("RecommendedMissionController (LUT-782)")
class RecommendedMissionControllerTest {

    private static final String USER_ID = "test-user-123";
    private static final String KEY = "11111111-2222-4333-8444-555555555555";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RecommendedMissionService recommendedMissionService;
    @MockitoBean private MissionIdempotencyService missionIdempotencyService;

    @Test
    @DisplayName("GET /api/v1/missions/recommended : 켜진 추천 미션 (요청 언어)")
    void list() throws Exception {
        when(recommendedMissionService.getActive("en"))
                .thenReturn(
                        List.of(
                                new RecommendedMissionResponse(
                                        1L,
                                        "Drink a glass of water",
                                        "💧",
                                        MissionExecutionMode.SIMPLE,
                                        null,
                                        10L),
                                new RecommendedMissionResponse(
                                        2L,
                                        "Read for 5 minutes",
                                        "📖",
                                        MissionExecutionMode.TIMED,
                                        5,
                                        5L)));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get("/api/v1/missions/recommended")
                                .with(user(USER_ID))
                                .header("Accept-Language", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value[1].execution_mode").value("TIMED"))
                .andExpect(jsonPath("$.value[1].target_minutes").value(5))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "추천미션-01. 목록",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag("Mission")
                                                .description(
                                                        "첫 미션 온보딩 추천 미션 (LUT-782, 로그인 필요) — 켜진 것만"
                                                                + " 순서대로. 이름은 Accept-Language(비면"
                                                                + " 한국어)")
                                                .requestHeaders(
                                                        headerWithName("Accept-Language")
                                                                .description("이름 언어 (ko·en·ja·ar)")
                                                                .optional())
                                                .responseFields(
                                                        fieldWithPath("code")
                                                                .type(JsonFieldType.STRING)
                                                                .description("응답 코드"),
                                                        fieldWithPath("message")
                                                                .type(JsonFieldType.STRING)
                                                                .description("응답 메시지"),
                                                        fieldWithPath("value[].id")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description("추천 미션 ID"),
                                                        fieldWithPath("value[].title")
                                                                .type(JsonFieldType.STRING)
                                                                .description("이름"),
                                                        fieldWithPath("value[].icon")
                                                                .type(JsonFieldType.STRING)
                                                                .description("카드 이모지"),
                                                        fieldWithPath("value[].execution_mode")
                                                                .type(JsonFieldType.STRING)
                                                                .description(
                                                                        "TIMED(타이머)·SIMPLE(체크)"),
                                                        fieldWithPath("value[].target_minutes")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description(
                                                                        "타이머 목표 시간(분), 체크는 null")
                                                                .optional(),
                                                        fieldWithPath("value[].category_id")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description("미션 카테고리 ID")
                                                                .optional())
                                                .build())));
    }

    @Test
    @DisplayName("POST /api/v1/missions/recommended/{id}/start : 내 미션 생성 + 바로 시작")
    void start() throws Exception {
        when(missionIdempotencyService.startRecommended(eq(USER_ID), eq(KEY), eq(2L), anyString()))
                .thenReturn(
                        new RecommendedMissionStartResponse(
                                MissionResponse.builder().id(77L).title("5분 독서").build(),
                                MissionExecutionResponse.builder()
                                        .id(900L)
                                        .missionId(77L)
                                        .build()));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/missions/recommended/{recommendedId}/start", 2L)
                                .with(user(USER_ID))
                                .header("Idempotency-Key", KEY)
                                .header("Accept-Language", "ko"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.mission.id").value(77))
                .andExpect(jsonPath("$.value.execution.id").value(900))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "추천미션-02. 원탭 시작",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag("Mission")
                                                .description(
                                                        "추천 미션으로 내 미션을 만들고 오늘 수행을 바로 시작한다"
                                                            + " (LUT-782). 체크 미션도 시작만 — 완료는 기존 완료"
                                                            + " API. 진행 중인 미션이 있으면 기존 시작과 같은 오류(만든"
                                                            + " 미션도 롤백). 없거나 꺼진 추천 404(050501)."
                                                            + " Idempotency-Key(UUID) 재요청은 같은 미션,"
                                                            + " 처리 중 409(050402)")
                                                .pathParameters(
                                                        parameterWithName("recommendedId")
                                                                .description("추천 미션 ID"))
                                                .requestHeaders(
                                                        headerWithName("Idempotency-Key")
                                                                .description(
                                                                        "멱등 키 (UUID, 누를 때마다 새로)")
                                                                .optional(),
                                                        headerWithName("Accept-Language")
                                                                .description("응답 미션 이름 언어")
                                                                .optional())
                                                .responseFields(
                                                        fieldWithPath("code")
                                                                .type(JsonFieldType.STRING)
                                                                .description("응답 코드"),
                                                        fieldWithPath("message")
                                                                .type(JsonFieldType.STRING)
                                                                .description("응답 메시지"),
                                                        subsectionWithPath("value.mission")
                                                                .type(JsonFieldType.OBJECT)
                                                                .description(
                                                                        "만든 미션 (GET /missions/{id}"
                                                                                + " 와 같은 모양)"),
                                                        subsectionWithPath("value.execution")
                                                                .type(JsonFieldType.OBJECT)
                                                                .description(
                                                                        "시작한 수행 (PATCH"
                                                                            + " /missions/{id}/executions/start"
                                                                            + " 응답과 같은 모양) — 멱등"
                                                                            + " 재생에서 본문을 못 읽으면"
                                                                            + " null")
                                                                .optional())
                                                .build())));
    }

    @Test
    @DisplayName("Idempotency-Key 없이 보내면 null 로 넘긴다 (매번 시작)")
    void startWithoutKey() throws Exception {
        when(missionIdempotencyService.startRecommended(eq(USER_ID), isNull(), eq(3L), isNull()))
                .thenReturn(
                        new RecommendedMissionStartResponse(
                                MissionResponse.builder().id(78L).build(), null));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/missions/recommended/{recommendedId}/start", 3L)
                                .with(user(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.mission.id").value(78));
    }

    @Test
    @DisplayName("POST /api/v1/missions/recommended/{id}/complete : 체크 추천 미션 생성 + 바로 완료 (LUT-803)")
    void complete() throws Exception {
        when(missionIdempotencyService.completeRecommended(
                        eq(USER_ID), eq(KEY), eq(1L), anyString()))
                .thenReturn(
                        new RecommendedMissionCompleteResponse(
                                MissionResponse.builder().id(77L).title("물 한 잔 마시기").build(),
                                MissionExecutionResponse.builder()
                                        .id(900L)
                                        .missionId(77L)
                                        .status(ExecutionStatus.COMPLETED)
                                        .expEarned(10)
                                        .dailySimpleExpCapped(false)
                                        .build()));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/missions/recommended/{recommendedId}/complete", 1L)
                                .with(user(USER_ID))
                                .header("Idempotency-Key", KEY)
                                .header("Accept-Language", "ko"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.mission.id").value(77))
                .andExpect(jsonPath("$.value.execution.status").value("COMPLETED"))
                .andExpect(jsonPath("$.value.execution.exp_earned").value(10))
                .andExpect(jsonPath("$.value.execution.daily_simple_exp_capped").value(false))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "추천미션-03. 체크 미션 원탭 완료",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag("Mission")
                                                .description(
                                                        "체크(SIMPLE) 추천 미션으로 내 미션을 만들고 바로 완료한다"
                                                            + " (LUT-803) — 수행 중을 거치지 않는다. 화면은 확인을"
                                                            + " 누른 뒤에 부르고, 취소면 아무것도 부르지 않는다(미션이 생기지"
                                                            + " 않는다). 경험치·체크 일일 한도는 기존 완료와 같다. 타이머"
                                                            + " 추천이면 400(050503), 없거나 꺼진 추천"
                                                            + " 404(050501), 진행 중인 미션이 있으면 기존 시작과"
                                                            + " 같은 오류. 완료에 실패하면 만든 미션을 지운다."
                                                            + " Idempotency-Key(UUID) 재요청은 같은 결과,"
                                                            + " 처리 중 409(050402). 완료 횟수 제한은 기존 완료"
                                                            + " API 와 함께 센다(1분 10회)")
                                                .pathParameters(
                                                        parameterWithName("recommendedId")
                                                                .description("추천 미션 ID (체크 미션)"))
                                                .requestHeaders(
                                                        headerWithName("Idempotency-Key")
                                                                .description(
                                                                        "멱등 키 (UUID, 누를 때마다 새로)")
                                                                .optional(),
                                                        headerWithName("Accept-Language")
                                                                .description("응답 미션 이름 언어")
                                                                .optional())
                                                .responseFields(
                                                        fieldWithPath("code")
                                                                .type(JsonFieldType.STRING)
                                                                .description("응답 코드"),
                                                        fieldWithPath("message")
                                                                .type(JsonFieldType.STRING)
                                                                .description("응답 메시지"),
                                                        subsectionWithPath("value.mission")
                                                                .type(JsonFieldType.OBJECT)
                                                                .description(
                                                                        "만든 미션 (GET /missions/{id}"
                                                                                + " 와 같은 모양)"),
                                                        subsectionWithPath("value.execution")
                                                                .type(JsonFieldType.OBJECT)
                                                                .description(
                                                                        "완료된 수행 (PATCH"
                                                                            + " /missions/{id}"
                                                                            + "/executions/{date}/complete"
                                                                            + " 응답과 같은 모양 —"
                                                                            + " exp_earned,"
                                                                            + " daily_simple_exp_capped"
                                                                            + " 등) — 멱등 재생에서 본문을 못"
                                                                            + " 읽으면 null")
                                                                .optional())
                                                .build())));
    }

    @Test
    @DisplayName("원탭 완료 — Idempotency-Key 없이 보내면 null 로 넘긴다 (매번 실행)")
    void completeWithoutKey() throws Exception {
        when(missionIdempotencyService.completeRecommended(eq(USER_ID), isNull(), eq(4L), isNull()))
                .thenReturn(
                        new RecommendedMissionCompleteResponse(
                                MissionResponse.builder().id(79L).build(), null));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/missions/recommended/{recommendedId}/complete", 4L)
                                .with(user(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.mission.id").value(79));
    }

    @Test
    @DisplayName("원탭 완료 — 타이머 추천이면 400(050503)")
    void completeNotSimple() throws Exception {
        when(missionIdempotencyService.completeRecommended(eq(USER_ID), isNull(), eq(2L), isNull()))
                .thenThrow(RecommendedMissionException.notSimple());

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/missions/recommended/{recommendedId}/complete", 2L)
                                .with(user(USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("050503"));
    }
}
