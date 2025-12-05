package io.pinkspider.leveluptogethermvp.userservice.oauth.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.userservice.oauth.application.JwtService;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.ReissueJwtResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.request.RefreshTokenRequestDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.response.SessionsResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.response.TokenStatusResponseDto;
import io.pinkspider.util.MockUtil;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = JwtController.class,
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
class JwtControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    JwtService jwtService;

    @Test
    @DisplayName("POST /jwt/reissue : JWT 재발행 (expire 된 경우에만 가능)")
    void reissueTest() throws Exception {
        // given
        RefreshTokenRequestDto mockRefreshTokenRequestDto = MockUtil.readJsonFileToClass("fixture/userservice/jwt/mockRefreshTokenRequestDto.json",
            RefreshTokenRequestDto.class);
        ReissueJwtResponseDto mockReissueJwtResponseDto = MockUtil.readJsonFileToClass("fixture/userservice/jwt/mockReissueJwtResponseDto.json",
            ReissueJwtResponseDto.class);

        when(jwtService.reissue(any()))
            .thenReturn(mockReissueJwtResponseDto);

        // then
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/jwt/reissue")
                .contentType("application/json;charset=UTF-8")
                .content(objectMapper.writeValueAsString(mockRefreshTokenRequestDto))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("01. jwt 재발행(access, refresh 둘다)",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .description("01. jwt 재발행(access, refresh 둘다)")
                        .requestHeaders(
                            headerWithName(HttpHeaders.CONTENT_TYPE).description("application/json; charset=UTF-8")
                        )
                        .requestFields(
                            fieldWithPath("refresh_token").type(JsonFieldType.STRING).description("refresh_token"),
                            fieldWithPath("device_type").type(JsonFieldType.STRING).description("device_type"),
                            fieldWithPath("device_id").type(JsonFieldType.STRING).description("device_id")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("code"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("message"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("value"),
                            fieldWithPath("value.access_token").type(JsonFieldType.STRING).description("access_token"),
                            fieldWithPath("value.refresh_token").type(JsonFieldType.STRING).description("refresh_token"),
                            fieldWithPath("value.token_type").type(JsonFieldType.STRING).description("token_type"),
                            fieldWithPath("value.expires_in").type(JsonFieldType.NUMBER).description("expires_in"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("user_id"),
                            fieldWithPath("value.device_id").type(JsonFieldType.STRING).description("device_id")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /jwt/logout : 로그아웃 (해당 디바이스만)")
    void logoutTest() throws Exception {
        // given
        doNothing().when(jwtService).logout(any());

        // then
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/jwt/logout")
                .contentType("application/json;charset=UTF-8")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .header("X-Device-Type", "web")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("02. logout",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .description("02. logout")
                        .requestHeaders(
                            headerWithName(HttpHeaders.CONTENT_TYPE).description("application/json; charset=UTF-8"),
                            headerWithName(HttpHeaders.AUTHORIZATION).description("Bearer token"),
                            headerWithName("X-Device-Type").description("web/ios/android")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("code"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("message")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /jwt/logout-all : 모든 디바이스 로그아웃")
    void logoutAllTest() throws Exception {
        // given
        doNothing().when(jwtService).logoutAll(any());

        // then
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/jwt/logout-all")
                .contentType("application/json;charset=UTF-8")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("03. logout all(모든 디바이스 로그아웃)",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .description("03. logout all(모든 디바이스 로그아웃)")
                        .requestHeaders(
                            headerWithName(HttpHeaders.CONTENT_TYPE).description("application/json; charset=UTF-8"),
                            headerWithName(HttpHeaders.AUTHORIZATION).description("Bearer token")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("code"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("message")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /jwt/sessions : 모든 세션 정보 조회")
    void getActiveSessionsTest() throws Exception {
        // given
        SessionsResponseDto mockSessionsResponseDto = MockUtil.readJsonFileToClass("fixture/userservice/jwt/mockSessionsResponseDto.json",
            SessionsResponseDto.class);

        when(jwtService.getActiveSessions(any()))
            .thenReturn(mockSessionsResponseDto);

        // then
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/jwt/sessions")
                .contentType("application/json;charset=UTF-8")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("04. 모든 세션 정보 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .description("04. 모든 세션 정보 조회")
                        .requestHeaders(
                            headerWithName(HttpHeaders.CONTENT_TYPE).description("application/json; charset=UTF-8")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("code"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("message"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("value"),
                            fieldWithPath("value.session_list[]").type(JsonFieldType.ARRAY).description("session_list"),
                            fieldWithPath("value.session_list[].device_type").type(JsonFieldType.STRING).description("device_type"),
                            fieldWithPath("value.session_list[].refresh_token_remaining").type(JsonFieldType.NUMBER)
                                .description("refresh_token_remaining"),
                            fieldWithPath("value.session_list[].access_token_valid").type(JsonFieldType.BOOLEAN).description("access_token_valid"),
                            fieldWithPath("value.session_list[].login_time").type(JsonFieldType.STRING).description("login_time"),
                            fieldWithPath("value.session_list[].access_token_remaining").type(JsonFieldType.NUMBER)
                                .description("access_token_remaining"),
                            fieldWithPath("value.session_list[].should_renew").type(JsonFieldType.BOOLEAN).description("should_renew"),
                            fieldWithPath("value.session_list[].access_token").type(JsonFieldType.STRING).description("access_token"),
                            fieldWithPath("value.session_list[].device_id").type(JsonFieldType.STRING).description("device_id"),
                            fieldWithPath("value.session_list[].refresh_token_valid").type(JsonFieldType.BOOLEAN).description("refresh_token_valid"),
                            fieldWithPath("value.session_list[].member_id").type(JsonFieldType.STRING).description("member_id"),
                            fieldWithPath("value.session_list[].refresh_token").type(JsonFieldType.STRING).description("refresh_token")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /jwt/token-status : 토큰 상태 정보 조회")
    void getTokenStatusTest() throws Exception {
        // given
        TokenStatusResponseDto mockTokenStatusResponseDto = MockUtil.readJsonFileToClass("fixture/userservice/jwt/mockTokenStatusResponseDto.json",
            TokenStatusResponseDto.class);

        when(jwtService.getTokenStatus(any()))
            .thenReturn(mockTokenStatusResponseDto);

        // then
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/jwt/token-status")
                .contentType("application/json;charset=UTF-8")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("05. 토큰 상태 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .description("05. 토큰 상태 조회")
                        .requestHeaders(
                            headerWithName(HttpHeaders.CONTENT_TYPE).description("application/json; charset=UTF-8")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("code"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("message"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("value"),
                            fieldWithPath("value.refresh_token_remaining").type(JsonFieldType.NUMBER).description("refresh_token_remaining"),
                            fieldWithPath("value.access_token_valid").type(JsonFieldType.BOOLEAN).description("access_token_valid"),
                            fieldWithPath("value.login_time").type(JsonFieldType.NUMBER).description("login_time"),
                            fieldWithPath("value.access_token_remaining").type(JsonFieldType.NUMBER).description("access_token_remaining"),
                            fieldWithPath("value.can_renew_refresh_token").type(JsonFieldType.BOOLEAN).description("can_renew_refresh_token"),
                            fieldWithPath("value.last_refresh_time").type(JsonFieldType.STRING).description("last_refresh_time"),
                            fieldWithPath("value.should_renew_refresh_token").type(JsonFieldType.BOOLEAN).description("should_renew_refresh_token"),
                            fieldWithPath("value.refresh_token_valid").type(JsonFieldType.BOOLEAN).description("refresh_token_valid")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
