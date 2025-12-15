package io.pinkspider.leveluptogethermvp.missionservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
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
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionExecutionService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import java.time.LocalDate;
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

@WebMvcTest(controllers = MissionExecutionController.class,
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
class MissionExecutionControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private MissionExecutionService executionService;

    private static final String MOCK_USER_ID = "test-user-123";

    private List<MissionExecutionResponse> createMockExecutions() {
        return List.of(
            MissionExecutionResponse.builder()
                .id(1L)
                .missionId(1L)
                .missionTitle("30일 운동 챌린지")
                .userId(MOCK_USER_ID)
                .executionDate(LocalDate.now())
                .status(ExecutionStatus.COMPLETED)
                .expEarned(50)
                .note("1시간 조깅 완료")
                .completedAt(LocalDateTime.now())
                .build(),
            MissionExecutionResponse.builder()
                .id(2L)
                .missionId(1L)
                .missionTitle("30일 운동 챌린지")
                .userId(MOCK_USER_ID)
                .executionDate(LocalDate.now().minusDays(1))
                .status(ExecutionStatus.COMPLETED)
                .expEarned(50)
                .completedAt(LocalDateTime.now().minusDays(1))
                .build()
        );
    }

    @Test
    @DisplayName("PATCH /api/v1/missions/{missionId}/executions/{executionDate}/complete : 미션 실행 완료 처리")
    void completeExecutionTest() throws Exception {
        // given
        MissionExecutionResponse response = MissionExecutionResponse.builder()
            .id(1L)
            .missionId(1L)
            .missionTitle("30일 운동 챌린지")
            .userId(MOCK_USER_ID)
            .executionDate(LocalDate.now())
            .status(ExecutionStatus.COMPLETED)
            .expEarned(50)
            .note("1시간 조깅 완료")
            .completedAt(LocalDateTime.now())
            .build();

        when(executionService.completeExecution(anyLong(), anyString(), any(LocalDate.class), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.patch("/api/v1/missions/{missionId}/executions/{executionDate}/complete",
                    1L, LocalDate.now().toString())
                .with(user(MOCK_USER_ID))
                .param("note", "1시간 조깅 완료")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("미션실행-01. 미션 실행 완료 처리",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Execution")
                        .description("특정 날짜의 미션 실행 완료 처리 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID"),
                            parameterWithName("executionDate").type(SimpleType.STRING).description("실행 날짜 (yyyy-MM-dd)")
                        )
                        .queryParameters(
                            parameterWithName("note").type(SimpleType.STRING).description("메모").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("미션 실행 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("실행 ID"),
                            fieldWithPath("value.mission_id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value.mission_title").type(JsonFieldType.STRING).description("미션 제목").optional(),
                            fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.execution_date").type(JsonFieldType.STRING).description("실행 날짜"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("상태 (PENDING, COMPLETED, SKIPPED)"),
                            fieldWithPath("value.exp_earned").type(JsonFieldType.NUMBER).description("획득 경험치").optional(),
                            fieldWithPath("value.participant_id").type(JsonFieldType.NUMBER).description("참여자 ID").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value.note").type(JsonFieldType.STRING).description("메모").optional(),
                            fieldWithPath("value.completed_at").type(JsonFieldType.STRING).description("완료 일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/missions/{missionId}/executions : 미션 실행 기록 조회")
    void getExecutionsTest() throws Exception {
        // given
        when(executionService.getExecutionsForMission(anyLong(), anyString()))
            .thenReturn(createMockExecutions());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/missions/{missionId}/executions", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("미션실행-02. 미션 실행 기록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Execution")
                        .description("미션의 모든 실행 기록 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("실행 기록 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("실행 ID"),
                            fieldWithPath("value[].mission_id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value[].mission_title").type(JsonFieldType.STRING).description("미션 제목").optional(),
                            fieldWithPath("value[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value[].execution_date").type(JsonFieldType.STRING).description("실행 날짜"),
                            fieldWithPath("value[].status").type(JsonFieldType.STRING).description("상태"),
                            fieldWithPath("value[].exp_earned").type(JsonFieldType.NUMBER).description("획득 경험치").optional(),
                            fieldWithPath("value[].participant_id").type(JsonFieldType.NUMBER).description("참여자 ID").optional(),
                            fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value[].note").type(JsonFieldType.STRING).description("메모").optional(),
                            fieldWithPath("value[].completed_at").type(JsonFieldType.STRING).description("완료 일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/missions/{missionId}/executions/range : 특정 기간 실행 기록 조회")
    void getExecutionsByDateRangeTest() throws Exception {
        // given
        when(executionService.getExecutionsByDateRange(anyLong(), anyString(),
            any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(createMockExecutions());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/missions/{missionId}/executions/range", 1L)
                .with(user(MOCK_USER_ID))
                .param("startDate", LocalDate.now().minusDays(7).toString())
                .param("endDate", LocalDate.now().toString())
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("미션실행-03. 특정 기간 실행 기록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Execution")
                        .description("특정 기간의 미션 실행 기록 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
                        )
                        .queryParameters(
                            parameterWithName("startDate").type(SimpleType.STRING).description("시작 날짜 (yyyy-MM-dd)"),
                            parameterWithName("endDate").type(SimpleType.STRING).description("종료 날짜 (yyyy-MM-dd)")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/missions/executions/today : 오늘 실행할 미션 목록")
    void getTodayExecutionsTest() throws Exception {
        // given
        List<MissionExecutionResponse> responses = List.of(
            MissionExecutionResponse.builder()
                .id(1L)
                .missionId(1L)
                .missionTitle("30일 운동 챌린지")
                .userId(MOCK_USER_ID)
                .executionDate(LocalDate.now())
                .status(ExecutionStatus.PENDING)
                .build(),
            MissionExecutionResponse.builder()
                .id(2L)
                .missionId(2L)
                .missionTitle("매일 독서하기")
                .userId(MOCK_USER_ID)
                .executionDate(LocalDate.now())
                .status(ExecutionStatus.COMPLETED)
                .expEarned(30)
                .completedAt(LocalDateTime.now())
                .build()
        );

        when(executionService.getTodayExecutions(anyString())).thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/missions/executions/today")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("미션실행-04. 오늘 실행할 미션 목록",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Execution")
                        .description("오늘 실행해야 할 미션 목록 조회 (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("오늘의 실행 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("실행 ID"),
                            fieldWithPath("value[].mission_id").type(JsonFieldType.NUMBER).description("미션 ID"),
                            fieldWithPath("value[].mission_title").type(JsonFieldType.STRING).description("미션 제목").optional(),
                            fieldWithPath("value[].user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value[].execution_date").type(JsonFieldType.STRING).description("실행 날짜"),
                            fieldWithPath("value[].status").type(JsonFieldType.STRING).description("상태 (PENDING, COMPLETED)"),
                            fieldWithPath("value[].exp_earned").type(JsonFieldType.NUMBER).description("획득 경험치").optional(),
                            fieldWithPath("value[].participant_id").type(JsonFieldType.NUMBER).description("참여자 ID").optional(),
                            fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value[].note").type(JsonFieldType.STRING).description("메모").optional(),
                            fieldWithPath("value[].completed_at").type(JsonFieldType.STRING).description("완료 일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/missions/{missionId}/executions/completion-rate : 미션 완료율 조회")
    void getCompletionRateTest() throws Exception {
        // given
        when(executionService.getCompletionRate(anyLong(), anyString())).thenReturn(85.5);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/missions/{missionId}/executions/completion-rate", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("미션실행-05. 미션 완료율 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Mission Execution")
                        .description("미션 완료율 조회 (완료된 실행 / 전체 실행) (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("missionId").type(SimpleType.NUMBER).description("미션 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.NUMBER).description("완료율 (%)")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
