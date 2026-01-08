package io.pinkspider.leveluptogethermvp.missionservice.api;

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
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCreateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionUpdateRequest;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = MissionController.class,
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
class MissionControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean
    private MissionService missionService;

    private static final String MOCK_USER_ID = "test-user-123";

    @Test
    @DisplayName("POST /api/v1/missions : 미션 생성")
    void createMissionTest() throws Exception {
        // given
        MissionCreateRequest request = MissionCreateRequest.builder()
            .title("30일 운동 챌린지")
            .description("매일 30분 이상 운동하기")
            .visibility(io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility.PUBLIC)
            .type(io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType.PERSONAL)
            .maxParticipants(50)
            .build();

        MissionResponse response = MockUtil.readJsonFileToClass(
            "fixture/missionservice/missionResponse.json", MissionResponse.class);

        when(missionService.createMission(anyString(), any(MissionCreateRequest.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/missions")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("01. 미션 생성",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission")
                        .description("미션 생성 (JWT 토큰 인증 필요)")
                        .requestFields(
                            fieldWithPath("title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("description").type(JsonFieldType.STRING).description("미션 설명").optional(),
                            fieldWithPath("visibility").type(JsonFieldType.STRING).description("공개 여부 (PUBLIC, PRIVATE)"),
                            fieldWithPath("type").type(JsonFieldType.STRING).description("미션 타입 (PERSONAL, GUILD)"),
                            fieldWithPath("guild_id").type(JsonFieldType.STRING).description("길드 ID (GUILD 타입일 경우 필수)").optional(),
                            fieldWithPath("category_id").type(JsonFieldType.NUMBER).description("카테고리 ID (기존 카테고리 선택 시)").optional(),
                            fieldWithPath("custom_category").type(JsonFieldType.STRING).description("사용자 정의 카테고리 (직접 입력 시)").optional(),
                            fieldWithPath("max_participants").type(JsonFieldType.NUMBER).description("최대 참여 인원").optional(),
                            fieldWithPath("start_at").type(JsonFieldType.STRING).description("시작일시").optional(),
                            fieldWithPath("end_at").type(JsonFieldType.STRING).description("종료일시").optional(),
                            fieldWithPath("mission_interval").type(JsonFieldType.STRING).description("수행 인터벌 (DAILY, EVERY_OTHER_DAY, EVERY_THREE_DAYS, WEEKLY, BIWEEKLY, MONTHLY)").optional(),
                            fieldWithPath("duration_days").type(JsonFieldType.NUMBER).description("미션 기간 (일)").optional(),
                            fieldWithPath("duration_minutes").type(JsonFieldType.NUMBER).description("미션 수행 시간 (분)").optional(),
                            fieldWithPath("exp_per_completion").type(JsonFieldType.NUMBER).description("수행 당 경험치").optional(),
                            fieldWithPath("bonus_exp_on_full_completion").type(JsonFieldType.NUMBER).description("전체 완료 시 보너스 경험치").optional(),
                            fieldWithPath("is_pinned").type(JsonFieldType.BOOLEAN).description("고정 미션 여부").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("미션 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.title_en").type(JsonFieldType.STRING).description("미션 제목 (영어)").optional(),
                            fieldWithPath("value.title_ar").type(JsonFieldType.STRING).description("미션 제목 (아랍어)").optional(),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("미션 설명").optional(),
                            fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("미션 설명 (영어)").optional(),
                            fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("미션 설명 (아랍어)").optional(),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("미션 상태"),
                            fieldWithPath("value.visibility").type(JsonFieldType.STRING).description("공개 여부"),
                            fieldWithPath("value.type").type(JsonFieldType.STRING).description("미션 타입"),
                            fieldWithPath("value.creator_id").type(JsonFieldType.STRING).description("생성자 ID"),
                            fieldWithPath("value.guild_id").type(JsonFieldType.STRING).description("길드 ID").optional(),
                            fieldWithPath("value.guild_name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("value.category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.category_name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                            fieldWithPath("value.max_participants").type(JsonFieldType.NUMBER).description("최대 참여 인원").optional(),
                            fieldWithPath("value.current_participants").type(JsonFieldType.NUMBER).description("현재 참여 인원").optional(),
                            fieldWithPath("value.start_at").type(JsonFieldType.STRING).description("시작일시").optional(),
                            fieldWithPath("value.end_at").type(JsonFieldType.STRING).description("종료일시").optional(),
                            fieldWithPath("value.mission_interval").type(JsonFieldType.STRING).description("수행 인터벌").optional(),
                            fieldWithPath("value.duration_days").type(JsonFieldType.NUMBER).description("미션 기간 (일)").optional(),
                            fieldWithPath("value.duration_minutes").type(JsonFieldType.NUMBER).description("미션 수행 시간 (분)").optional(),
                            fieldWithPath("value.exp_per_completion").type(JsonFieldType.NUMBER).description("수행 당 경험치").optional(),
                            fieldWithPath("value.bonus_exp_on_full_completion").type(JsonFieldType.NUMBER).description("전체 완료 시 보너스 경험치").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value.modified_at").type(JsonFieldType.STRING).description("수정일시").optional(),
                            fieldWithPath("value.source").type(JsonFieldType.STRING).description("미션 출처").optional(),
                            fieldWithPath("value.participation_type").type(JsonFieldType.STRING).description("참여 유형").optional(),
                            fieldWithPath("value.is_customizable").type(JsonFieldType.BOOLEAN).description("커스터마이징 가능 여부").optional(),
                            fieldWithPath("value.is_pinned").type(JsonFieldType.BOOLEAN).description("고정 미션 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/missions/{missionId} : 미션 조회")
    void getMissionTest() throws Exception {
        // given
        Long missionId = 1L;
        MissionResponse response = MockUtil.readJsonFileToClass(
            "fixture/missionservice/missionResponse.json", MissionResponse.class);

        when(missionService.getMission(anyLong()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/missions/{missionId}", missionId)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("02. 미션 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission")
                        .description("미션 상세 조회")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("미션 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.title_en").type(JsonFieldType.STRING).description("미션 제목 (영어)").optional(),
                            fieldWithPath("value.title_ar").type(JsonFieldType.STRING).description("미션 제목 (아랍어)").optional(),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("미션 설명").optional(),
                            fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("미션 설명 (영어)").optional(),
                            fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("미션 설명 (아랍어)").optional(),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("미션 상태"),
                            fieldWithPath("value.visibility").type(JsonFieldType.STRING).description("공개 여부"),
                            fieldWithPath("value.type").type(JsonFieldType.STRING).description("미션 타입"),
                            fieldWithPath("value.creator_id").type(JsonFieldType.STRING).description("생성자 ID"),
                            fieldWithPath("value.guild_id").type(JsonFieldType.STRING).description("길드 ID").optional(),
                            fieldWithPath("value.guild_name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("value.category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.category_name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                            fieldWithPath("value.max_participants").type(JsonFieldType.NUMBER).description("최대 참여 인원").optional(),
                            fieldWithPath("value.current_participants").type(JsonFieldType.NUMBER).description("현재 참여 인원").optional(),
                            fieldWithPath("value.start_at").type(JsonFieldType.STRING).description("시작일시").optional(),
                            fieldWithPath("value.end_at").type(JsonFieldType.STRING).description("종료일시").optional(),
                            fieldWithPath("value.mission_interval").type(JsonFieldType.STRING).description("수행 인터벌").optional(),
                            fieldWithPath("value.duration_days").type(JsonFieldType.NUMBER).description("미션 기간 (일)").optional(),
                            fieldWithPath("value.duration_minutes").type(JsonFieldType.NUMBER).description("미션 수행 시간 (분)").optional(),
                            fieldWithPath("value.exp_per_completion").type(JsonFieldType.NUMBER).description("수행 당 경험치").optional(),
                            fieldWithPath("value.bonus_exp_on_full_completion").type(JsonFieldType.NUMBER).description("전체 완료 시 보너스 경험치").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value.modified_at").type(JsonFieldType.STRING).description("수정일시").optional(),
                            fieldWithPath("value.source").type(JsonFieldType.STRING).description("미션 출처").optional(),
                            fieldWithPath("value.participation_type").type(JsonFieldType.STRING).description("참여 유형").optional(),
                            fieldWithPath("value.is_customizable").type(JsonFieldType.BOOLEAN).description("커스터마이징 가능 여부").optional(),
                            fieldWithPath("value.is_pinned").type(JsonFieldType.BOOLEAN).description("고정 미션 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/missions/my : 내 미션 목록 조회")
    void getMyMissionsTest() throws Exception {
        // given
        List<MissionResponse> responses = MockUtil.readJsonFileToClassList(
            "fixture/missionservice/missionResponseList.json",
            new TypeReference<List<MissionResponse>>() {});

        when(missionService.getMyMissions(anyString()))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/missions/my")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("03. 내 미션 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission")
                        .description("내가 생성한 미션 목록 조회 (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("미션 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value[].title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value[].title_en").type(JsonFieldType.STRING).description("미션 제목 (영어)").optional(),
                            fieldWithPath("value[].title_ar").type(JsonFieldType.STRING).description("미션 제목 (아랍어)").optional(),
                            fieldWithPath("value[].description").type(JsonFieldType.STRING).description("미션 설명").optional(),
                            fieldWithPath("value[].description_en").type(JsonFieldType.STRING).description("미션 설명 (영어)").optional(),
                            fieldWithPath("value[].description_ar").type(JsonFieldType.STRING).description("미션 설명 (아랍어)").optional(),
                            fieldWithPath("value[].status").type(JsonFieldType.STRING).description("미션 상태"),
                            fieldWithPath("value[].visibility").type(JsonFieldType.STRING).description("공개 여부"),
                            fieldWithPath("value[].type").type(JsonFieldType.STRING).description("미션 타입"),
                            fieldWithPath("value[].creator_id").type(JsonFieldType.STRING).description("생성자 ID"),
                            fieldWithPath("value[].guild_id").type(JsonFieldType.STRING).description("길드 ID").optional(),
                            fieldWithPath("value[].guild_name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("value[].category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value[].category_name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                            fieldWithPath("value[].max_participants").type(JsonFieldType.NUMBER).description("최대 참여 인원").optional(),
                            fieldWithPath("value[].current_participants").type(JsonFieldType.NUMBER).description("현재 참여 인원").optional(),
                            fieldWithPath("value[].start_at").type(JsonFieldType.STRING).description("시작일시").optional(),
                            fieldWithPath("value[].end_at").type(JsonFieldType.STRING).description("종료일시").optional(),
                            fieldWithPath("value[].mission_interval").type(JsonFieldType.STRING).description("수행 인터벌").optional(),
                            fieldWithPath("value[].duration_days").type(JsonFieldType.NUMBER).description("미션 기간 (일)").optional(),
                            fieldWithPath("value[].duration_minutes").type(JsonFieldType.NUMBER).description("미션 수행 시간 (분)").optional(),
                            fieldWithPath("value[].exp_per_completion").type(JsonFieldType.NUMBER).description("수행 당 경험치").optional(),
                            fieldWithPath("value[].bonus_exp_on_full_completion").type(JsonFieldType.NUMBER).description("전체 완료 시 보너스 경험치").optional(),
                            fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value[].modified_at").type(JsonFieldType.STRING).description("수정일시").optional(),
                            fieldWithPath("value[].source").type(JsonFieldType.STRING).description("미션 출처").optional(),
                            fieldWithPath("value[].participation_type").type(JsonFieldType.STRING).description("참여 유형").optional(),
                            fieldWithPath("value[].is_customizable").type(JsonFieldType.BOOLEAN).description("커스터마이징 가능 여부").optional(),
                            fieldWithPath("value[].is_pinned").type(JsonFieldType.BOOLEAN).description("고정 미션 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/missions/public : 공개 미션 목록 조회")
    void getPublicOpenMissionsTest() throws Exception {
        // given
        List<MissionResponse> missionList = MockUtil.readJsonFileToClassList(
            "fixture/missionservice/missionResponseList.json",
            new TypeReference<List<MissionResponse>>() {});
        Page<MissionResponse> responses = new PageImpl<>(missionList, PageRequest.of(0, 20), missionList.size());

        when(missionService.getPublicOpenMissions(any()))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/missions/public")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("04. 공개 미션 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission")
                        .description("공개된 모집중 미션 목록 조회 (페이징)")
                        .queryParameters(
                            parameterWithName("page").type(SimpleType.NUMBER).description("페이지 번호 (0부터 시작)").optional(),
                            parameterWithName("size").type(SimpleType.NUMBER).description("페이지 크기").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("페이징된 미션 목록"),
                            fieldWithPath("value.content[]").type(JsonFieldType.ARRAY).description("미션 목록"),
                            fieldWithPath("value.content[].id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.content[].title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.content[].title_en").type(JsonFieldType.STRING).description("미션 제목 (영어)").optional(),
                            fieldWithPath("value.content[].title_ar").type(JsonFieldType.STRING).description("미션 제목 (아랍어)").optional(),
                            fieldWithPath("value.content[].description").type(JsonFieldType.STRING).description("미션 설명").optional(),
                            fieldWithPath("value.content[].description_en").type(JsonFieldType.STRING).description("미션 설명 (영어)").optional(),
                            fieldWithPath("value.content[].description_ar").type(JsonFieldType.STRING).description("미션 설명 (아랍어)").optional(),
                            fieldWithPath("value.content[].status").type(JsonFieldType.STRING).description("미션 상태"),
                            fieldWithPath("value.content[].visibility").type(JsonFieldType.STRING).description("공개 여부"),
                            fieldWithPath("value.content[].type").type(JsonFieldType.STRING).description("미션 타입"),
                            fieldWithPath("value.content[].creator_id").type(JsonFieldType.STRING).description("생성자 ID"),
                            fieldWithPath("value.content[].guild_id").type(JsonFieldType.STRING).description("길드 ID").optional(),
                            fieldWithPath("value.content[].guild_name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("value.content[].category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.content[].category_name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                            fieldWithPath("value.content[].max_participants").type(JsonFieldType.NUMBER).description("최대 참여 인원").optional(),
                            fieldWithPath("value.content[].current_participants").type(JsonFieldType.NUMBER).description("현재 참여 인원").optional(),
                            fieldWithPath("value.content[].start_at").type(JsonFieldType.STRING).description("시작일시").optional(),
                            fieldWithPath("value.content[].end_at").type(JsonFieldType.STRING).description("종료일시").optional(),
                            fieldWithPath("value.content[].mission_interval").type(JsonFieldType.STRING).description("수행 인터벌").optional(),
                            fieldWithPath("value.content[].duration_days").type(JsonFieldType.NUMBER).description("미션 기간 (일)").optional(),
                            fieldWithPath("value.content[].duration_minutes").type(JsonFieldType.NUMBER).description("미션 수행 시간 (분)").optional(),
                            fieldWithPath("value.content[].exp_per_completion").type(JsonFieldType.NUMBER).description("수행 당 경험치").optional(),
                            fieldWithPath("value.content[].bonus_exp_on_full_completion").type(JsonFieldType.NUMBER).description("전체 완료 시 보너스 경험치").optional(),
                            fieldWithPath("value.content[].created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value.content[].modified_at").type(JsonFieldType.STRING).description("수정일시").optional(),
                            fieldWithPath("value.content[].source").type(JsonFieldType.STRING).description("미션 출처").optional(),
                            fieldWithPath("value.content[].participation_type").type(JsonFieldType.STRING).description("참여 유형").optional(),
                            fieldWithPath("value.content[].is_customizable").type(JsonFieldType.BOOLEAN).description("커스터마이징 가능 여부").optional(),
                            fieldWithPath("value.content[].is_pinned").type(JsonFieldType.BOOLEAN).description("고정 미션 여부").optional(),
                            fieldWithPath("value.pageable").type(JsonFieldType.OBJECT).description("페이징 정보"),
                            fieldWithPath("value.pageable.page_number").type(JsonFieldType.NUMBER).description("현재 페이지 번호"),
                            fieldWithPath("value.pageable.page_size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.pageable.sort").type(JsonFieldType.OBJECT).description("정렬 정보"),
                            fieldWithPath("value.pageable.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음 여부"),
                            fieldWithPath("value.pageable.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬 여부"),
                            fieldWithPath("value.pageable.sort.unsorted").type(JsonFieldType.BOOLEAN).description("미정렬 여부"),
                            fieldWithPath("value.pageable.offset").type(JsonFieldType.NUMBER).description("오프셋"),
                            fieldWithPath("value.pageable.paged").type(JsonFieldType.BOOLEAN).description("페이징 여부"),
                            fieldWithPath("value.pageable.unpaged").type(JsonFieldType.BOOLEAN).description("비페이징 여부"),
                            fieldWithPath("value.total_elements").type(JsonFieldType.NUMBER).description("전체 요소 수"),
                            fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                            fieldWithPath("value.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부"),
                            fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.number").type(JsonFieldType.NUMBER).description("현재 페이지 번호"),
                            fieldWithPath("value.sort").type(JsonFieldType.OBJECT).description("정렬 정보"),
                            fieldWithPath("value.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 비어있음 여부"),
                            fieldWithPath("value.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬 여부"),
                            fieldWithPath("value.sort.unsorted").type(JsonFieldType.BOOLEAN).description("미정렬 여부"),
                            fieldWithPath("value.first").type(JsonFieldType.BOOLEAN).description("첫 페이지 여부"),
                            fieldWithPath("value.number_of_elements").type(JsonFieldType.NUMBER).description("현재 페이지 요소 수"),
                            fieldWithPath("value.empty").type(JsonFieldType.BOOLEAN).description("비어있음 여부")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/missions/guild/{guildId} : 길드 미션 목록 조회")
    void getGuildMissionsTest() throws Exception {
        // given
        String guildId = "guild-123";
        List<MissionResponse> responses = MockUtil.readJsonFileToClassList(
            "fixture/missionservice/missionResponseList.json",
            new TypeReference<List<MissionResponse>>() {});

        when(missionService.getGuildMissions(anyString()))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/missions/guild/{guildId}", guildId)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("05. 길드 미션 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission")
                        .description("특정 길드의 미션 목록 조회")
                        .pathParameters(
                            parameterWithName("guildId").type(SimpleType.STRING).description("길드 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("미션 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value[].title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value[].title_en").type(JsonFieldType.STRING).description("미션 제목 (영어)").optional(),
                            fieldWithPath("value[].title_ar").type(JsonFieldType.STRING).description("미션 제목 (아랍어)").optional(),
                            fieldWithPath("value[].description").type(JsonFieldType.STRING).description("미션 설명").optional(),
                            fieldWithPath("value[].description_en").type(JsonFieldType.STRING).description("미션 설명 (영어)").optional(),
                            fieldWithPath("value[].description_ar").type(JsonFieldType.STRING).description("미션 설명 (아랍어)").optional(),
                            fieldWithPath("value[].status").type(JsonFieldType.STRING).description("미션 상태"),
                            fieldWithPath("value[].visibility").type(JsonFieldType.STRING).description("공개 여부"),
                            fieldWithPath("value[].type").type(JsonFieldType.STRING).description("미션 타입"),
                            fieldWithPath("value[].creator_id").type(JsonFieldType.STRING).description("생성자 ID"),
                            fieldWithPath("value[].guild_id").type(JsonFieldType.STRING).description("길드 ID").optional(),
                            fieldWithPath("value[].guild_name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("value[].category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value[].category_name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                            fieldWithPath("value[].max_participants").type(JsonFieldType.NUMBER).description("최대 참여 인원").optional(),
                            fieldWithPath("value[].current_participants").type(JsonFieldType.NUMBER).description("현재 참여 인원").optional(),
                            fieldWithPath("value[].start_at").type(JsonFieldType.STRING).description("시작일시").optional(),
                            fieldWithPath("value[].end_at").type(JsonFieldType.STRING).description("종료일시").optional(),
                            fieldWithPath("value[].mission_interval").type(JsonFieldType.STRING).description("수행 인터벌").optional(),
                            fieldWithPath("value[].duration_days").type(JsonFieldType.NUMBER).description("미션 기간 (일)").optional(),
                            fieldWithPath("value[].duration_minutes").type(JsonFieldType.NUMBER).description("미션 수행 시간 (분)").optional(),
                            fieldWithPath("value[].exp_per_completion").type(JsonFieldType.NUMBER).description("수행 당 경험치").optional(),
                            fieldWithPath("value[].bonus_exp_on_full_completion").type(JsonFieldType.NUMBER).description("전체 완료 시 보너스 경험치").optional(),
                            fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value[].modified_at").type(JsonFieldType.STRING).description("수정일시").optional(),
                            fieldWithPath("value[].source").type(JsonFieldType.STRING).description("미션 출처").optional(),
                            fieldWithPath("value[].participation_type").type(JsonFieldType.STRING).description("참여 유형").optional(),
                            fieldWithPath("value[].is_customizable").type(JsonFieldType.BOOLEAN).description("커스터마이징 가능 여부").optional(),
                            fieldWithPath("value[].is_pinned").type(JsonFieldType.BOOLEAN).description("고정 미션 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PUT /api/v1/missions/{missionId} : 미션 수정")
    void updateMissionTest() throws Exception {
        // given
        Long missionId = 1L;
        MissionUpdateRequest request = MissionUpdateRequest.builder()
            .title("30일 운동 챌린지 (수정)")
            .description("매일 1시간 이상 운동하기")
            .visibility(io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility.PUBLIC)
            .maxParticipants(100)
            .build();

        MissionResponse response = MockUtil.readJsonFileToClass(
            "fixture/missionservice/missionResponse.json", MissionResponse.class);

        when(missionService.updateMission(anyLong(), anyString(), any(MissionUpdateRequest.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.put("/api/v1/missions/{missionId}", missionId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("06. 미션 수정",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission")
                        .description("미션 정보 수정 (DRAFT 상태일 때만 가능) (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
                        )
                        .requestFields(
                            fieldWithPath("title").type(JsonFieldType.STRING).description("미션 제목").optional(),
                            fieldWithPath("description").type(JsonFieldType.STRING).description("미션 설명").optional(),
                            fieldWithPath("visibility").type(JsonFieldType.STRING).description("공개 여부").optional(),
                            fieldWithPath("category_id").type(JsonFieldType.NUMBER).description("카테고리 ID (기존 카테고리 선택 시)").optional(),
                            fieldWithPath("custom_category").type(JsonFieldType.STRING).description("사용자 정의 카테고리 (직접 입력 시)").optional(),
                            fieldWithPath("clear_category").type(JsonFieldType.BOOLEAN).description("카테고리 삭제 여부").optional(),
                            fieldWithPath("max_participants").type(JsonFieldType.NUMBER).description("최대 참여 인원").optional(),
                            fieldWithPath("start_at").type(JsonFieldType.STRING).description("시작일시").optional(),
                            fieldWithPath("end_at").type(JsonFieldType.STRING).description("종료일시").optional(),
                            fieldWithPath("mission_interval").type(JsonFieldType.STRING).description("수행 인터벌").optional(),
                            fieldWithPath("duration_days").type(JsonFieldType.NUMBER).description("미션 기간 (일)").optional(),
                            fieldWithPath("duration_minutes").type(JsonFieldType.NUMBER).description("미션 수행 시간 (분)").optional(),
                            fieldWithPath("exp_per_completion").type(JsonFieldType.NUMBER).description("수행 당 경험치").optional(),
                            fieldWithPath("bonus_exp_on_full_completion").type(JsonFieldType.NUMBER).description("전체 완료 시 보너스 경험치").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("미션 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.title_en").type(JsonFieldType.STRING).description("미션 제목 (영어)").optional(),
                            fieldWithPath("value.title_ar").type(JsonFieldType.STRING).description("미션 제목 (아랍어)").optional(),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("미션 설명").optional(),
                            fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("미션 설명 (영어)").optional(),
                            fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("미션 설명 (아랍어)").optional(),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("미션 상태"),
                            fieldWithPath("value.visibility").type(JsonFieldType.STRING).description("공개 여부"),
                            fieldWithPath("value.type").type(JsonFieldType.STRING).description("미션 타입"),
                            fieldWithPath("value.creator_id").type(JsonFieldType.STRING).description("생성자 ID"),
                            fieldWithPath("value.guild_id").type(JsonFieldType.STRING).description("길드 ID").optional(),
                            fieldWithPath("value.guild_name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("value.category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.category_name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                            fieldWithPath("value.max_participants").type(JsonFieldType.NUMBER).description("최대 참여 인원").optional(),
                            fieldWithPath("value.current_participants").type(JsonFieldType.NUMBER).description("현재 참여 인원").optional(),
                            fieldWithPath("value.start_at").type(JsonFieldType.STRING).description("시작일시").optional(),
                            fieldWithPath("value.end_at").type(JsonFieldType.STRING).description("종료일시").optional(),
                            fieldWithPath("value.mission_interval").type(JsonFieldType.STRING).description("수행 인터벌").optional(),
                            fieldWithPath("value.duration_days").type(JsonFieldType.NUMBER).description("미션 기간 (일)").optional(),
                            fieldWithPath("value.duration_minutes").type(JsonFieldType.NUMBER).description("미션 수행 시간 (분)").optional(),
                            fieldWithPath("value.exp_per_completion").type(JsonFieldType.NUMBER).description("수행 당 경험치").optional(),
                            fieldWithPath("value.bonus_exp_on_full_completion").type(JsonFieldType.NUMBER).description("전체 완료 시 보너스 경험치").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value.modified_at").type(JsonFieldType.STRING).description("수정일시").optional(),
                            fieldWithPath("value.source").type(JsonFieldType.STRING).description("미션 출처").optional(),
                            fieldWithPath("value.participation_type").type(JsonFieldType.STRING).description("참여 유형").optional(),
                            fieldWithPath("value.is_customizable").type(JsonFieldType.BOOLEAN).description("커스터마이징 가능 여부").optional(),
                            fieldWithPath("value.is_pinned").type(JsonFieldType.BOOLEAN).description("고정 미션 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/v1/missions/{missionId}/open : 미션 모집 시작")
    void openMissionTest() throws Exception {
        // given
        Long missionId = 1L;
        MissionResponse response = MockUtil.readJsonFileToClass(
            "fixture/missionservice/missionResponseOpen.json", MissionResponse.class);

        when(missionService.openMission(anyLong(), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.patch("/api/v1/missions/{missionId}/open", missionId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("07. 미션 모집 시작",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission")
                        .description("미션 상태를 OPEN으로 변경하여 참여자 모집 시작 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("미션 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.title_en").type(JsonFieldType.STRING).description("미션 제목 (영어)").optional(),
                            fieldWithPath("value.title_ar").type(JsonFieldType.STRING).description("미션 제목 (아랍어)").optional(),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("미션 설명").optional(),
                            fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("미션 설명 (영어)").optional(),
                            fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("미션 설명 (아랍어)").optional(),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("미션 상태"),
                            fieldWithPath("value.visibility").type(JsonFieldType.STRING).description("공개 여부"),
                            fieldWithPath("value.type").type(JsonFieldType.STRING).description("미션 타입"),
                            fieldWithPath("value.creator_id").type(JsonFieldType.STRING).description("생성자 ID"),
                            fieldWithPath("value.guild_id").type(JsonFieldType.STRING).description("길드 ID").optional(),
                            fieldWithPath("value.guild_name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("value.category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.category_name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                            fieldWithPath("value.max_participants").type(JsonFieldType.NUMBER).description("최대 참여 인원").optional(),
                            fieldWithPath("value.current_participants").type(JsonFieldType.NUMBER).description("현재 참여 인원").optional(),
                            fieldWithPath("value.start_at").type(JsonFieldType.STRING).description("시작일시").optional(),
                            fieldWithPath("value.end_at").type(JsonFieldType.STRING).description("종료일시").optional(),
                            fieldWithPath("value.mission_interval").type(JsonFieldType.STRING).description("수행 인터벌").optional(),
                            fieldWithPath("value.duration_days").type(JsonFieldType.NUMBER).description("미션 기간 (일)").optional(),
                            fieldWithPath("value.duration_minutes").type(JsonFieldType.NUMBER).description("미션 수행 시간 (분)").optional(),
                            fieldWithPath("value.exp_per_completion").type(JsonFieldType.NUMBER).description("수행 당 경험치").optional(),
                            fieldWithPath("value.bonus_exp_on_full_completion").type(JsonFieldType.NUMBER).description("전체 완료 시 보너스 경험치").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value.modified_at").type(JsonFieldType.STRING).description("수정일시").optional(),
                            fieldWithPath("value.source").type(JsonFieldType.STRING).description("미션 출처").optional(),
                            fieldWithPath("value.participation_type").type(JsonFieldType.STRING).description("참여 유형").optional(),
                            fieldWithPath("value.is_customizable").type(JsonFieldType.BOOLEAN).description("커스터마이징 가능 여부").optional(),
                            fieldWithPath("value.is_pinned").type(JsonFieldType.BOOLEAN).description("고정 미션 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/v1/missions/{missionId}/start : 미션 진행 시작")
    void startMissionTest() throws Exception {
        // given
        Long missionId = 1L;
        MissionResponse response = MockUtil.readJsonFileToClass(
            "fixture/missionservice/missionResponseInProgress.json", MissionResponse.class);

        when(missionService.startMission(anyLong(), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.patch("/api/v1/missions/{missionId}/start", missionId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("08. 미션 진행 시작",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission")
                        .description("미션 상태를 IN_PROGRESS로 변경하여 미션 진행 시작 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("미션 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.title_en").type(JsonFieldType.STRING).description("미션 제목 (영어)").optional(),
                            fieldWithPath("value.title_ar").type(JsonFieldType.STRING).description("미션 제목 (아랍어)").optional(),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("미션 설명").optional(),
                            fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("미션 설명 (영어)").optional(),
                            fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("미션 설명 (아랍어)").optional(),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("미션 상태"),
                            fieldWithPath("value.visibility").type(JsonFieldType.STRING).description("공개 여부"),
                            fieldWithPath("value.type").type(JsonFieldType.STRING).description("미션 타입"),
                            fieldWithPath("value.creator_id").type(JsonFieldType.STRING).description("생성자 ID"),
                            fieldWithPath("value.guild_id").type(JsonFieldType.STRING).description("길드 ID").optional(),
                            fieldWithPath("value.guild_name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("value.category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.category_name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                            fieldWithPath("value.max_participants").type(JsonFieldType.NUMBER).description("최대 참여 인원").optional(),
                            fieldWithPath("value.current_participants").type(JsonFieldType.NUMBER).description("현재 참여 인원").optional(),
                            fieldWithPath("value.start_at").type(JsonFieldType.STRING).description("시작일시").optional(),
                            fieldWithPath("value.end_at").type(JsonFieldType.STRING).description("종료일시").optional(),
                            fieldWithPath("value.mission_interval").type(JsonFieldType.STRING).description("수행 인터벌").optional(),
                            fieldWithPath("value.duration_days").type(JsonFieldType.NUMBER).description("미션 기간 (일)").optional(),
                            fieldWithPath("value.duration_minutes").type(JsonFieldType.NUMBER).description("미션 수행 시간 (분)").optional(),
                            fieldWithPath("value.exp_per_completion").type(JsonFieldType.NUMBER).description("수행 당 경험치").optional(),
                            fieldWithPath("value.bonus_exp_on_full_completion").type(JsonFieldType.NUMBER).description("전체 완료 시 보너스 경험치").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value.modified_at").type(JsonFieldType.STRING).description("수정일시").optional(),
                            fieldWithPath("value.source").type(JsonFieldType.STRING).description("미션 출처").optional(),
                            fieldWithPath("value.participation_type").type(JsonFieldType.STRING).description("참여 유형").optional(),
                            fieldWithPath("value.is_customizable").type(JsonFieldType.BOOLEAN).description("커스터마이징 가능 여부").optional(),
                            fieldWithPath("value.is_pinned").type(JsonFieldType.BOOLEAN).description("고정 미션 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/v1/missions/{missionId}/complete : 미션 완료")
    void completeMissionTest() throws Exception {
        // given
        Long missionId = 1L;
        MissionResponse response = MockUtil.readJsonFileToClass(
            "fixture/missionservice/missionResponseCompleted.json", MissionResponse.class);

        when(missionService.completeMission(anyLong(), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.patch("/api/v1/missions/{missionId}/complete", missionId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("09. 미션 완료",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission")
                        .description("미션 상태를 COMPLETED로 변경 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("미션 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.title_en").type(JsonFieldType.STRING).description("미션 제목 (영어)").optional(),
                            fieldWithPath("value.title_ar").type(JsonFieldType.STRING).description("미션 제목 (아랍어)").optional(),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("미션 설명").optional(),
                            fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("미션 설명 (영어)").optional(),
                            fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("미션 설명 (아랍어)").optional(),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("미션 상태"),
                            fieldWithPath("value.visibility").type(JsonFieldType.STRING).description("공개 여부"),
                            fieldWithPath("value.type").type(JsonFieldType.STRING).description("미션 타입"),
                            fieldWithPath("value.creator_id").type(JsonFieldType.STRING).description("생성자 ID"),
                            fieldWithPath("value.guild_id").type(JsonFieldType.STRING).description("길드 ID").optional(),
                            fieldWithPath("value.guild_name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("value.category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.category_name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                            fieldWithPath("value.max_participants").type(JsonFieldType.NUMBER).description("최대 참여 인원").optional(),
                            fieldWithPath("value.current_participants").type(JsonFieldType.NUMBER).description("현재 참여 인원").optional(),
                            fieldWithPath("value.start_at").type(JsonFieldType.STRING).description("시작일시").optional(),
                            fieldWithPath("value.end_at").type(JsonFieldType.STRING).description("종료일시").optional(),
                            fieldWithPath("value.mission_interval").type(JsonFieldType.STRING).description("수행 인터벌").optional(),
                            fieldWithPath("value.duration_days").type(JsonFieldType.NUMBER).description("미션 기간 (일)").optional(),
                            fieldWithPath("value.duration_minutes").type(JsonFieldType.NUMBER).description("미션 수행 시간 (분)").optional(),
                            fieldWithPath("value.exp_per_completion").type(JsonFieldType.NUMBER).description("수행 당 경험치").optional(),
                            fieldWithPath("value.bonus_exp_on_full_completion").type(JsonFieldType.NUMBER).description("전체 완료 시 보너스 경험치").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value.modified_at").type(JsonFieldType.STRING).description("수정일시").optional(),
                            fieldWithPath("value.source").type(JsonFieldType.STRING).description("미션 출처").optional(),
                            fieldWithPath("value.participation_type").type(JsonFieldType.STRING).description("참여 유형").optional(),
                            fieldWithPath("value.is_customizable").type(JsonFieldType.BOOLEAN).description("커스터마이징 가능 여부").optional(),
                            fieldWithPath("value.is_pinned").type(JsonFieldType.BOOLEAN).description("고정 미션 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/v1/missions/{missionId}/cancel : 미션 취소")
    void cancelMissionTest() throws Exception {
        // given
        Long missionId = 1L;
        MissionResponse response = MockUtil.readJsonFileToClass(
            "fixture/missionservice/missionResponseCancelled.json", MissionResponse.class);

        when(missionService.cancelMission(anyLong(), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.patch("/api/v1/missions/{missionId}/cancel", missionId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("10. 미션 취소",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission")
                        .description("미션 상태를 CANCELLED로 변경 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("미션 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.title_en").type(JsonFieldType.STRING).description("미션 제목 (영어)").optional(),
                            fieldWithPath("value.title_ar").type(JsonFieldType.STRING).description("미션 제목 (아랍어)").optional(),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("미션 설명").optional(),
                            fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("미션 설명 (영어)").optional(),
                            fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("미션 설명 (아랍어)").optional(),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("미션 상태"),
                            fieldWithPath("value.visibility").type(JsonFieldType.STRING).description("공개 여부"),
                            fieldWithPath("value.type").type(JsonFieldType.STRING).description("미션 타입"),
                            fieldWithPath("value.creator_id").type(JsonFieldType.STRING).description("생성자 ID"),
                            fieldWithPath("value.guild_id").type(JsonFieldType.STRING).description("길드 ID").optional(),
                            fieldWithPath("value.guild_name").type(JsonFieldType.STRING).description("길드 이름").optional(),
                            fieldWithPath("value.category_id").type(JsonFieldType.NUMBER).description("카테고리 ID").optional(),
                            fieldWithPath("value.category_name").type(JsonFieldType.STRING).description("카테고리 이름").optional(),
                            fieldWithPath("value.max_participants").type(JsonFieldType.NUMBER).description("최대 참여 인원").optional(),
                            fieldWithPath("value.current_participants").type(JsonFieldType.NUMBER).description("현재 참여 인원").optional(),
                            fieldWithPath("value.start_at").type(JsonFieldType.STRING).description("시작일시").optional(),
                            fieldWithPath("value.end_at").type(JsonFieldType.STRING).description("종료일시").optional(),
                            fieldWithPath("value.mission_interval").type(JsonFieldType.STRING).description("수행 인터벌").optional(),
                            fieldWithPath("value.duration_days").type(JsonFieldType.NUMBER).description("미션 기간 (일)").optional(),
                            fieldWithPath("value.duration_minutes").type(JsonFieldType.NUMBER).description("미션 수행 시간 (분)").optional(),
                            fieldWithPath("value.exp_per_completion").type(JsonFieldType.NUMBER).description("수행 당 경험치").optional(),
                            fieldWithPath("value.bonus_exp_on_full_completion").type(JsonFieldType.NUMBER).description("전체 완료 시 보너스 경험치").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value.modified_at").type(JsonFieldType.STRING).description("수정일시").optional(),
                            fieldWithPath("value.source").type(JsonFieldType.STRING).description("미션 출처").optional(),
                            fieldWithPath("value.participation_type").type(JsonFieldType.STRING).description("참여 유형").optional(),
                            fieldWithPath("value.is_customizable").type(JsonFieldType.BOOLEAN).description("커스터마이징 가능 여부").optional(),
                            fieldWithPath("value.is_pinned").type(JsonFieldType.BOOLEAN).description("고정 미션 여부").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/missions/{missionId} : 미션 삭제")
    void deleteMissionTest() throws Exception {
        // given
        Long missionId = 1L;

        doNothing().when(missionService).deleteMission(anyLong(), anyString());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/missions/{missionId}", missionId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("11. 미션 삭제",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission")
                        .description("미션 삭제 (IN_PROGRESS 상태가 아닐 때만 가능) (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
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
}
