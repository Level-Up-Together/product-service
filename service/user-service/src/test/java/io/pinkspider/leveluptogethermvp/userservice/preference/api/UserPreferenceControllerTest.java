package io.pinkspider.leveluptogethermvp.userservice.preference.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
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
import io.pinkspider.leveluptogethermvp.userservice.preference.application.UserUiPreferenceService;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto.UserUiPreferenceRequest;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto.UserUiPreferenceResponse;
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

@WebMvcTest(controllers = UserPreferenceController.class,
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
class UserPreferenceControllerTest {

    private static final String MOCK_USER_ID = "test-user-123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    private UserUiPreferenceService preferenceService;

    @Test
    @DisplayName("GET /api/v1/users/me/preferences : UI 환경설정 조회")
    void getPreferencesTest() throws Exception {
        // given
        when(preferenceService.getPreferences(anyString()))
            .thenReturn(new UserUiPreferenceResponse(true));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/users/me/preferences")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("UI환경설정-01. UI 환경설정 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User Preference")
                        .description("유저 UI 환경설정 조회 — 저장값 없는 유저는 기본값으로 응답 (LUT-437, JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("UI 환경설정"),
                            fieldWithPath("value.mission_completed_section_collapsed").type(JsonFieldType.BOOLEAN)
                                .description("나의 미션 '오늘 완료한 미션' 섹션 접힘 여부 (기본 false)")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PUT /api/v1/users/me/preferences : UI 환경설정 부분 업데이트")
    void updatePreferencesTest() throws Exception {
        // given
        UserUiPreferenceRequest request = UserUiPreferenceRequest.builder()
            .missionCompletedSectionCollapsed(true)
            .build();

        when(preferenceService.updatePreferences(anyString(), any(UserUiPreferenceRequest.class)))
            .thenReturn(new UserUiPreferenceResponse(true));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.put("/api/v1/users/me/preferences")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("UI환경설정-02. UI 환경설정 수정",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User Preference")
                        .description("유저 UI 환경설정 부분 업데이트 — 전송한 필드만 변경, null/미전송 필드는 유지 (LUT-437, JWT 토큰 인증 필요)")
                        .requestFields(
                            fieldWithPath("mission_completed_section_collapsed").type(JsonFieldType.BOOLEAN)
                                .description("나의 미션 '오늘 완료한 미션' 섹션 접힘 여부").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("변경 후 UI 환경설정"),
                            fieldWithPath("value.mission_completed_section_collapsed").type(JsonFieldType.BOOLEAN)
                                .description("나의 미션 '오늘 완료한 미션' 섹션 접힘 여부")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
