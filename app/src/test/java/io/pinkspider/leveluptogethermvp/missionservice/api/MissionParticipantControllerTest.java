package io.pinkspider.leveluptogethermvp.missionservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionParticipantService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionParticipantResponse;
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

@WebMvcTest(controllers = MissionParticipantController.class,
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
class MissionParticipantControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    private MissionParticipantService participantService;

    private static final String MOCK_USER_ID = "test-user-456";

    @Test
    @DisplayName("POST /api/v1/missions/{missionId}/join : 미션 참여 신청")
    void joinMissionTest() throws Exception {
        // given
        Long missionId = 1L;
        MissionParticipantResponse response = MockUtil.readJsonFileToClass(
            "fixture/missionservice/missionParticipantResponse.json", MissionParticipantResponse.class);

        when(participantService.joinMission(anyLong(), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/missions/{missionId}/join", missionId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("참여자-01. 미션 참여 신청",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Participant")
                        .description("미션에 참여 신청 (공개 미션: 자동 승인, 비공개 미션: 대기 상태) (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("참여 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("참여 ID"),
                            fieldWithPath("value.mission_id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.mission_title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("참여 상태"),
                            fieldWithPath("value.progress").type(JsonFieldType.NUMBER).description("진행률"),
                            fieldWithPath("value.note").type(JsonFieldType.STRING).description("메모").optional(),
                            fieldWithPath("value.joined_at").type(JsonFieldType.STRING).description("참여일시").optional(),
                            fieldWithPath("value.completed_at").type(JsonFieldType.STRING).description("완료일시").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/v1/missions/{missionId}/participants/{participantId}/accept : 참여자 승인")
    void acceptParticipantTest() throws Exception {
        // given
        Long missionId = 1L;
        Long participantId = 1L;
        MissionParticipantResponse response = MockUtil.readJsonFileToClass(
            "fixture/missionservice/missionParticipantResponse.json", MissionParticipantResponse.class);

        when(participantService.acceptParticipant(anyLong(), anyLong(), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.patch("/api/v1/missions/{missionId}/participants/{participantId}/accept", missionId, participantId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("참여자-02. 참여자 승인",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Participant")
                        .description("미션 생성자가 참여 신청자를 승인 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID"),
                            parameterWithName("participantId").type(SimpleType.NUMBER).description("참여자 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("참여 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("참여 ID"),
                            fieldWithPath("value.mission_id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.mission_title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("참여 상태"),
                            fieldWithPath("value.progress").type(JsonFieldType.NUMBER).description("진행률"),
                            fieldWithPath("value.note").type(JsonFieldType.STRING).description("메모").optional(),
                            fieldWithPath("value.joined_at").type(JsonFieldType.STRING).description("참여일시").optional(),
                            fieldWithPath("value.completed_at").type(JsonFieldType.STRING).description("완료일시").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/v1/missions/{missionId}/start-progress : 미션 진행 시작")
    void startProgressTest() throws Exception {
        // given
        Long missionId = 1L;
        MissionParticipantResponse response = MockUtil.readJsonFileToClass(
            "fixture/missionservice/missionParticipantResponse.json", MissionParticipantResponse.class);
        response.setStatus(io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.IN_PROGRESS);

        when(participantService.startProgress(anyLong(), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.patch("/api/v1/missions/{missionId}/start-progress", missionId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("참여자-03. 미션 진행 시작",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Participant")
                        .description("참여자가 미션 진행을 시작 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("참여 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("참여 ID"),
                            fieldWithPath("value.mission_id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.mission_title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("참여 상태"),
                            fieldWithPath("value.progress").type(JsonFieldType.NUMBER).description("진행률"),
                            fieldWithPath("value.note").type(JsonFieldType.STRING).description("메모").optional(),
                            fieldWithPath("value.joined_at").type(JsonFieldType.STRING).description("참여일시").optional(),
                            fieldWithPath("value.completed_at").type(JsonFieldType.STRING).description("완료일시").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/v1/missions/{missionId}/progress : 진행률 업데이트")
    void updateProgressTest() throws Exception {
        // given
        Long missionId = 1L;
        int progress = 50;
        MissionParticipantResponse response = MockUtil.readJsonFileToClass(
            "fixture/missionservice/missionParticipantResponse.json", MissionParticipantResponse.class);
        response.setProgress(progress);

        when(participantService.updateProgress(anyLong(), anyString(), anyInt()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.patch("/api/v1/missions/{missionId}/progress", missionId)
                .with(user(MOCK_USER_ID))
                .param("progress", String.valueOf(progress))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("참여자-04. 진행률 업데이트",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Participant")
                        .description("미션 진행률 업데이트 (0-100). progress 파라미터: 진행률 (0-100) (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("참여 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("참여 ID"),
                            fieldWithPath("value.mission_id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.mission_title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("참여 상태"),
                            fieldWithPath("value.progress").type(JsonFieldType.NUMBER).description("진행률"),
                            fieldWithPath("value.note").type(JsonFieldType.STRING).description("메모").optional(),
                            fieldWithPath("value.joined_at").type(JsonFieldType.STRING).description("참여일시").optional(),
                            fieldWithPath("value.completed_at").type(JsonFieldType.STRING).description("완료일시").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/v1/missions/{missionId}/complete-participation : 참여 완료")
    void completeParticipationTest() throws Exception {
        // given
        Long missionId = 1L;
        MissionParticipantResponse response = MockUtil.readJsonFileToClass(
            "fixture/missionservice/missionParticipantResponse.json", MissionParticipantResponse.class);
        response.setStatus(io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.COMPLETED);
        response.setProgress(100);

        when(participantService.completeParticipant(anyLong(), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.patch("/api/v1/missions/{missionId}/complete-participation", missionId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("참여자-05. 참여 완료",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Participant")
                        .description("미션 참여를 완료 상태로 변경 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("참여 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("참여 ID"),
                            fieldWithPath("value.mission_id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.mission_title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("참여 상태"),
                            fieldWithPath("value.progress").type(JsonFieldType.NUMBER).description("진행률"),
                            fieldWithPath("value.note").type(JsonFieldType.STRING).description("메모").optional(),
                            fieldWithPath("value.joined_at").type(JsonFieldType.STRING).description("참여일시").optional(),
                            fieldWithPath("value.completed_at").type(JsonFieldType.STRING).description("완료일시").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/v1/missions/{missionId}/withdraw : 미션 참여 철회")
    void withdrawFromMissionTest() throws Exception {
        // given
        Long missionId = 1L;
        MissionParticipantResponse response = MockUtil.readJsonFileToClass(
            "fixture/missionservice/missionParticipantResponse.json", MissionParticipantResponse.class);
        response.setStatus(io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus.WITHDRAWN);

        when(participantService.withdrawFromMission(anyLong(), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.patch("/api/v1/missions/{missionId}/withdraw", missionId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("참여자-06. 미션 참여 철회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Participant")
                        .description("미션 참여를 철회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("참여 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("참여 ID"),
                            fieldWithPath("value.mission_id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.mission_title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("참여 상태"),
                            fieldWithPath("value.progress").type(JsonFieldType.NUMBER).description("진행률"),
                            fieldWithPath("value.note").type(JsonFieldType.STRING).description("메모").optional(),
                            fieldWithPath("value.joined_at").type(JsonFieldType.STRING).description("참여일시").optional(),
                            fieldWithPath("value.completed_at").type(JsonFieldType.STRING).description("완료일시").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/missions/{missionId}/participants : 미션 참여자 목록 조회")
    void getMissionParticipantsTest() throws Exception {
        // given
        Long missionId = 1L;
        List<MissionParticipantResponse> responses = MockUtil.readJsonFileToClassList(
            "fixture/missionservice/missionParticipantResponseList.json",
            new TypeReference<List<MissionParticipantResponse>>() {});

        when(participantService.getMissionParticipants(anyLong()))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/missions/{missionId}/participants", missionId)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("참여자-07. 미션 참여자 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Participant")
                        .description("특정 미션의 모든 참여자 목록 조회")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("참여자 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("참여 ID"),
                            fieldWithPath("value[].mission_id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value[].mission_title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value[].status").type(JsonFieldType.STRING).description("참여 상태"),
                            fieldWithPath("value[].progress").type(JsonFieldType.NUMBER).description("진행률"),
                            fieldWithPath("value[].note").type(JsonFieldType.STRING).description("메모").optional(),
                            fieldWithPath("value[].joined_at").type(JsonFieldType.STRING).description("참여일시").optional(),
                            fieldWithPath("value[].completed_at").type(JsonFieldType.STRING).description("완료일시").optional(),
                            fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("생성일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/missions/{missionId}/my-participation : 내 참여 정보 조회")
    void getMyParticipationTest() throws Exception {
        // given
        Long missionId = 1L;
        MissionParticipantResponse response = MockUtil.readJsonFileToClass(
            "fixture/missionservice/missionParticipantResponse.json", MissionParticipantResponse.class);

        when(participantService.getMyParticipation(anyLong(), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/missions/{missionId}/my-participation", missionId)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("참여자-08. 내 참여 정보 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Participant")
                        .description("특정 미션에 대한 내 참여 정보 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("참여 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("참여 ID"),
                            fieldWithPath("value.mission_id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.mission_title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("참여 상태"),
                            fieldWithPath("value.progress").type(JsonFieldType.NUMBER).description("진행률"),
                            fieldWithPath("value.note").type(JsonFieldType.STRING).description("메모").optional(),
                            fieldWithPath("value.joined_at").type(JsonFieldType.STRING).description("참여일시").optional(),
                            fieldWithPath("value.completed_at").type(JsonFieldType.STRING).description("완료일시").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/missions/my-participations : 내 참여 목록 조회")
    void getMyParticipationsTest() throws Exception {
        // given
        List<MissionParticipantResponse> responses = MockUtil.readJsonFileToClassList(
            "fixture/missionservice/missionParticipantResponseList.json",
            new TypeReference<List<MissionParticipantResponse>>() {});

        when(participantService.getMyParticipations(anyString()))
            .thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/missions/my-participations")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("참여자-09. 내 참여 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Participant")
                        .description("내가 참여한 모든 미션 목록 조회 (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("참여 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("참여 ID"),
                            fieldWithPath("value[].mission_id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value[].mission_title").type(JsonFieldType.STRING).description("미션 제목"),
                            fieldWithPath("value[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value[].status").type(JsonFieldType.STRING).description("참여 상태"),
                            fieldWithPath("value[].progress").type(JsonFieldType.NUMBER).description("진행률"),
                            fieldWithPath("value[].note").type(JsonFieldType.STRING).description("메모").optional(),
                            fieldWithPath("value[].joined_at").type(JsonFieldType.STRING).description("참여일시").optional(),
                            fieldWithPath("value[].completed_at").type(JsonFieldType.STRING).description("완료일시").optional(),
                            fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("생성일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
