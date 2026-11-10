package io.pinkspider.leveluptogethermvp.notificationservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.global.component.LmObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.notificationservice.application.DeviceTokenService;
import io.pinkspider.leveluptogethermvp.notificationservice.application.FcmPushService;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.DeviceTokenRequest;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.DeviceTokenResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.DeviceToken.DeviceType;
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

@WebMvcTest(
        controllers = DeviceTokenController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceTokenControllerTest {

    @Autowired protected MockMvc mockMvc;

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean private DeviceTokenService deviceTokenService;

    @MockitoBean private FcmPushService fcmPushService;

    private static final String MOCK_USER_ID = "test-user-123";

    private DeviceTokenResponse createMockDeviceTokenResponse(Long id) {
        return new DeviceTokenResponse(
                id,
                MOCK_USER_ID,
                DeviceType.IOS,
                "device-id-123",
                "iPhone 15",
                "1.0.0",
                true,
                0,
                LocalDateTime.now());
    }

    @Test
    @DisplayName("POST /api/v1/device-tokens : 디바이스 토큰 등록")
    void registerTokenTest() throws Exception {
        // given
        DeviceTokenRequest request =
                new DeviceTokenRequest(
                        "fcm-token-xyz", DeviceType.IOS, "device-id-123", "iPhone 15", "1.0.0");
        DeviceTokenResponse response = createMockDeviceTokenResponse(1L);

        when(deviceTokenService.registerToken(anyString(), any(DeviceTokenRequest.class)))
                .thenReturn(response);

        // when
        ResultActions resultActions =
                mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/v1/device-tokens")
                                .with(user(MOCK_USER_ID))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)));

        // then
        resultActions
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "device-tokens-register",
                                ResourceSnippetParameters.builder()
                                        .tag("Device Token")
                                        .description(
                                                "FCM 푸시 토큰 등록 — 앱이 로그인·토큰 갱신 때 부른다. 같은 기기는 갱신"),
                                requestFields(
                                        fieldWithPath("fcm_token").description("FCM 토큰 (필수)"),
                                        fieldWithPath("device_type")
                                                .description("기기 종류 IOS / ANDROID (필수)"),
                                        fieldWithPath("device_id")
                                                .type(JsonFieldType.STRING)
                                                .description("기기 ID")
                                                .optional(),
                                        fieldWithPath("device_name")
                                                .type(JsonFieldType.STRING)
                                                .description("기기 이름")
                                                .optional(),
                                        fieldWithPath("app_version")
                                                .type(JsonFieldType.STRING)
                                                .description("앱 버전")
                                                .optional()),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.id").description("토큰 ID"),
                                        fieldWithPath("value.user_id").description("유저 ID"),
                                        fieldWithPath("value.device_type")
                                                .description("기기 종류 (IOS / ANDROID)"),
                                        fieldWithPath("value.device_id")
                                                .description("기기 ID")
                                                .optional(),
                                        fieldWithPath("value.device_name")
                                                .description("기기 이름")
                                                .optional(),
                                        fieldWithPath("value.app_version")
                                                .description("앱 버전")
                                                .optional(),
                                        fieldWithPath("value.is_active").description("사용 중인 토큰인가"),
                                        fieldWithPath("value.badge_count")
                                                .description("앱 아이콘 배지 수"),
                                        fieldWithPath("value.created_at")
                                                .description("등록 시각 (UTC)"))));
    }

    @Test
    @DisplayName("DELETE /api/v1/device-tokens : 디바이스 토큰 해제")
    void unregisterTokenTest() throws Exception {
        // given
        doNothing().when(deviceTokenService).unregisterToken(anyString(), anyString());

        // when
        ResultActions resultActions =
                mockMvc.perform(
                        RestDocumentationRequestBuilders.delete("/api/v1/device-tokens")
                                .with(user(MOCK_USER_ID))
                                .queryParam("fcm_token", "fcm-token-xyz")
                                .contentType(MediaType.APPLICATION_JSON));

        // then
        resultActions
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "device-tokens-unregister",
                                ResourceSnippetParameters.builder()
                                        .tag("Device Token")
                                        .description("FCM 토큰 해제 — 로그아웃 때 그 기기 토큰만"),
                                queryParameters(
                                        parameterWithName("fcm_token").description("해제할 FCM 토큰"))));
    }

    @Test
    @DisplayName("DELETE /api/v1/device-tokens/all : 모든 디바이스 토큰 해제")
    void unregisterAllTokensTest() throws Exception {
        // given
        doNothing().when(deviceTokenService).unregisterAllTokens(anyString());

        // when
        ResultActions resultActions =
                mockMvc.perform(
                        RestDocumentationRequestBuilders.delete("/api/v1/device-tokens/all")
                                .with(user(MOCK_USER_ID))
                                .contentType(MediaType.APPLICATION_JSON));

        // then
        resultActions
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "device-tokens-unregister-all",
                                ResourceSnippetParameters.builder()
                                        .tag("Device Token")
                                        .description("내 FCM 토큰 전부 해제")));
    }

    @Test
    @DisplayName("GET /api/v1/device-tokens : 등록된 토큰 목록 조회")
    void getTokensTest() throws Exception {
        // given
        List<DeviceTokenResponse> tokens =
                List.of(createMockDeviceTokenResponse(1L), createMockDeviceTokenResponse(2L));

        when(deviceTokenService.getTokensByUserId(anyString())).thenReturn(tokens);

        // when
        ResultActions resultActions =
                mockMvc.perform(
                        RestDocumentationRequestBuilders.get("/api/v1/device-tokens")
                                .with(user(MOCK_USER_ID))
                                .contentType(MediaType.APPLICATION_JSON));

        // then
        resultActions
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "device-tokens-list",
                                ResourceSnippetParameters.builder()
                                        .tag("Device Token")
                                        .description("내 등록 토큰 목록"),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value[].id").description("토큰 ID"),
                                        fieldWithPath("value[].user_id").description("유저 ID"),
                                        fieldWithPath("value[].device_type")
                                                .description("기기 종류 (IOS / ANDROID)"),
                                        fieldWithPath("value[].device_id")
                                                .description("기기 ID")
                                                .optional(),
                                        fieldWithPath("value[].device_name")
                                                .description("기기 이름")
                                                .optional(),
                                        fieldWithPath("value[].app_version")
                                                .description("앱 버전")
                                                .optional(),
                                        fieldWithPath("value[].is_active")
                                                .description("사용 중인 토큰인가"),
                                        fieldWithPath("value[].badge_count")
                                                .description("앱 아이콘 배지 수"),
                                        fieldWithPath("value[].created_at")
                                                .description("등록 시각 (UTC)"))));
    }

    @Test
    @DisplayName("POST /api/v1/device-tokens/badge/reset : 배지 카운트 초기화")
    void resetBadgeCountTest() throws Exception {
        // given
        doNothing().when(deviceTokenService).resetBadgeCount(anyString());

        // when
        ResultActions resultActions =
                mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/v1/device-tokens/badge/reset")
                                .with(user(MOCK_USER_ID))
                                .contentType(MediaType.APPLICATION_JSON));

        // then
        resultActions
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "device-tokens-badge-reset",
                                ResourceSnippetParameters.builder()
                                        .tag("Device Token")
                                        .description("앱 아이콘 배지 수를 0 으로 (앱 접속 시 호출)")));
    }

    @Test
    @DisplayName("POST /api/v1/device-tokens/test-push : 내 기기로 테스트 푸시 (디버깅용) — 실패해도 200 + 실패 문구")
    void sendTestPushTest() throws Exception {
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/v1/device-tokens/test-push")
                                .with(user(MOCK_USER_ID)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$.value").value("Push sent to user: " + MOCK_USER_ID))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "device-tokens-test-push",
                                ResourceSnippetParameters.builder()
                                        .tag("Device Token")
                                        .description(
                                                "내 기기로 테스트 푸시 보내기 (디버깅용) — 보내기에 실패해도 200, value 에"
                                                        + " 실패 문구"),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value").description("결과 문구"))));

        doThrow(new IllegalStateException("no token"))
                .when(fcmPushService)
                .sendToUser(anyString(), any());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/v1/device-tokens/test-push")
                                .with(user(MOCK_USER_ID)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$.value").value("Failed: no token"));
    }
}
