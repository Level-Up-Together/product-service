package io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.application.KakaoWebhookService;
import io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.domain.dto.KakaoUnlinkWebhookRequest;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = KakaoWebhookController.class,
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
class KakaoWebhookControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    KakaoWebhookService kakaoWebhookService;

    @Test
    @DisplayName("GET /api/v1/oauth/kakao/webhook/unlink : 카카오 연결 해제 웹훅 수신 (GET)")
    void handleUnlinkWebhook_GET() throws Exception {
        // given
        String adminKey = "test-admin-key";
        String appId = "123456";
        String userId = "987654321";
        String referrerType = "UNLINK_FROM_APPS";

        doNothing().when(kakaoWebhookService).handleUnlinkWebhook(eq("KakaoAK " + adminKey), any(KakaoUnlinkWebhookRequest.class));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/oauth/kakao/webhook/unlink")
                .header("Authorization", "KakaoAK " + adminKey)
                .param("app_id", appId)
                .param("user_id", userId)
                .param("referrer_type", referrerType)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("카카오 연결 해제 웹훅 (GET)",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .description("카카오에서 사용자가 앱 연결을 끊었을 때 호출되는 웹훅입니다.")
                        .requestHeaders(
                            headerWithName("Authorization").description("KakaoAK ${PRIMARY_ADMIN_KEY} 형식의 어드민 키")
                        )
                        .queryParameters(
                            parameterWithName("app_id").description("연결 해제를 요청한 앱 ID"),
                            parameterWithName("user_id").description("사용자의 카카오 회원번호"),
                            parameterWithName("referrer_type").description("연결 해제 경로 (ACCOUNT_DELETE, UNLINK_FROM_APPS, FORCED_UNLINK_BY_ADMIN)")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
        verify(kakaoWebhookService).handleUnlinkWebhook(eq("KakaoAK " + adminKey), any(KakaoUnlinkWebhookRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/oauth/kakao/webhook/unlink : 카카오 연결 해제 웹훅 수신 (POST)")
    void handleUnlinkWebhook_POST() throws Exception {
        // given
        String adminKey = "test-admin-key";
        String appId = "123456";
        String userId = "987654321";
        String referrerType = "ACCOUNT_DELETE";

        doNothing().when(kakaoWebhookService).handleUnlinkWebhook(eq("KakaoAK " + adminKey), any(KakaoUnlinkWebhookRequest.class));

        // when & then - POST 방식도 GET과 동일하게 동작하는지만 확인 (REST Docs는 GET에서 문서화)
        mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/oauth/kakao/webhook/unlink")
                .header("Authorization", "KakaoAK " + adminKey)
                .param("app_id", appId)
                .param("user_id", userId)
                .param("referrer_type", referrerType)
        ).andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/oauth/kakao/webhook/status : 카카오 계정 상태 변경 웹훅 수신 - 검증 성공")
    void handleAccountStatusWebhook_success() throws Exception {
        // given
        String setToken = "eyJraWQiOiJrYWthb19zc2ZfMSIsInR5cCI6InNlY2V2ZW50K2p3dCIsImFsZyI6IlJTMjU2In0." +
            "eyJhdWQiOiJ0ZXN0LXJlc3QtYXBpLWtleSIsInN1YiI6Ijk4NzY1NDMyMSIsImlzcyI6Imh0dHBzOi8va2F1dGgua2FrYW8uY29tIiwiaWF0IjoxNzA2NzU1MjAwLCJ0b2UiOjE3MDY3NTUyMDAsImp0aSI6InVuaXF1ZS1ldmVudC1pZCIsImV2ZW50cyI6eyJodHRwczovL3NjaGVtYXMua2FrYW8uY29tL2V2ZW50cy9vYXV0aC91c2VyLXVubGlua2VkIjp7fX19." +
            "signature";

        doNothing().when(kakaoWebhookService).handleAccountStatusWebhook(eq(setToken));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/oauth/kakao/webhook/status")
                .contentType("application/secevent+jwt")
                .content(setToken)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("카카오 계정 상태 변경 웹훅",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .description("카카오 계정 상태가 변경되었을 때 호출되는 웹훅입니다. SSF 규격에 따른 SET(Security Event Token) JWT를 수신합니다.")
                        .requestHeaders(
                            headerWithName("Content-Type").description("application/secevent+jwt")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isAccepted());
        verify(kakaoWebhookService).handleAccountStatusWebhook(eq(setToken));
    }

    @Test
    @DisplayName("POST /api/v1/oauth/kakao/webhook/status : 카카오 계정 상태 변경 웹훅 수신 - 검증 실패")
    void handleAccountStatusWebhook_validationFailed() throws Exception {
        // given
        String invalidSetToken = "invalid.set.token";

        doThrow(new KakaoWebhookService.SetValidationException("invalid_request", "Invalid JWT format"))
            .when(kakaoWebhookService).handleAccountStatusWebhook(eq(invalidSetToken));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/oauth/kakao/webhook/status")
                .contentType("application/secevent+jwt")
                .content(invalidSetToken)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("카카오 계정 상태 변경 웹훅 - 검증 실패",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .description("SET 토큰 검증 실패 시 400 Bad Request와 함께 에러 정보를 반환합니다.")
                        .requestHeaders(
                            headerWithName("Content-Type").description("application/secevent+jwt")
                        )
                        .responseFields(
                            fieldWithPath("err").type(JsonFieldType.STRING).description("에러 코드 (invalid_request, invalid_key, invalid_issuer, invalid_audience)"),
                            fieldWithPath("description").type(JsonFieldType.STRING).description("에러 설명")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isBadRequest())
            .andExpect(MockMvcResultMatchers.jsonPath("$.err").value("invalid_request"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.description").value("Invalid JWT format"));
    }

    @Test
    @DisplayName("연결 해제 웹훅 - group_user_token 포함")
    void handleUnlinkWebhook_withGroupUserToken() throws Exception {
        // given
        String adminKey = "test-admin-key";
        String appId = "123456";
        String userId = "987654321";
        String referrerType = "UNLINK_FROM_APPS";
        String groupUserToken = "group-token-12345";

        doNothing().when(kakaoWebhookService).handleUnlinkWebhook(any(), any(KakaoUnlinkWebhookRequest.class));

        // when & then
        mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/oauth/kakao/webhook/unlink")
                .header("Authorization", "KakaoAK " + adminKey)
                .param("app_id", appId)
                .param("user_id", userId)
                .param("referrer_type", referrerType)
                .param("group_user_token", groupUserToken)
        ).andExpect(MockMvcResultMatchers.status().isOk());
    }
}
