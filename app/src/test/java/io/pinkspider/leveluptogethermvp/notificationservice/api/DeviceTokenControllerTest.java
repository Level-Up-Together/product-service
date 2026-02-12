package io.pinkspider.leveluptogethermvp.notificationservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = DeviceTokenController.class,
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
class DeviceTokenControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean
    private DeviceTokenService deviceTokenService;

    @MockitoBean
    private FcmPushService fcmPushService;

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
            LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("POST /api/v1/device-tokens : 디바이스 토큰 등록")
    void registerTokenTest() throws Exception {
        // given
        DeviceTokenRequest request = new DeviceTokenRequest(
            "fcm-token-xyz", DeviceType.IOS, "device-id-123", "iPhone 15", "1.0.0"
        );
        DeviceTokenResponse response = createMockDeviceTokenResponse(1L);

        when(deviceTokenService.registerToken(anyString(), any(DeviceTokenRequest.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/device-tokens")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/device-tokens : 디바이스 토큰 해제")
    void unregisterTokenTest() throws Exception {
        // given
        doNothing().when(deviceTokenService).unregisterToken(anyString(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/device-tokens")
                .with(user(MOCK_USER_ID))
                .param("fcm_token", "fcm-token-xyz")
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/device-tokens/all : 모든 디바이스 토큰 해제")
    void unregisterAllTokensTest() throws Exception {
        // given
        doNothing().when(deviceTokenService).unregisterAllTokens(anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/device-tokens/all")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/device-tokens : 등록된 토큰 목록 조회")
    void getTokensTest() throws Exception {
        // given
        List<DeviceTokenResponse> tokens = List.of(
            createMockDeviceTokenResponse(1L),
            createMockDeviceTokenResponse(2L)
        );

        when(deviceTokenService.getTokensByUserId(anyString())).thenReturn(tokens);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/device-tokens")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/device-tokens/badge/reset : 배지 카운트 초기화")
    void resetBadgeCountTest() throws Exception {
        // given
        doNothing().when(deviceTokenService).resetBadgeCount(anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/device-tokens/badge/reset")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
