package io.pinkspider.leveluptogethermvp.userservice.oauth.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;

import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.request.MobileLoginRequestDto;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.userservice.oauth.application.Oauth2Service;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.CreateJwtResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.OAuth2LoginUriResponseDto;
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

@WebMvcTest(controllers = Oauth2Controller.class,
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
class Oauth2ControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    Oauth2Service oauth2Service;

    @Test
    @DisplayName("GET /oauth/uri/{provider} : 소셜로그인사의 로그인 페이지 uri 얻기")
    void getOauth2LoginUri() throws Exception {
        // given
        String mockProvider = "kakao";
        OAuth2LoginUriResponseDto mockOAuth2LoginUriResponseDto = MockUtil.readJsonFileToClass(
            "fixture/userservice/oauth/mockOAuth2LoginUriResponseDto.json",
            OAuth2LoginUriResponseDto.class);

        when(oauth2Service.getAppleOauthUri(any(), any()))
            .thenReturn(mockOAuth2LoginUriResponseDto);

        when(oauth2Service.getOauth2LoginUri(any(), any()))
            .thenReturn(mockOAuth2LoginUriResponseDto);

        // then
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/oauth/uri/{provider}", mockProvider)
                .header("Origin", "https://local.level-up-together.com")
                .contentType("application/json;charset=UTF-8")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("01. provider별 oauth login url",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .description("01. provider별 oauth login url")
                        .requestHeaders(
                            headerWithName(HttpHeaders.CONTENT_TYPE).description("application/json; charset=UTF-8")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("code"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("message"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("value"),
                            fieldWithPath("value.auth_url").type(JsonFieldType.STRING).description("auth_url")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /oauth/callback/{provider} : 소셜로그인사에서 로그인 완료하면 callback하는 uri, callback되면 자체 jwt 발행")
    void createJwt() throws Exception {
        // given
        String mockProvider = "kakao";
        CreateJwtResponseDto mockCreateJwtResponseDto = MockUtil.readJsonFileToClass("fixture/userservice/oauth/mockCreateJwtResponseDto.json",
            CreateJwtResponseDto.class);

        when(oauth2Service.createJwt(any(), any(), any(), any(), any(), any()))
            .thenReturn(mockCreateJwtResponseDto);

        // then
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/oauth/callback/{provider}", mockProvider)
                .contentType("application/json;charset=UTF-8")
                .param("code", "code")
                .param("id_token", "id_token")
                .param("device_type", "device_type")
                .param("device_id", "device_id")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("02. 소셜로그인 인증 후 자체 jwt 만들기",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .description("02. 소셜로그인 인증 후 자체 jwt 만들기")
                        .requestHeaders(
                            headerWithName(HttpHeaders.CONTENT_TYPE).description("application/json; charset=UTF-8")
                        )
                        .queryParameters(
                            parameterWithName("code").description("provider로 부터 받은 code").optional(),
                            parameterWithName("id_token").description("apple의 경우 id token").optional(),
                            parameterWithName("device_type").description("디바이스 타입 (web, ios, android").optional(),
                            parameterWithName("device_id").description("휴대폰인경우 device id").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("code"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("message"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("value"),
                            fieldWithPath("value.access_token").type(JsonFieldType.STRING).description("access_token"),
                            fieldWithPath("value.refresh_token").type(JsonFieldType.STRING).description("refresh_token"),
                            fieldWithPath("value.token_type").type(JsonFieldType.STRING).description("token_type"),
                            fieldWithPath("value.expired_time").type(JsonFieldType.STRING).description("expired_time"),
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
    @DisplayName("POST /oauth/mobile/login : 모바일 앱용 소셜 로그인 JWT 발급")
    void mobileLogin() throws Exception {
        // given
        MobileLoginRequestDto requestDto = MobileLoginRequestDto.builder()
            .provider("google")
            .accessToken("mock_access_token_from_native_sdk")
            .deviceType("ios")
            .deviceId("device_uuid_12345")
            .build();

        CreateJwtResponseDto mockCreateJwtResponseDto = MockUtil.readJsonFileToClass(
            "fixture/userservice/oauth/mockCreateJwtResponseDto.json",
            CreateJwtResponseDto.class);

        when(oauth2Service.createJwtFromMobileToken(any(), eq("google"), eq("mock_access_token_from_native_sdk"), eq("ios"), eq("device_uuid_12345")))
            .thenReturn(mockCreateJwtResponseDto);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/oauth/mobile/login")
                .contentType("application/json;charset=UTF-8")
                .content(objectMapper.writeValueAsString(requestDto))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("03. 모바일 앱용 소셜 로그인 JWT 발급",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .description("03. 모바일 앱용 소셜 로그인 JWT 발급 - 네이티브 SDK에서 받은 access_token/id_token을 직접 전달받아 JWT 발급")
                        .requestHeaders(
                            headerWithName(HttpHeaders.CONTENT_TYPE).description("application/json; charset=UTF-8")
                        )
                        .requestFields(
                            fieldWithPath("provider").type(JsonFieldType.STRING).description("OAuth 제공자 (google, kakao, apple)"),
                            fieldWithPath("access_token").type(JsonFieldType.STRING).description("네이티브 SDK에서 받은 access_token (Apple의 경우 id_token)"),
                            fieldWithPath("device_type").type(JsonFieldType.STRING).description("디바이스 타입 (ios, android)").optional(),
                            fieldWithPath("device_id").type(JsonFieldType.STRING).description("디바이스 고유 ID").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("응답 데이터"),
                            fieldWithPath("value.access_token").type(JsonFieldType.STRING).description("JWT Access Token"),
                            fieldWithPath("value.refresh_token").type(JsonFieldType.STRING).description("JWT Refresh Token"),
                            fieldWithPath("value.token_type").type(JsonFieldType.STRING).description("토큰 타입 (Bearer)"),
                            fieldWithPath("value.expired_time").type(JsonFieldType.STRING).description("만료 시간"),
                            fieldWithPath("value.expires_in").type(JsonFieldType.NUMBER).description("만료까지 남은 시간 (초)"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.device_id").type(JsonFieldType.STRING).description("디바이스 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /oauth/mobile/login : 필수 파라미터 누락 시 400 에러")
    void mobileLogin_missingRequiredFields() throws Exception {
        // given - provider 누락
        MobileLoginRequestDto requestDto = MobileLoginRequestDto.builder()
            .accessToken("mock_access_token")
            .build();

        // when & then
        mockMvc.perform(
            RestDocumentationRequestBuilders.post("/oauth/mobile/login")
                .contentType("application/json;charset=UTF-8")
                .content(objectMapper.writeValueAsString(requestDto))
        ).andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    @DisplayName("POST /oauth/mobile/login : accessToken 누락 시 400 에러")
    void mobileLogin_missingAccessToken() throws Exception {
        // given - accessToken 누락
        MobileLoginRequestDto requestDto = MobileLoginRequestDto.builder()
            .provider("google")
            .build();

        // when & then
        mockMvc.perform(
            RestDocumentationRequestBuilders.post("/oauth/mobile/login")
                .contentType("application/json;charset=UTF-8")
                .content(objectMapper.writeValueAsString(requestDto))
        ).andExpect(MockMvcResultMatchers.status().isBadRequest());
    }
}
