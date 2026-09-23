package io.pinkspider.leveluptogethermvp.guildservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildInviteLinkService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInviteJoinResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInviteLinkResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInvitePreviewResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
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
    controllers = GuildInviteLinkController.class,
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class
    })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuildInviteLinkControllerTest {

    @Autowired protected MockMvc mockMvc;

    @MockitoBean private GuildInviteLinkService guildInviteLinkService;

    private static final String MOCK_USER_ID = "test-user-123";

    // =========================================================================
    // GET /api/v1/guilds/{guildId}/invite-link - 초대 링크 조회
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/invite-link : 초대 링크 조회 (길드원)")
    void getInviteLinkTest() throws Exception {
        // given
        GuildInviteLinkResponse response =
            GuildInviteLinkResponse.builder()
                .code("ABC23XYZ90")
                .invitePath("/guild/invite/ABC23XYZ90")
                .build();
        when(guildInviteLinkService.getOrCreateInviteLink(anyLong(), anyString())).thenReturn(response);

        // when
        ResultActions resultActions =
            mockMvc.perform(
                RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/invite-link", 10L)
                    .with(user(MOCK_USER_ID))
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(
                    MockMvcRestDocumentationWrapper.document(
                        "길드초대링크-01. 초대 링크 조회",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource(
                            ResourceSnippetParameters.builder()
                                .tag("GuildInviteLink")
                                .description("길드원이 길드당 1개·불변인 초대 링크 코드를 조회합니다(없으면 지연 발급).")
                                .pathParameters(
                                    parameterWithName("guildId")
                                        .type(SimpleType.NUMBER)
                                        .description("길드 ID"))
                                .responseFields(
                                    fieldWithPath("code")
                                        .type(JsonFieldType.STRING)
                                        .description("응답 코드"),
                                    fieldWithPath("message")
                                        .type(JsonFieldType.STRING)
                                        .description("응답 메시지"),
                                    fieldWithPath("value")
                                        .type(JsonFieldType.OBJECT)
                                        .description("초대 링크 정보"),
                                    fieldWithPath("value.code")
                                        .type(JsonFieldType.STRING)
                                        .description("초대 코드 (길드당 1개·불변)"),
                                    fieldWithPath("value.invite_path")
                                        .type(JsonFieldType.STRING)
                                        .description("초대 링크 상대 경로 (프론트가 origin 을 붙임)"))
                                .build())));

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    // =========================================================================
    // GET /api/v1/guild-invite-links/{code} - 초대 링크 미리보기 (비로그인 허용)
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/guild-invite-links/{code} : 초대 링크 미리보기")
    void getPreviewTest() throws Exception {
        // given
        GuildInvitePreviewResponse response =
            GuildInvitePreviewResponse.builder()
                .valid(true)
                .joinable(false)
                .alreadyMember(false)
                .reason("FULL")
                .guild(GuildResponse.builder().name("테스트 길드").build())
                .build();
        when(guildInviteLinkService.getPreview(anyString(), anyString())).thenReturn(response);

        // when
        ResultActions resultActions =
            mockMvc.perform(
                RestDocumentationRequestBuilders.get(
                        "/api/v1/guild-invite-links/{code}", "ABC23XYZ90")
                    .with(user(MOCK_USER_ID))
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(
                    MockMvcRestDocumentationWrapper.document(
                        "길드초대링크-02. 초대 링크 미리보기",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource(
                            ResourceSnippetParameters.builder()
                                .tag("GuildInviteLink")
                                .description(
                                    "초대 링크 미리보기(비로그인 허용). 잘못된/삭제된 코드는 valid=false 로 응답합니다.")
                                .pathParameters(
                                    parameterWithName("code")
                                        .type(SimpleType.STRING)
                                        .description("초대 코드"))
                                .responseFields(
                                    fieldWithPath("code")
                                        .type(JsonFieldType.STRING)
                                        .description("응답 코드"),
                                    fieldWithPath("message")
                                        .type(JsonFieldType.STRING)
                                        .description("응답 메시지"),
                                    fieldWithPath("value")
                                        .type(JsonFieldType.OBJECT)
                                        .description("미리보기 정보"),
                                    fieldWithPath("value.valid")
                                        .type(JsonFieldType.BOOLEAN)
                                        .description("코드가 활성 길드로 해석됨"),
                                    fieldWithPath("value.joinable")
                                        .type(JsonFieldType.BOOLEAN)
                                        .description("합류 가능(정원 여유). 이미 멤버면 true"),
                                    fieldWithPath("value.already_member")
                                        .type(JsonFieldType.BOOLEAN)
                                        .description("뷰어가 이미 길드원 (비로그인이면 false)"),
                                    fieldWithPath("value.reason")
                                        .type(JsonFieldType.STRING)
                                        .description("합류 불가 사유: FULL | INVALID (합류 가능하면 생략)")
                                        .optional(),
                                    subsectionWithPath("value.guild")
                                        .type(JsonFieldType.OBJECT)
                                        .description("길드 카드 정보(GuildResponse). valid=false 면 생략")
                                        .optional())
                                .build())));

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    // =========================================================================
    // POST /api/v1/guild-invite-links/{code}/join - 초대 링크 합류
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/guild-invite-links/{code}/join : 초대 링크 합류")
    void joinTest() throws Exception {
        // given
        GuildInviteJoinResponse response = GuildInviteJoinResponse.builder().guildId(10L).build();
        when(guildInviteLinkService.join(anyString(), anyString())).thenReturn(response);

        // when
        ResultActions resultActions =
            mockMvc.perform(
                RestDocumentationRequestBuilders.post(
                        "/api/v1/guild-invite-links/{code}/join", "ABC23XYZ90")
                    .with(user(MOCK_USER_ID))
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(
                    MockMvcRestDocumentationWrapper.document(
                        "길드초대링크-03. 초대 링크 합류",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource(
                            ResourceSnippetParameters.builder()
                                .tag("GuildInviteLink")
                                .description("초대 링크로 길드에 합류합니다(멱등). 비공개 길드도 승인 없이 즉시 합류.")
                                .pathParameters(
                                    parameterWithName("code")
                                        .type(SimpleType.STRING)
                                        .description("초대 코드"))
                                .responseFields(
                                    fieldWithPath("code")
                                        .type(JsonFieldType.STRING)
                                        .description("응답 코드"),
                                    fieldWithPath("message")
                                        .type(JsonFieldType.STRING)
                                        .description("응답 메시지"),
                                    fieldWithPath("value")
                                        .type(JsonFieldType.OBJECT)
                                        .description("합류 결과"),
                                    fieldWithPath("value.guild_id")
                                        .type(JsonFieldType.NUMBER)
                                        .description("합류(또는 이미 멤버)한 길드 ID"))
                                .build())));

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
