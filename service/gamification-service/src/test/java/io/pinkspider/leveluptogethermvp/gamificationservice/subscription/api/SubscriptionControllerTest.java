package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.api;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application.SubscriptionGrantService;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application.SubscriptionService;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionEntitlementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionVerifyRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionStatus;
import java.time.LocalDateTime;
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

@WebMvcTest(controllers = SubscriptionController.class,
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
class SubscriptionControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private SubscriptionService subscriptionService;

    @MockitoBean
    private SubscriptionGrantService subscriptionGrantService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String MOCK_USER_ID = "test-user-123";

    @Test
    @DisplayName("GET /api/v1/subscriptions/me : 내 구독 상태 조회 (LUT-450)")
    void getMySubscriptionTest() throws Exception {
        // 유예기간 케이스 — 응답 필드가 전부 채워져 문서화에 적합
        when(subscriptionService.getMyEntitlement(anyString()))
            .thenReturn(new SubscriptionEntitlementResponse(
                SubscriptionStatus.GRACE_PERIOD,
                true,
                SubscriptionPlan.MONTHLY,
                LocalDateTime.of(2026, 9, 1, 0, 0, 0),
                LocalDateTime.of(2026, 9, 15, 0, 0, 0),
                true,
                false));

        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/subscriptions/me")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("구독-01. 내 구독 상태 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Subscription")
                        .description("내 구독 권한(entitlement) 조회 (JWT 토큰 인증 필요) — "
                            + "프론트 구독 상태의 단일 출처. 구독 이력이 없으면 status=NONE")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("구독 권한 정보"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING)
                                .description("구독 상태 (NONE|ACTIVE|GRACE_PERIOD|EXPIRED)"),
                            fieldWithPath("value.is_entitled").type(JsonFieldType.BOOLEAN)
                                .description("구독 권한 보유 여부 (ACTIVE 또는 GRACE_PERIOD)"),
                            fieldWithPath("value.plan").type(JsonFieldType.STRING).optional()
                                .description("내부 플랜 (MONTHLY|ANNUAL) — NONE이면 null"),
                            fieldWithPath("value.expires_at").type(JsonFieldType.STRING).optional()
                                .description("만료 시각 (ISO 8601) — NONE이면 null"),
                            fieldWithPath("value.grace_period_expires_at").type(JsonFieldType.STRING)
                                .optional()
                                .description("유예기간 종료 시각 (ISO 8601) — 유예 중이 아니면 null"),
                            fieldWithPath("value.auto_renew").type(JsonFieldType.BOOLEAN)
                                .description("자동갱신 여부"),
                            fieldWithPath("value.trial_used").type(JsonFieldType.BOOLEAN)
                                .description("무료 체험 사용 여부")
                        )
                        .build()
                )
            )
        );

        resultActions
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.status").value("GRACE_PERIOD"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.is_entitled").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.plan").value("MONTHLY"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.auto_renew").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.trial_used").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/subscriptions/verify : 구독 영수증 검증 + 권한 부여 (LUT-451)")
    void verifySubscriptionTest() throws Exception {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 10, 4, 0, 0, 0);
        when(subscriptionGrantService.verifyAndGrant(anyString(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new SubscriptionEntitlementResponse(
                SubscriptionStatus.ACTIVE, true, SubscriptionPlan.MONTHLY,
                expiresAt, null, true, true));

        SubscriptionVerifyRequest request = SubscriptionVerifyRequest.builder()
            .platform("ios")
            .productId("membership_1m")
            .transactionId("2000000123456789")
            .build();

        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/subscriptions/verify")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("구독-02. 구독 영수증 검증·권한 부여",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Subscription")
                        .description("스토어 구독 영수증 검증 + 권한 부여 (JWT 토큰 인증 필요) — "
                            + "최초 구매와 복원(Restore) 공용, 멱등. "
                            + "ios: transaction_id, android: purchase_token 필수")
                        .requestFields(
                            fieldWithPath("platform").type(JsonFieldType.STRING)
                                .description("결제 플랫폼 (ios|android)"),
                            fieldWithPath("product_id").type(JsonFieldType.STRING)
                                .description("스토어 상품 ID (ios: membership_1m|membership_1y, android: membership)"),
                            fieldWithPath("transaction_id").type(JsonFieldType.STRING).optional()
                                .description("iOS 트랜잭션 ID (App Store Server API 조회 키)"),
                            fieldWithPath("purchase_token").type(JsonFieldType.STRING).optional()
                                .description("Android purchase token (subscriptionsv2 검증 키)"),
                            fieldWithPath("base_plan_id").type(JsonFieldType.STRING).optional()
                                .description("Android base plan ID (1m|1y) — 검증 비활성(dev) 모드 플랜 판정 힌트")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("부여된 구독 권한 정보"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING)
                                .description("구독 상태 (NONE|ACTIVE|GRACE_PERIOD|EXPIRED)"),
                            fieldWithPath("value.is_entitled").type(JsonFieldType.BOOLEAN)
                                .description("구독 권한 보유 여부"),
                            fieldWithPath("value.plan").type(JsonFieldType.STRING).optional()
                                .description("내부 플랜 (MONTHLY|ANNUAL)"),
                            fieldWithPath("value.expires_at").type(JsonFieldType.STRING).optional()
                                .description("만료 시각 (ISO 8601)"),
                            fieldWithPath("value.grace_period_expires_at").type(JsonFieldType.STRING)
                                .optional()
                                .description("유예기간 종료 시각 (ISO 8601) — 유예 중이 아니면 null"),
                            fieldWithPath("value.auto_renew").type(JsonFieldType.BOOLEAN)
                                .description("자동갱신 여부"),
                            fieldWithPath("value.trial_used").type(JsonFieldType.BOOLEAN)
                                .description("무료 체험 사용 여부")
                        )
                        .build()
                )
            )
        );

        resultActions
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.status").value("ACTIVE"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.is_entitled").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.plan").value("MONTHLY"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.trial_used").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/subscriptions/verify : platform 누락이면 400")
    void verifySubscriptionInvalidRequestTest() throws Exception {
        mockMvc.perform(
                RestDocumentationRequestBuilders.post("/api/v1/subscriptions/verify")
                    .with(user(MOCK_USER_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"product_id\":\"membership_1m\"}"))
            .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/subscriptions/me : 구독 이력이 없으면 NONE")
    void getMySubscriptionNoneTest() throws Exception {
        when(subscriptionService.getMyEntitlement(anyString()))
            .thenReturn(SubscriptionEntitlementResponse.none());

        mockMvc.perform(
                RestDocumentationRequestBuilders.get("/api/v1/subscriptions/me")
                    .with(user(MOCK_USER_ID))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.status").value("NONE"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.is_entitled").value(false))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.auto_renew").value(false))
            .andExpect(MockMvcResultMatchers.jsonPath("$.value.trial_used").value(false));
    }
}
