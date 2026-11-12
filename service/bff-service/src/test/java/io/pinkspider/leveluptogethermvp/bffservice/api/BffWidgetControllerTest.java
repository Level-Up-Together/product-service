package io.pinkspider.leveluptogethermvp.bffservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.WidgetSummaryResponse;
import io.pinkspider.leveluptogethermvp.bffservice.application.BffWidgetService;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageView;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.enums.WidgetMessageCondition;
import java.time.Instant;
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
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(
        controllers = BffWidgetController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("BffWidgetController 테스트 (LUT-775)")
class BffWidgetControllerTest {

    private static final String MOCK_USER_ID = "test-user-123";

    @Autowired MockMvc mockMvc;
    @MockitoBean BffWidgetService bffWidgetService;

    @Test
    @DisplayName("GET /api/v1/widget/summary : 위젯 요약")
    void getSummary() throws Exception {
        when(bffWidgetService.getSummary(MOCK_USER_ID, "ko"))
                .thenReturn(
                        new WidgetSummaryResponse(
                                12,
                                false,
                                Instant.parse("2026-11-11T09:00:00Z"),
                                "Asia/Seoul",
                                List.of(
                                        new WidgetMessageView(
                                                1L,
                                                6,
                                                12,
                                                WidgetMessageCondition.NOT_DONE,
                                                "아침의 한 걸음이 하루를 바꿔요. 오늘 미션을 시작해 볼까요?",
                                                "https://images.level-up-together.com/widget/0b6f8c1e.png"),
                                        new WidgetMessageView(
                                                2L,
                                                6,
                                                12,
                                                WidgetMessageCondition.DONE,
                                                "아침부터 해냈어요! {streak}일 연속 실천 중이에요.",
                                                null))));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get("/api/v1/widget/summary")
                                .with(user(MOCK_USER_ID))
                                .header(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.value.practice_streak_days").value(12))
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.value.server_time")
                                .value("2026-11-11T09:00:00Z"))
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.value.messages[1].text")
                                .value("아침부터 해냈어요! {streak}일 연속 실천 중이에요."))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "위젯-01. 위젯 요약",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag("Widget")
                                                .description(
                                                        "앱 홈 화면 위젯(LUT-777)이 30~60분마다·앱 신호 때 부른다."
                                                                + " 연속 실천 일수와 오늘 완료 여부, 활성 문장 전체를"
                                                                + " 요청 언어로 내려 주고 위젯이 시간대·완료 여부로"
                                                                + " 고른다. 로그인 필요")
                                                .requestHeaders(
                                                        headerWithName(HttpHeaders.ACCEPT_LANGUAGE)
                                                                .description(
                                                                        "ko·en·ja·ar, 없거나 모르는 언어면"
                                                                                + " en")
                                                                .optional())
                                                .responseFields(
                                                        fieldWithPath("code")
                                                                .type(JsonFieldType.STRING)
                                                                .description("응답 코드"),
                                                        fieldWithPath("message")
                                                                .type(JsonFieldType.STRING)
                                                                .description("응답 메시지"),
                                                        fieldWithPath("value.practice_streak_days")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description(
                                                                        "연속 실천 일수 — 프로필"
                                                                            + " practice_streak_days"
                                                                            + " 와 같은 값(방어권 반영)"),
                                                        fieldWithPath("value.completed_today")
                                                                .type(JsonFieldType.BOOLEAN)
                                                                .description(
                                                                        "유저 타임존 기준 오늘 미션을 하나라도"
                                                                                + " 완료했는가"),
                                                        fieldWithPath("value.server_time")
                                                                .type(JsonFieldType.STRING)
                                                                .description(
                                                                        "응답 시각(UTC, ISO-8601)"),
                                                        fieldWithPath("value.timezone")
                                                                .type(JsonFieldType.STRING)
                                                                .description(
                                                                        "판정에 쓴 유저 타임존(없으면"
                                                                                + " Asia/Seoul)"),
                                                        fieldWithPath("value.messages")
                                                                .type(JsonFieldType.ARRAY)
                                                                .description(
                                                                        "활성 문장 전체(시작 시각 → 정렬 순)"),
                                                        fieldWithPath("value.messages[].id")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description("문장 ID"),
                                                        fieldWithPath("value.messages[].start_hour")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description(
                                                                        "시작 시각(기기 로컬, 0~23, 포함)"),
                                                        fieldWithPath("value.messages[].end_hour")
                                                                .type(JsonFieldType.NUMBER)
                                                                .description(
                                                                        "끝 시각(1~24, 미포함). 시작보다"
                                                                                + " 작으면 자정을 넘는 구간"),
                                                        fieldWithPath("value.messages[].condition")
                                                                .type(JsonFieldType.STRING)
                                                                .description(
                                                                        "NOT_DONE(완료 전) / DONE(완료"
                                                                                + " 후) / ALL"),
                                                        fieldWithPath("value.messages[].text")
                                                                .type(JsonFieldType.STRING)
                                                                .description(
                                                                        "요청 언어 문장(비면 en → ko)."
                                                                            + " {streak} 는 위젯이 연속"
                                                                            + " 일수로 바꾼다"),
                                                        fieldWithPath(
                                                                        "value.messages[].background_image_url")
                                                                .type(JsonFieldType.STRING)
                                                                .description(
                                                                        "그 문장을 보여 줄 때 위젯 배경에 깔 이미지"
                                                                            + " 절대 URL(LUT-797)."
                                                                            + " 없으면 null — 하얀 배경")
                                                                .optional())
                                                .build())));

        verify(bffWidgetService).getSummary(MOCK_USER_ID, "ko");
    }

    @Test
    @DisplayName("Accept-Language 가 없으면 en 으로 조회")
    void defaultLanguage() throws Exception {
        when(bffWidgetService.getSummary(MOCK_USER_ID, "en"))
                .thenReturn(
                        new WidgetSummaryResponse(
                                0, false, Instant.parse("2026-11-11T09:00:00Z"), "UTC", List.of()));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get("/api/v1/widget/summary")
                                .with(user(MOCK_USER_ID)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.value.messages").isEmpty());

        verify(bffWidgetService).getSummary(MOCK_USER_ID, "en");
    }
}
