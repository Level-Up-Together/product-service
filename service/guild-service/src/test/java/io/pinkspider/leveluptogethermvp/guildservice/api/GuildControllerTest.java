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
import io.pinkspider.global.component.LmObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildExperienceService;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildHeadquartersService;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildMemberService;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryService;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildExperienceResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildHeadquartersInfoResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildHeadquartersValidationResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildJoinRequestResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildMemberResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildUpdateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.TransferMasterRequest;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.entity.GuildLevelConfig;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.JoinRequestStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildJoinType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.mock.web.MockMultipartFile;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = GuildController.class,
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
class GuildControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean
    private GuildService guildService;

    @MockitoBean
    private GuildQueryService guildQueryService;

    @MockitoBean
    private GuildMemberService guildMemberService;

    @MockitoBean
    private GuildExperienceService guildExperienceService;

    @MockitoBean
    private GuildHeadquartersService guildHeadquartersService;

    private static final String MOCK_USER_ID = "test-user-123";

    private GuildResponse createMockGuildResponse() {
        return GuildResponse.builder()
            .id(1L)
            .name("테스트 길드")
            .description("테스트 길드입니다.")
            .masterId(MOCK_USER_ID)
            .visibility(GuildVisibility.PUBLIC)
            .joinType(GuildJoinType.OPEN)
            .maxMembers(50)
            .currentMemberCount(10)
            .currentLevel(5)
            .currentExp(500)
            .totalExp(1500)
            .categoryId(1L)
            .categoryName("자기계발")
            .categoryIcon("📚")
            .baseAddress("서울시 강남구")
            .baseLatitude(37.5665)
            .baseLongitude(126.978)
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("POST /api/v1/guilds : 길드 생성")
    void createGuildTest() throws Exception {
        // given
        GuildCreateRequest request = GuildCreateRequest.builder()
            .name("테스트 길드")
            .description("테스트 길드입니다.")
            .visibility(GuildVisibility.PUBLIC)
            .categoryId(1L)
            .maxMembers(50)
            .build();

        when(guildService.createGuild(anyString(), any(GuildCreateRequest.class)))
            .thenReturn(createMockGuildResponse());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guilds")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-01. 길드 생성",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("새 길드 생성 (JWT 토큰 인증 필요)")
                        .requestFields(
                            fieldWithPath("name").type(JsonFieldType.STRING).description("길드 이름"),
                            fieldWithPath("description").type(JsonFieldType.STRING).description("길드 설명").optional(),
                            fieldWithPath("visibility").type(JsonFieldType.STRING).description("공개 여부 (PUBLIC, PRIVATE)"),
                            fieldWithPath("join_type").type(JsonFieldType.STRING).description("가입 방식 (OPEN, APPROVAL, INVITE_ONLY)").optional(),
                            fieldWithPath("category_id").type(JsonFieldType.NUMBER).description("카테고리 ID (필수)"),
                            fieldWithPath("max_members").type(JsonFieldType.NUMBER).description("최대 멤버 수").optional(),
                            fieldWithPath("image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("base_address").type(JsonFieldType.STRING).description("거점 주소").optional(),
                            fieldWithPath("base_latitude").type(JsonFieldType.NUMBER).description("거점 위도").optional(),
                            fieldWithPath("base_longitude").type(JsonFieldType.NUMBER).description("거점 경도").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("길드 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.name").type(JsonFieldType.STRING).description("길드 이름"),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("길드 설명").optional(),
                            fieldWithPath("value.master_id").type(JsonFieldType.STRING).description("마스터 ID"),
                            fieldWithPath("value.visibility").type(JsonFieldType.STRING).description("공개 여부"),
                            fieldWithPath("value.join_type").type(JsonFieldType.STRING).description("가입 방식 (OPEN, APPROVAL, INVITE_ONLY)").optional(),
                            fieldWithPath("value.max_members").type(JsonFieldType.NUMBER).description("최대 멤버 수"),
                            fieldWithPath("value.current_member_count").type(JsonFieldType.NUMBER).description("현재 멤버 수").optional(),
                            fieldWithPath("value.current_level").type(JsonFieldType.NUMBER).description("길드 레벨").optional(),
                            fieldWithPath("value.current_exp").type(JsonFieldType.NUMBER).description("현재 경험치").optional(),
                            fieldWithPath("value.total_exp").type(JsonFieldType.NUMBER).description("길드 누적 경험치").optional(),
                            fieldWithPath("value.category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.category_name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                            fieldWithPath("value.category_icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
                            fieldWithPath("value.base_address").type(JsonFieldType.STRING).description("거점 주소").optional(),
                            fieldWithPath("value.base_latitude").type(JsonFieldType.NUMBER).description("거점 위도").optional(),
                            fieldWithPath("value.base_longitude").type(JsonFieldType.NUMBER).description("거점 경도").optional(),
                            fieldWithPath("value.image_url").type(JsonFieldType.STRING).description("길드 이미지").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시"),
                            fieldWithPath("value.is_under_review").type(JsonFieldType.BOOLEAN).description("신고 처리중 여부").optional(),
                            fieldWithPath("value.is_pending_join_request").type(JsonFieldType.BOOLEAN).description("가입 신청 대기중 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId} : 길드 조회")
    void getGuildTest() throws Exception {
        // given
        when(guildQueryService.getGuild(anyLong(), anyString()))
            .thenReturn(createMockGuildResponse());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-02. 길드 상세 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("길드 상세 정보 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/public : 공개 길드 목록 조회")
    void getPublicGuildsTest() throws Exception {
        // given
        Page<GuildResponse> page = new PageImpl<>(
            List.of(createMockGuildResponse()), PageRequest.of(0, 20), 1);

        when(guildQueryService.getPublicGuilds(any(), any(Pageable.class))).thenReturn(page);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/public")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-03. 공개 길드 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("공개 길드 목록 조회 (페이징)")
                        .queryParameters(
                            parameterWithName("page").type(SimpleType.NUMBER).description("페이지 번호").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER).description("페이지 크기").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/search : 길드 검색")
    void searchGuildsTest() throws Exception {
        // given
        Page<GuildResponse> page = new PageImpl<>(
            List.of(createMockGuildResponse()), PageRequest.of(0, 20), 1);

        when(guildQueryService.searchGuilds(any(), anyString(), any(Pageable.class))).thenReturn(page);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/search")
                .param("keyword", "테스트")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-04. 길드 검색",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("길드 이름으로 검색")
                        .queryParameters(
                            parameterWithName("keyword").type(SimpleType.STRING).description("검색 키워드"),
                            parameterWithName("page").type(SimpleType.NUMBER).description("페이지 번호").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER).description("페이지 크기").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/my : 내 길드 목록")
    void getMyGuildsTest() throws Exception {
        // given
        when(guildQueryService.getMyGuilds(anyString()))
            .thenReturn(List.of(createMockGuildResponse()));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/my")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-05. 내 길드 목록",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("내가 가입한 길드 목록 (JWT 토큰 인증 필요)")
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PUT /api/v1/guilds/{guildId} : 길드 정보 수정")
    void updateGuildTest() throws Exception {
        // given
        GuildUpdateRequest request = GuildUpdateRequest.builder()
            .name("수정된 길드명")
            .description("수정된 설명")
            .build();

        when(guildService.updateGuild(anyLong(), anyString(), any(GuildUpdateRequest.class)))
            .thenReturn(createMockGuildResponse());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.put("/api/v1/guilds/{guildId}", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-06. 길드 정보 수정",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("길드 정보 수정 (마스터만 가능, JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .requestFields(
                            fieldWithPath("name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("description").type(JsonFieldType.STRING).description("길드 설명").optional(),
                            fieldWithPath("visibility").type(JsonFieldType.STRING).description("공개 여부").optional(),
                            fieldWithPath("join_type").type(JsonFieldType.STRING).description("가입 방식 (OPEN, APPROVAL, INVITE_ONLY)").optional(),
                            fieldWithPath("category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("max_members").type(JsonFieldType.NUMBER).description("최대 멤버 수").optional(),
                            fieldWithPath("image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("base_address").type(JsonFieldType.STRING).description("거점 주소").optional(),
                            fieldWithPath("base_latitude").type(JsonFieldType.NUMBER).description("거점 위도").optional(),
                            fieldWithPath("base_longitude").type(JsonFieldType.NUMBER).description("거점 경도").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("길드 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.name").type(JsonFieldType.STRING).description("길드 이름"),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("길드 설명").optional(),
                            fieldWithPath("value.master_id").type(JsonFieldType.STRING).description("마스터 ID"),
                            fieldWithPath("value.visibility").type(JsonFieldType.STRING).description("공개 여부"),
                            fieldWithPath("value.join_type").type(JsonFieldType.STRING).description("가입 방식 (OPEN, APPROVAL, INVITE_ONLY)").optional(),
                            fieldWithPath("value.max_members").type(JsonFieldType.NUMBER).description("최대 멤버 수"),
                            fieldWithPath("value.current_member_count").type(JsonFieldType.NUMBER).description("현재 멤버 수").optional(),
                            fieldWithPath("value.current_level").type(JsonFieldType.NUMBER).description("길드 레벨").optional(),
                            fieldWithPath("value.current_exp").type(JsonFieldType.NUMBER).description("현재 경험치").optional(),
                            fieldWithPath("value.total_exp").type(JsonFieldType.NUMBER).description("길드 누적 경험치").optional(),
                            fieldWithPath("value.category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.category_name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                            fieldWithPath("value.category_icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
                            fieldWithPath("value.base_address").type(JsonFieldType.STRING).description("거점 주소").optional(),
                            fieldWithPath("value.base_latitude").type(JsonFieldType.NUMBER).description("거점 위도").optional(),
                            fieldWithPath("value.base_longitude").type(JsonFieldType.NUMBER).description("거점 경도").optional(),
                            fieldWithPath("value.image_url").type(JsonFieldType.STRING).description("길드 이미지").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시"),
                            fieldWithPath("value.is_under_review").type(JsonFieldType.BOOLEAN).description("신고 처리중 여부").optional(),
                            fieldWithPath("value.is_pending_join_request").type(JsonFieldType.BOOLEAN).description("가입 신청 대기중 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/members : 길드 멤버 목록")
    void getGuildMembersTest() throws Exception {
        // given
        List<GuildMemberResponse> members = List.of(
            GuildMemberResponse.builder()
                .id(1L)
                .guildId(1L)
                .userId(MOCK_USER_ID)
                .role(GuildMemberRole.MASTER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build(),
            GuildMemberResponse.builder()
                .id(2L)
                .guildId(1L)
                .userId("user-456")
                .role(GuildMemberRole.MEMBER)
                .status(GuildMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build()
        );

        when(guildQueryService.getGuildMembers(anyLong(), anyString())).thenReturn(members);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/members", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-07. 길드 멤버 목록",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("길드 멤버 목록 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("멤버 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("멤버십 ID"),
                            fieldWithPath("value[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value[].role").type(JsonFieldType.STRING).description("역할 (MASTER, MANAGER, MEMBER)"),
                            fieldWithPath("value[].status").type(JsonFieldType.STRING).description("상태 (ACTIVE, INACTIVE, BANNED)"),
                            fieldWithPath("value[].joined_at").type(JsonFieldType.STRING).description("가입일시"),
                            fieldWithPath("value[].nickname").type(JsonFieldType.STRING).description("닉네임").optional(),
                            fieldWithPath("value[].profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value[].user_level").type(JsonFieldType.NUMBER).description("사용자 레벨").optional(),
                            fieldWithPath("value[].equipped_title_name").type(JsonFieldType.STRING).description("장착된 칭호명").optional(),
                            fieldWithPath("value[].equipped_title_rarity").type(JsonFieldType.STRING).description("장착된 칭호 희귀도").optional(),
                            fieldWithPath("value[].left_title_name").type(JsonFieldType.STRING).description("왼쪽 칭호명").optional(),
                            fieldWithPath("value[].left_title_rarity").type(JsonFieldType.STRING).description("왼쪽 칭호 희귀도").optional(),
                            fieldWithPath("value[].right_title_name").type(JsonFieldType.STRING).description("오른쪽 칭호명").optional(),
                            fieldWithPath("value[].right_title_rarity").type(JsonFieldType.STRING).description("오른쪽 칭호 희귀도").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/guilds/{guildId}/join-requests : 가입 신청")
    void requestJoinTest() throws Exception {
        // given
        GuildJoinRequestResponse response = GuildJoinRequestResponse.builder()
            .id(1L)
            .guildId(1L)
            .requesterId(MOCK_USER_ID)
            .status(JoinRequestStatus.PENDING)
            .message("가입 신청합니다!")
            .createdAt(LocalDateTime.now())
            .build();

        when(guildMemberService.requestJoin(anyLong(), anyString(), any())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guilds/{guildId}/join-requests", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"가입 신청합니다!\"}")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-08. 가입 신청",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("공개 길드 가입 신청 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("가입 신청 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("신청 ID").optional(),
                            fieldWithPath("value.guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.guild_name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("value.requester_id").type(JsonFieldType.STRING).description("신청자 ID"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("신청 상태 (PENDING, APPROVED, REJECTED, CANCELLED)"),
                            fieldWithPath("value.message").type(JsonFieldType.STRING).description("신청 메시지").optional(),
                            fieldWithPath("value.reject_reason").type(JsonFieldType.STRING).description("거절 사유").optional(),
                            fieldWithPath("value.processed_by").type(JsonFieldType.STRING).description("처리자 ID").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("신청일시"),
                            fieldWithPath("value.processed_at").type(JsonFieldType.STRING).description("처리일시").optional(),
                            fieldWithPath("value.is_member").type(JsonFieldType.BOOLEAN).description("멤버 여부 (APPROVED 시 true)").optional(),
                            fieldWithPath("value.current_member_count").type(JsonFieldType.NUMBER).description("현재 멤버 수 (APPROVED 시 포함)").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/guilds/{guildId}/members/me : 길드 탈퇴")
    void leaveGuildTest() throws Exception {
        // given
        doNothing().when(guildMemberService).leaveGuild(anyLong(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/guilds/{guildId}/members/me", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-09. 길드 탈퇴",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("길드 탈퇴 (마스터는 탈퇴 불가, JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/guilds/{guildId}/transfer-master : 마스터 이양")
    void transferMasterTest() throws Exception {
        // given
        doNothing().when(guildMemberService).transferMaster(anyLong(), anyString(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guilds/{guildId}/transfer-master", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"new_master_id\":\"new-master-123\"}")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-10. 마스터 이양",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("길드 마스터 권한 이양 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .requestFields(
                            fieldWithPath("new_master_id").type(JsonFieldType.STRING).description("새 마스터 사용자 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/experience : 길드 경험치 정보")
    void getGuildExperienceTest() throws Exception {
        // given
        GuildExperienceResponse response = GuildExperienceResponse.builder()
            .guildId(1L)
            .guildName("테스트 길드")
            .currentLevel(5)
            .currentExp(500)
            .totalExp(1500)
            .requiredExpForNextLevel(600)
            .maxMembers(50)
            .levelTitle("성장하는 길드")
            .build();

        when(guildExperienceService.getGuildExperience(anyLong())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/experience", 1L)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-11. 길드 경험치 정보",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("길드 경험치/레벨 정보 조회")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("경험치 정보"),
                            fieldWithPath("value.guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.guild_name").type(JsonFieldType.STRING).description("길드 이름"),
                            fieldWithPath("value.current_level").type(JsonFieldType.NUMBER).description("현재 레벨"),
                            fieldWithPath("value.current_exp").type(JsonFieldType.NUMBER).description("현재 경험치"),
                            fieldWithPath("value.total_exp").type(JsonFieldType.NUMBER).description("누적 경험치"),
                            fieldWithPath("value.required_exp_for_next_level").type(JsonFieldType.NUMBER).description("다음 레벨 필요 경험치"),
                            fieldWithPath("value.max_members").type(JsonFieldType.NUMBER).description("최대 멤버 수"),
                            fieldWithPath("value.level_title").type(JsonFieldType.STRING).description("레벨 타이틀").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/{guildId}/join-requests : 가입 신청 목록 조회")
    void getPendingJoinRequestsTest() throws Exception {
        // given
        List<GuildJoinRequestResponse> requests = List.of(
            GuildJoinRequestResponse.builder()
                .id(1L)
                .guildId(1L)
                .requesterId("user-456")
                .status(JoinRequestStatus.PENDING)
                .message("가입하고 싶습니다")
                .createdAt(LocalDateTime.now())
                .build()
        );
        Page<GuildJoinRequestResponse> page = new PageImpl<>(requests, PageRequest.of(0, 20), 1);

        when(guildMemberService.getPendingJoinRequests(anyLong(), anyString(), any(Pageable.class)))
            .thenReturn(page);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/{guildId}/join-requests", 1L)
                .with(user(MOCK_USER_ID))
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/guilds/join-requests/{requestId}/approve : 가입 승인")
    void approveJoinRequestTest() throws Exception {
        // given
        GuildMemberResponse response = GuildMemberResponse.builder()
            .id(1L)
            .guildId(1L)
            .userId("user-456")
            .role(GuildMemberRole.MEMBER)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();

        when(guildMemberService.approveJoinRequest(anyLong(), anyString())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guilds/join-requests/{requestId}/approve", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/guilds/join-requests/{requestId}/reject : 가입 거절")
    void rejectJoinRequestTest() throws Exception {
        // given
        GuildJoinRequestResponse response = GuildJoinRequestResponse.builder()
            .id(1L)
            .guildId(1L)
            .requesterId("user-456")
            .status(JoinRequestStatus.REJECTED)
            .message("가입하고 싶습니다")
            .rejectReason("조건 미달")
            .createdAt(LocalDateTime.now())
            .build();

        when(guildMemberService.rejectJoinRequest(anyLong(), anyString(), anyString())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guilds/join-requests/{requestId}/reject", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reject_reason\":\"조건 미달\"}")
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/guilds/{guildId}/members/{targetUserId} : 멤버 추방")
    void kickMemberTest() throws Exception {
        // given
        doNothing().when(guildMemberService).kickMember(anyLong(), anyString(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/guilds/{guildId}/members/{targetUserId}", 1L, "user-456")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/guilds/{guildId} : 길드 해체")
    void dissolveGuildTest() throws Exception {
        // given
        doNothing().when(guildService).dissolveGuild(anyLong(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/guilds/{guildId}", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/level-configs : 레벨 설정 조회")
    void getLevelConfigsTest() throws Exception {
        // given
        List<GuildLevelConfig> configs = List.of(
            GuildLevelConfig.builder()
                .level(1)
                .requiredExp(500)
                .maxMembers(20)
                .title("초보 길드")
                .build()
        );

        when(guildExperienceService.getAllLevelConfigs()).thenReturn(configs);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/level-configs")
                .contentType(MediaType.APPLICATION_JSON)
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/guilds/{guildId}/image : 길드 이미지 업로드")
    void uploadGuildImageTest() throws Exception {
        // given
        MockMultipartFile imageFile = new MockMultipartFile(
            "image",
            "guild.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            "test image content".getBytes()
        );

        when(guildService.uploadGuildImage(anyLong(), anyString(), any())).thenReturn(createMockGuildResponse());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.multipart("/api/v1/guilds/{guildId}/image", 1L)
                .file(imageFile)
                .with(user(MOCK_USER_ID))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-12. 길드 이미지 업로드",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("길드 이미지 파일 업로드 (multipart/form-data, 마스터만 가능, JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("길드 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.name").type(JsonFieldType.STRING).description("길드 이름"),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("길드 설명").optional(),
                            fieldWithPath("value.master_id").type(JsonFieldType.STRING).description("마스터 ID"),
                            fieldWithPath("value.visibility").type(JsonFieldType.STRING).description("공개 여부"),
                            fieldWithPath("value.join_type").type(JsonFieldType.STRING).description("가입 방식").optional(),
                            fieldWithPath("value.max_members").type(JsonFieldType.NUMBER).description("최대 멤버 수"),
                            fieldWithPath("value.current_member_count").type(JsonFieldType.NUMBER).description("현재 멤버 수").optional(),
                            fieldWithPath("value.current_level").type(JsonFieldType.NUMBER).description("길드 레벨").optional(),
                            fieldWithPath("value.current_exp").type(JsonFieldType.NUMBER).description("현재 경험치").optional(),
                            fieldWithPath("value.total_exp").type(JsonFieldType.NUMBER).description("길드 누적 경험치").optional(),
                            fieldWithPath("value.category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.category_name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                            fieldWithPath("value.category_icon").type(JsonFieldType.STRING).description("카테고리 아이콘").optional(),
                            fieldWithPath("value.base_address").type(JsonFieldType.STRING).description("거점 주소").optional(),
                            fieldWithPath("value.base_latitude").type(JsonFieldType.NUMBER).description("거점 위도").optional(),
                            fieldWithPath("value.base_longitude").type(JsonFieldType.NUMBER).description("거점 경도").optional(),
                            fieldWithPath("value.image_url").type(JsonFieldType.STRING).description("업로드된 길드 이미지 URL").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시"),
                            fieldWithPath("value.is_under_review").type(JsonFieldType.BOOLEAN).description("신고 처리중 여부").optional(),
                            fieldWithPath("value.is_pending_join_request").type(JsonFieldType.BOOLEAN).description("가입 신청 대기중 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/guilds/{guildId}/members/{targetUserId}/promote-sub-master : 부마스터 승격")
    void promoteToSubMasterTest() throws Exception {
        // given
        GuildMemberResponse response = GuildMemberResponse.builder()
            .id(2L)
            .guildId(1L)
            .userId("user-456")
            .role(GuildMemberRole.SUB_MASTER)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();

        when(guildMemberService.promoteToSubMaster(anyLong(), anyString(), anyString())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post(
                "/api/v1/guilds/{guildId}/members/{targetUserId}/promote-sub-master", 1L, "user-456")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-13. 부마스터 승격",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("특정 멤버를 부마스터(MANAGER)로 승격 (마스터만 가능, JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("targetUserId").type(SimpleType.STRING).description("승격할 멤버의 사용자 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("업데이트된 멤버 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("멤버십 ID"),
                            fieldWithPath("value.guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.role").type(JsonFieldType.STRING).description("변경된 역할 (SUB_MASTER)"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("상태"),
                            fieldWithPath("value.joined_at").type(JsonFieldType.STRING).description("가입일시"),
                            fieldWithPath("value.nickname").type(JsonFieldType.STRING).description("닉네임").optional(),
                            fieldWithPath("value.profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value.user_level").type(JsonFieldType.NUMBER).description("사용자 레벨").optional(),
                            fieldWithPath("value.equipped_title_name").type(JsonFieldType.STRING).description("장착된 칭호명").optional(),
                            fieldWithPath("value.equipped_title_rarity").type(JsonFieldType.STRING).description("장착된 칭호 희귀도").optional(),
                            fieldWithPath("value.left_title_name").type(JsonFieldType.STRING).description("왼쪽 칭호명").optional(),
                            fieldWithPath("value.left_title_rarity").type(JsonFieldType.STRING).description("왼쪽 칭호 희귀도").optional(),
                            fieldWithPath("value.right_title_name").type(JsonFieldType.STRING).description("오른쪽 칭호명").optional(),
                            fieldWithPath("value.right_title_rarity").type(JsonFieldType.STRING).description("오른쪽 칭호 희귀도").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/guilds/{guildId}/members/{targetUserId}/demote-sub-master : 부마스터 해제")
    void demoteFromSubMasterTest() throws Exception {
        // given
        GuildMemberResponse response = GuildMemberResponse.builder()
            .id(2L)
            .guildId(1L)
            .userId("user-456")
            .role(GuildMemberRole.MEMBER)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();

        when(guildMemberService.demoteFromSubMaster(anyLong(), anyString(), anyString())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post(
                "/api/v1/guilds/{guildId}/members/{targetUserId}/demote-sub-master", 1L, "user-456")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-14. 부마스터 해제",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("부마스터(MANAGER)를 일반 멤버로 강등 (마스터만 가능, JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("targetUserId").type(SimpleType.STRING).description("강등할 부마스터의 사용자 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("업데이트된 멤버 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("멤버십 ID"),
                            fieldWithPath("value.guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.role").type(JsonFieldType.STRING).description("변경된 역할 (MEMBER)"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("상태"),
                            fieldWithPath("value.joined_at").type(JsonFieldType.STRING).description("가입일시"),
                            fieldWithPath("value.nickname").type(JsonFieldType.STRING).description("닉네임").optional(),
                            fieldWithPath("value.profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value.user_level").type(JsonFieldType.NUMBER).description("사용자 레벨").optional(),
                            fieldWithPath("value.equipped_title_name").type(JsonFieldType.STRING).description("장착된 칭호명").optional(),
                            fieldWithPath("value.equipped_title_rarity").type(JsonFieldType.STRING).description("장착된 칭호 희귀도").optional(),
                            fieldWithPath("value.left_title_name").type(JsonFieldType.STRING).description("왼쪽 칭호명").optional(),
                            fieldWithPath("value.left_title_rarity").type(JsonFieldType.STRING).description("왼쪽 칭호 희귀도").optional(),
                            fieldWithPath("value.right_title_name").type(JsonFieldType.STRING).description("오른쪽 칭호명").optional(),
                            fieldWithPath("value.right_title_rarity").type(JsonFieldType.STRING).description("오른쪽 칭호 희귀도").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/guilds/headquarters : 길드 거점 목록")
    void getAllHeadquartersTest() throws Exception {
        // given
        GuildHeadquartersInfoResponse.HeadquartersConfig config =
            GuildHeadquartersInfoResponse.HeadquartersConfig.builder()
                .baseRadiusMeters(500)
                .radiusIncreasePerLevelTier(100)
                .levelTierSize(5)
                .build();

        GuildHeadquartersInfoResponse.GuildHeadquartersInfo hq =
            GuildHeadquartersInfoResponse.GuildHeadquartersInfo.builder()
                .guildId(1L)
                .guildName("테스트 길드")
                .guildLevel(5)
                .categoryId(1L)
                .categoryName("자기계발")
                .categoryIcon("📚")
                .latitude(37.5665)
                .longitude(126.978)
                .protectionRadiusMeters(500)
                .build();

        GuildHeadquartersInfoResponse response = GuildHeadquartersInfoResponse.builder()
            .guilds(List.of(hq))
            .config(config)
            .build();

        when(guildHeadquartersService.getAllHeadquartersInfo()).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/guilds/headquarters")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-15. 길드 거점 목록",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("모든 길드 거점 정보 조회 (지도 표시용)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("거점 정보"),
                            fieldWithPath("value.guilds[]").type(JsonFieldType.ARRAY).description("거점을 보유한 길드 목록"),
                            fieldWithPath("value.guilds[].guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.guilds[].guild_name").type(JsonFieldType.STRING).description("길드 이름"),
                            fieldWithPath("value.guilds[].guild_image_url").type(JsonFieldType.STRING).description("길드 이미지 URL").optional(),
                            fieldWithPath("value.guilds[].guild_level").type(JsonFieldType.NUMBER).description("길드 레벨"),
                            fieldWithPath("value.guilds[].category_id").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                            fieldWithPath("value.guilds[].category_name").type(JsonFieldType.STRING).description("카테고리 이름"),
                            fieldWithPath("value.guilds[].category_icon").type(JsonFieldType.STRING).description("카테고리 아이콘"),
                            fieldWithPath("value.guilds[].latitude").type(JsonFieldType.NUMBER).description("거점 위도"),
                            fieldWithPath("value.guilds[].longitude").type(JsonFieldType.NUMBER).description("거점 경도"),
                            fieldWithPath("value.guilds[].protection_radius_meters").type(JsonFieldType.NUMBER).description("보호 반경(미터)"),
                            fieldWithPath("value.config").type(JsonFieldType.OBJECT).description("거점 설정"),
                            fieldWithPath("value.config.base_radius_meters").type(JsonFieldType.NUMBER).description("기본 반경(미터)"),
                            fieldWithPath("value.config.radius_increase_per_level_tier").type(JsonFieldType.NUMBER).description("레벨 티어당 반경 증가량"),
                            fieldWithPath("value.config.level_tier_size").type(JsonFieldType.NUMBER).description("레벨 티어 크기")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/guilds/{guildId}/headquarters/validate : 길드 거점 검증")
    void validateHeadquartersTest() throws Exception {
        // given
        GuildHeadquartersValidationResponse response = GuildHeadquartersValidationResponse.builder()
            .valid(true)
            .message("거점 설정 가능한 위치입니다.")
            .nearbyGuilds(List.of())
            .baseRadiusMeters(500)
            .radiusIncreasePerLevelTier(100)
            .levelTierSize(5)
            .build();

        when(guildHeadquartersService.validateHeadquartersLocation(anyLong(),
            org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.anyDouble())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/guilds/{guildId}/headquarters/validate", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"latitude\":37.5665,\"longitude\":126.978}")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-16. 길드 거점 설정 검증",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("거점 설정 가능 여부 검증 (마스터용, JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID")
                        )
                        .requestFields(
                            fieldWithPath("latitude").type(JsonFieldType.NUMBER).description("검증할 위도"),
                            fieldWithPath("longitude").type(JsonFieldType.NUMBER).description("검증할 경도")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("검증 결과"),
                            fieldWithPath("value.valid").type(JsonFieldType.BOOLEAN).description("거점 설정 가능 여부"),
                            fieldWithPath("value.message").type(JsonFieldType.STRING).description("결과 메시지"),
                            fieldWithPath("value.nearby_guilds").type(JsonFieldType.ARRAY).description("근처 길드 목록").optional(),
                            fieldWithPath("value.base_radius_meters").type(JsonFieldType.NUMBER).description("기본 반경(미터)").optional(),
                            fieldWithPath("value.radius_increase_per_level_tier").type(JsonFieldType.NUMBER).description("레벨 티어당 반경 증가량").optional(),
                            fieldWithPath("value.level_tier_size").type(JsonFieldType.NUMBER).description("레벨 티어 크기").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/guilds/{guildId}/members/{inviteeId} : 멤버 직접 초대")
    void inviteMemberTest() throws Exception {
        // given
        GuildMemberResponse response = GuildMemberResponse.builder()
            .id(3L)
            .guildId(1L)
            .userId("invitee-789")
            .role(GuildMemberRole.MEMBER)
            .status(GuildMemberStatus.ACTIVE)
            .joinedAt(LocalDateTime.now())
            .build();

        when(guildMemberService.inviteMember(anyLong(), anyString(), anyString())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post(
                "/api/v1/guilds/{guildId}/members/{inviteeId}", 1L, "invitee-789")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("길드-17. 멤버 직접 초대",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Guild")
                        .description("비공개 길드 멤버 직접 초대 (마스터 또는 부마스터만 가능, JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.NUMBER).description("길드 ID"),
                            parameterWithName("inviteeId").type(SimpleType.STRING).description("초대할 사용자 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("초대된 멤버 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("멤버십 ID"),
                            fieldWithPath("value.guild_id").type(JsonFieldType.NUMBER).description("길드 ID"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("초대된 사용자 ID"),
                            fieldWithPath("value.role").type(JsonFieldType.STRING).description("역할 (MEMBER)"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("상태 (ACTIVE)"),
                            fieldWithPath("value.joined_at").type(JsonFieldType.STRING).description("가입일시"),
                            fieldWithPath("value.nickname").type(JsonFieldType.STRING).description("닉네임").optional(),
                            fieldWithPath("value.profile_image_url").type(JsonFieldType.STRING).description("프로필 이미지 URL").optional(),
                            fieldWithPath("value.user_level").type(JsonFieldType.NUMBER).description("사용자 레벨").optional(),
                            fieldWithPath("value.equipped_title_name").type(JsonFieldType.STRING).description("장착된 칭호명").optional(),
                            fieldWithPath("value.equipped_title_rarity").type(JsonFieldType.STRING).description("장착된 칭호 희귀도").optional(),
                            fieldWithPath("value.left_title_name").type(JsonFieldType.STRING).description("왼쪽 칭호명").optional(),
                            fieldWithPath("value.left_title_rarity").type(JsonFieldType.STRING).description("왼쪽 칭호 희귀도").optional(),
                            fieldWithPath("value.right_title_name").type(JsonFieldType.STRING).description("오른쪽 칭호명").optional(),
                            fieldWithPath("value.right_title_rarity").type(JsonFieldType.STRING).description("오른쪽 칭호 희귀도").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
