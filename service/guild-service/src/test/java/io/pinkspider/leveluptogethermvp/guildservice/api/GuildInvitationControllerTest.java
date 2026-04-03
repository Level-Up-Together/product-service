package io.pinkspider.leveluptogethermvp.guildservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import com.fasterxml.jackson.core.type.TypeReference;
import io.pinkspider.global.component.LmObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildInvitationService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInvitationRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInvitationResponse;
import io.pinkspider.util.MockUtil;
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

@WebMvcTest(controllers = GuildInvitationController.class,
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
class GuildInvitationControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean
    private GuildInvitationService guildInvitationService;

    private static final String MOCK_USER_ID = "test-user-123";

    private static final String FIXTURE_BASE = "fixture/guildservice/invitation/";

    // -------------------------------------------------------------------------
    // 공통 응답 필드 (단일 GuildInvitationResponse)
    // -------------------------------------------------------------------------

    private static final org.springframework.restdocs.payload.FieldDescriptor[] INVITATION_VALUE_FIELDS = {
        fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("초대 ID"),
        fieldWithPath("value.guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
        fieldWithPath("value.guild_name").type(JsonFieldType.STRING).description("길드 이름"),
        fieldWithPath("value.guild_image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
        fieldWithPath("value.inviter_id").type(JsonFieldType.STRING).description("초대자 ID"),
        fieldWithPath("value.inviter_nickname").type(JsonFieldType.STRING).description("초대자 닉네임"),
        fieldWithPath("value.invitee_id").type(JsonFieldType.STRING).description("초대 대상자 ID"),
        fieldWithPath("value.invitee_nickname").type(JsonFieldType.STRING).description("초대 대상자 닉네임"),
        fieldWithPath("value.message").type(JsonFieldType.STRING).description("초대 메시지").optional(),
        fieldWithPath("value.status").type(JsonFieldType.STRING).description("초대 상태 (PENDING, ACCEPTED, REJECTED, CANCELLED, EXPIRED)"),
        fieldWithPath("value.expires_at").type(JsonFieldType.STRING).description("만료 일시"),
        fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성 일시")
    };

    // -------------------------------------------------------------------------
    // 공통 응답 필드 (List<GuildInvitationResponse>)
    // -------------------------------------------------------------------------

    private static final org.springframework.restdocs.payload.FieldDescriptor[] INVITATION_LIST_VALUE_FIELDS = {
        fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("초대 목록"),
        fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("초대 ID"),
        fieldWithPath("value[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
        fieldWithPath("value[].guild_name").type(JsonFieldType.STRING).description("길드 이름"),
        fieldWithPath("value[].guild_image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
        fieldWithPath("value[].inviter_id").type(JsonFieldType.STRING).description("초대자 ID"),
        fieldWithPath("value[].inviter_nickname").type(JsonFieldType.STRING).description("초대자 닉네임"),
        fieldWithPath("value[].invitee_id").type(JsonFieldType.STRING).description("초대 대상자 ID"),
        fieldWithPath("value[].invitee_nickname").type(JsonFieldType.STRING).description("초대 대상자 닉네임"),
        fieldWithPath("value[].message").type(JsonFieldType.STRING).description("초대 메시지").optional(),
        fieldWithPath("value[].status").type(JsonFieldType.STRING).description("초대 상태 (PENDING, ACCEPTED, REJECTED, CANCELLED, EXPIRED)"),
        fieldWithPath("value[].expires_at").type(JsonFieldType.STRING).description("만료 일시"),
        fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("생성 일시")
    };

    // =========================================================================
    // POST /api/v1/guilds/{guildId}/invitations - 초대 발송
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/guilds/{guildId}/invitations : 길드 초대 발송")
    void sendInvitationTest() throws Exception {
        // given
        GuildInvitationRequest request = new GuildInvitationRequest("invitee-user-456", "저희 길드에 초대합니다!");
        GuildInvitationResponse response = MockUtil.readJsonFileToClass(
            FIXTURE_BASE + "mockGuildInvitationResponse.json", GuildInvitationResponse.class);
        when(guildInvitationService.sendInvitation(anyLong(), anyString(), anyString(), any()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guilds/{guildId}/invitations", 10L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드초대-01. 길드 초대 발송",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("GuildInvitation")
                        .description("비공개 길드에서 마스터/부마스터가 다른 유저를 초대합니다.")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .requestFields(
                            fieldWithPath("invitee_id").type(JsonFieldType.STRING).description("초대 대상자 ID (필수)"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("초대 메시지 (선택, 최대 500자)").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("생성된 초대 정보")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("생성된 초대 정보"),
                            INVITATION_VALUE_FIELDS[0],
                            INVITATION_VALUE_FIELDS[1],
                            INVITATION_VALUE_FIELDS[2],
                            INVITATION_VALUE_FIELDS[3],
                            INVITATION_VALUE_FIELDS[4],
                            INVITATION_VALUE_FIELDS[5],
                            INVITATION_VALUE_FIELDS[6],
                            INVITATION_VALUE_FIELDS[7],
                            INVITATION_VALUE_FIELDS[8],
                            INVITATION_VALUE_FIELDS[9],
                            INVITATION_VALUE_FIELDS[10],
                            INVITATION_VALUE_FIELDS[11]
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    // =========================================================================
    // GET /api/v1/guilds/{guildId}/invitations - 길드의 대기중 초대 목록 조회
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/invitations : 길드 대기중 초대 목록 조회")
    void getGuildPendingInvitationsTest() throws Exception {
        // given
        List<GuildInvitationResponse> responseList = MockUtil.readJsonFileToClassList(
            FIXTURE_BASE + "mockGuildInvitationResponseList.json",
            new TypeReference<List<GuildInvitationResponse>>() {});
        when(guildInvitationService.getGuildPendingInvitations(anyLong(), anyString()))
            .thenReturn(responseList);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/invitations", 10L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드초대-02. 길드 대기중 초대 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("GuildInvitation")
                        .description("특정 길드의 대기 중인 초대 목록을 조회합니다. 마스터/부마스터만 조회 가능합니다.")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.ARRAY).description("대기중 초대 목록"),
                            INVITATION_LIST_VALUE_FIELDS[1],
                            INVITATION_LIST_VALUE_FIELDS[2],
                            INVITATION_LIST_VALUE_FIELDS[3],
                            INVITATION_LIST_VALUE_FIELDS[4],
                            INVITATION_LIST_VALUE_FIELDS[5],
                            INVITATION_LIST_VALUE_FIELDS[6],
                            INVITATION_LIST_VALUE_FIELDS[7],
                            INVITATION_LIST_VALUE_FIELDS[8],
                            INVITATION_LIST_VALUE_FIELDS[9],
                            INVITATION_LIST_VALUE_FIELDS[10],
                            INVITATION_LIST_VALUE_FIELDS[11],
                            INVITATION_LIST_VALUE_FIELDS[12]
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    // =========================================================================
    // GET /api/v1/users/me/guild-invitations - 내 대기중 초대 목록 조회
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/users/me/guild-invitations : 내 대기중 초대 목록 조회")
    void getMyPendingInvitationsTest() throws Exception {
        // given
        List<GuildInvitationResponse> responseList = MockUtil.readJsonFileToClassList(
            FIXTURE_BASE + "mockGuildInvitationResponseList.json",
            new TypeReference<List<GuildInvitationResponse>>() {});
        when(guildInvitationService.getMyPendingInvitations(anyString()))
            .thenReturn(responseList);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/users/me/guild-invitations")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드초대-03. 내 대기중 초대 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("GuildInvitation")
                        .description("현재 로그인한 유저가 받은 대기 중인 길드 초대 목록을 조회합니다.")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.ARRAY).description("대기중 초대 목록"),
                            INVITATION_LIST_VALUE_FIELDS[1],
                            INVITATION_LIST_VALUE_FIELDS[2],
                            INVITATION_LIST_VALUE_FIELDS[3],
                            INVITATION_LIST_VALUE_FIELDS[4],
                            INVITATION_LIST_VALUE_FIELDS[5],
                            INVITATION_LIST_VALUE_FIELDS[6],
                            INVITATION_LIST_VALUE_FIELDS[7],
                            INVITATION_LIST_VALUE_FIELDS[8],
                            INVITATION_LIST_VALUE_FIELDS[9],
                            INVITATION_LIST_VALUE_FIELDS[10],
                            INVITATION_LIST_VALUE_FIELDS[11],
                            INVITATION_LIST_VALUE_FIELDS[12]
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    // =========================================================================
    // POST /api/v1/guild-invitations/{invitationId}/accept - 초대 수락
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/guild-invitations/{invitationId}/accept : 길드 초대 수락")
    void acceptInvitationTest() throws Exception {
        // given
        GuildInvitationResponse response = MockUtil.readJsonFileToClass(
            FIXTURE_BASE + "mockGuildInvitationResponse.json", GuildInvitationResponse.class);
        when(guildInvitationService.acceptInvitation(anyLong(), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guild-invitations/{invitationId}/accept", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드초대-04. 길드 초대 수락",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("GuildInvitation")
                        .description("받은 길드 초대를 수락하고 길드에 가입합니다.")
                        .pathParameters(
                            parameterWithName("invitationId").type(SimpleType.NUMBER).description("초대 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("수락된 초대 정보"),
                            INVITATION_VALUE_FIELDS[0],
                            INVITATION_VALUE_FIELDS[1],
                            INVITATION_VALUE_FIELDS[2],
                            INVITATION_VALUE_FIELDS[3],
                            INVITATION_VALUE_FIELDS[4],
                            INVITATION_VALUE_FIELDS[5],
                            INVITATION_VALUE_FIELDS[6],
                            INVITATION_VALUE_FIELDS[7],
                            INVITATION_VALUE_FIELDS[8],
                            INVITATION_VALUE_FIELDS[9],
                            INVITATION_VALUE_FIELDS[10],
                            INVITATION_VALUE_FIELDS[11]
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    // =========================================================================
    // POST /api/v1/guild-invitations/{invitationId}/reject - 초대 거절
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/guild-invitations/{invitationId}/reject : 길드 초대 거절")
    void rejectInvitationTest() throws Exception {
        // given
        doNothing().when(guildInvitationService).rejectInvitation(anyLong(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guild-invitations/{invitationId}/reject", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드초대-05. 길드 초대 거절",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("GuildInvitation")
                        .description("받은 길드 초대를 거절합니다.")
                        .pathParameters(
                            parameterWithName("invitationId").type(SimpleType.NUMBER).description("초대 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.NULL).description("응답 데이터 없음").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    // =========================================================================
    // DELETE /api/v1/guild-invitations/{invitationId} - 초대 취소
    // =========================================================================

    @Test
    @DisplayName("DELETE /api/v1/guild-invitations/{invitationId} : 길드 초대 취소")
    void cancelInvitationTest() throws Exception {
        // given
        doNothing().when(guildInvitationService).cancelInvitation(anyLong(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/guild-invitations/{invitationId}", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드초대-06. 길드 초대 취소",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("GuildInvitation")
                        .description("마스터/부마스터가 발송한 초대를 취소합니다.")
                        .pathParameters(
                            parameterWithName("invitationId").type(SimpleType.NUMBER).description("초대 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.NULL).description("응답 데이터 없음").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
