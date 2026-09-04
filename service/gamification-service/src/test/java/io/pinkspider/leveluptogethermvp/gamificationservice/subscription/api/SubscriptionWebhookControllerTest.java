package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application.SubscriptionWebhookService;
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

@WebMvcTest(controllers = SubscriptionWebhookController.class,
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
class SubscriptionWebhookControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private SubscriptionWebhookService webhookService;

    @Test
    @DisplayName("POST /api/v1/webhooks/subscriptions/apple : ASSN V2 수신 (LUT-452)")
    void appleWebhookTest() throws Exception {
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/webhooks/subscriptions/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"signedPayload\":\"eyJhbGciOi...signed-jws...\"}")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("구독-03. Apple 구독 웹훅 (ASSN V2)",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Subscription")
                        .description("App Store Server Notifications V2 수신 — Apple 서버가 호출"
                            + " (인증 없음, signedPayload JWS 서명 검증으로 대체). 항상 200 응답")
                        .requestFields(
                            fieldWithPath("signedPayload").type(JsonFieldType.STRING)
                                .description("Apple 서명 JWS payload"))
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"))
                        .build()
                )
            )
        );

        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
        verify(webhookService).handleAppleNotification("eyJhbGciOi...signed-jws...");
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/subscriptions/google : RTDN 수신 (LUT-452)")
    void googleWebhookTest() throws Exception {
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/webhooks/subscriptions/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":{\"data\":\"eyJ0ZXN0Tm90aWZpY2F0aW9uIjp7fX0=\","
                    + "\"messageId\":\"1234\"},\"subscription\":\"projects/p/subscriptions/s\"}")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("구독-04. Google 구독 웹훅 (RTDN)",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Subscription")
                        .description("Google Real-time Developer Notifications(Pub/Sub push) 수신 — "
                            + "페이로드는 신뢰하지 않고 purchaseToken 으로 Play API 재조회. 항상 200 응답")
                        .requestFields(
                            fieldWithPath("message").type(JsonFieldType.OBJECT)
                                .description("Pub/Sub 메시지"),
                            fieldWithPath("message.data").type(JsonFieldType.STRING)
                                .description("base64 인코딩된 DeveloperNotification JSON"),
                            fieldWithPath("message.messageId").type(JsonFieldType.STRING).optional()
                                .description("Pub/Sub 메시지 ID"),
                            fieldWithPath("subscription").type(JsonFieldType.STRING).optional()
                                .description("Pub/Sub 구독 이름"))
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"))
                        .build()
                )
            )
        );

        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
        verify(webhookService).handleGoogleNotification("eyJ0ZXN0Tm90aWZpY2F0aW9uIjp7fX0=");
    }
}
