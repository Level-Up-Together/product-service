package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application.SubscriptionPaymentHistoryAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionPaymentHistoryPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionPaymentHistoryResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import java.math.BigDecimal;
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

@WebMvcTest(controllers = SubscriptionPaymentHistoryAdminInternalController.class,
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class
    }
)
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class SubscriptionPaymentHistoryAdminInternalControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private SubscriptionPaymentHistoryAdminService subscriptionPaymentHistoryAdminService;

    private SubscriptionPaymentHistoryResponse historyRow(
            Long id, SubscriptionPaymentEventType eventType) {
        return new SubscriptionPaymentHistoryResponse(
            id,
            "user-1",
            "ios",
            "membership_1m",
            null,
            SubscriptionPlan.MONTHLY,
            eventType,
            false,
            new BigDecimal("4900.00"),
            "KRW",
            "tx-" + id,
            LocalDateTime.of(2026, 10, 14, 12, 0),
            LocalDateTime.of(2026, 9, 14, 12, 0));
    }

    @Test
    @DisplayName("GET /api/internal/subscription-payments : 유저별 구독 결제 이력 조회")
    void getPaymentHistory() throws Exception {
        // given
        SubscriptionPaymentHistoryPageResponse response =
            new SubscriptionPaymentHistoryPageResponse(
                List.of(
                    historyRow(2L, SubscriptionPaymentEventType.RENEWAL),
                    historyRow(1L, SubscriptionPaymentEventType.PURCHASE)),
                0, 20, 2L, 1, true, true);
        when(subscriptionPaymentHistoryAdminService.getPaymentHistory(
            eq("user-1"), anyInt(), anyInt())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/internal/subscription-payments")
                .param("user_id", "user-1")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("구독-내부-01. 구독 결제 이력 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Subscription - Admin Internal")
                        .description("유저별 구독 결제 이력 조회 (LUT-486, 어드민 유저 상세 '결제 이력' 탭)")
                        .queryParameters(
                            parameterWithName("user_id").type(SimpleType.STRING)
                                .description("사용자 ID"),
                            parameterWithName("page").type(SimpleType.NUMBER)
                                .description("페이지 번호 (0부터)").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER)
                                .description("페이지 크기").optional())
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("응답 데이터"),
                            fieldWithPath("value.content[]").type(JsonFieldType.ARRAY).description("구독 결제 이력 목록"),
                            fieldWithPath("value.content[].id").type(JsonFieldType.NUMBER).description("이력 ID"),
                            fieldWithPath("value.content[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.content[].platform").type(JsonFieldType.STRING).description("결제 플랫폼 (ios|android)"),
                            fieldWithPath("value.content[].product_id").type(JsonFieldType.STRING).description("스토어 상품 ID"),
                            fieldWithPath("value.content[].base_plan_id").type(JsonFieldType.STRING).description("Android base plan ID — iOS는 null").optional(),
                            fieldWithPath("value.content[].plan").type(JsonFieldType.STRING).description("내부 플랜 (MONTHLY|ANNUAL)"),
                            fieldWithPath("value.content[].event_type").type(JsonFieldType.STRING).description("이벤트 타입 (PURCHASE|RENEWAL|REFUND)"),
                            fieldWithPath("value.content[].trial").type(JsonFieldType.BOOLEAN).description("무료 체험 결제 여부"),
                            fieldWithPath("value.content[].price_amount").type(JsonFieldType.NUMBER).description("결제 금액 (iOS만 — Android는 null)").optional(),
                            fieldWithPath("value.content[].price_currency").type(JsonFieldType.STRING).description("결제 통화 (ISO 4217)").optional(),
                            fieldWithPath("value.content[].transaction_id").type(JsonFieldType.STRING).description("결제 건 트랜잭션 ID").optional(),
                            fieldWithPath("value.content[].expires_at").type(JsonFieldType.STRING).description("이 결제로 확보된 기간 종료 시각 (REFUND는 회수 시각)"),
                            fieldWithPath("value.content[].occurred_at").type(JsonFieldType.STRING).description("이벤트 발생 시각"),
                            fieldWithPath("value.page").type(JsonFieldType.NUMBER).description("현재 페이지 번호"),
                            fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.total_elements").type(JsonFieldType.NUMBER).description("전체 데이터 수"),
                            fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                            fieldWithPath("value.first").type(JsonFieldType.BOOLEAN).description("첫 번째 페이지 여부"),
                            fieldWithPath("value.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.content[0].event_type").value("RENEWAL"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.content[0].price_amount").value(4900.00))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.total_elements").value(2));
    }
}
