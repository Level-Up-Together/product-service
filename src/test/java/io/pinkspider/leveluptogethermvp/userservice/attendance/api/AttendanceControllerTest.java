package io.pinkspider.leveluptogethermvp.userservice.attendance.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
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
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.userservice.attendance.application.AttendanceService;
import io.pinkspider.leveluptogethermvp.userservice.attendance.domain.dto.AttendanceCheckInResponse;
import io.pinkspider.leveluptogethermvp.userservice.attendance.domain.dto.AttendanceResponse;
import io.pinkspider.leveluptogethermvp.userservice.attendance.domain.dto.MonthlyAttendanceResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.application.UserService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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

@WebMvcTest(controllers = AttendanceController.class,
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
class AttendanceControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private AttendanceService attendanceService;

    @MockitoBean
    private UserService userService;

    private static final String MOCK_USER_ID = "test-user-123";

    @Test
    @DisplayName("POST /api/v1/attendance/check-in : 출석 체크")
    void checkInTest() throws Exception {
        // given
        when(userService.isNewUserToday(anyString())).thenReturn(false);

        AttendanceResponse attendance = AttendanceResponse.builder()
            .id(1L)
            .userId(MOCK_USER_ID)
            .attendanceDate(LocalDate.now())
            .consecutiveDays(5)
            .rewardExp(10)
            .bonusRewardExp(5)
            .totalRewardExp(15)
            .createdAt(LocalDateTime.now())
            .build();

        AttendanceCheckInResponse response = AttendanceCheckInResponse.builder()
            .attendance(attendance)
            .consecutiveDays(5)
            .baseExp(10)
            .bonusExp(5)
            .totalExp(15)
            .bonusReasons(List.of("5일 연속 출석 보너스"))
            .isAlreadyCheckedIn(false)
            .message("출석 체크 완료! 5일 연속 출석입니다.")
            .build();

        when(attendanceService.checkIn(anyString())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/attendance/check-in")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("출석-01. 출석 체크",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Attendance")
                        .description("일일 출석 체크 (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("출석 체크 결과"),
                            fieldWithPath("value.attendance").type(JsonFieldType.OBJECT).description("출석 정보"),
                            fieldWithPath("value.attendance.id").type(JsonFieldType.NUMBER).description("출석 ID"),
                            fieldWithPath("value.attendance.user_id").type(JsonFieldType.STRING).description("사용자 ID"),
                            fieldWithPath("value.attendance.attendance_date").type(JsonFieldType.STRING).description("출석 날짜"),
                            fieldWithPath("value.attendance.consecutive_days").type(JsonFieldType.NUMBER).description("연속 출석 일수"),
                            fieldWithPath("value.attendance.reward_exp").type(JsonFieldType.NUMBER).description("기본 보상 경험치"),
                            fieldWithPath("value.attendance.bonus_reward_exp").type(JsonFieldType.NUMBER).description("보너스 보상 경험치"),
                            fieldWithPath("value.attendance.total_reward_exp").type(JsonFieldType.NUMBER).description("총 보상 경험치"),
                            fieldWithPath("value.attendance.created_at").type(JsonFieldType.STRING).description("출석 시간"),
                            fieldWithPath("value.consecutive_days").type(JsonFieldType.NUMBER).description("연속 출석 일수"),
                            fieldWithPath("value.base_exp").type(JsonFieldType.NUMBER).description("기본 경험치"),
                            fieldWithPath("value.bonus_exp").type(JsonFieldType.NUMBER).description("보너스 경험치"),
                            fieldWithPath("value.total_exp").type(JsonFieldType.NUMBER).description("총 획득 경험치"),
                            fieldWithPath("value.bonus_reasons").type(JsonFieldType.ARRAY).description("보너스 사유 목록").optional(),
                            fieldWithPath("value.already_checked_in").type(JsonFieldType.BOOLEAN).description("이미 출석 여부"),
                            fieldWithPath("value.message").type(JsonFieldType.STRING).description("결과 메시지")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/attendance/today : 오늘 출석 여부 확인")
    void hasCheckedInTodayTest() throws Exception {
        // given
        when(attendanceService.hasCheckedInToday(anyString())).thenReturn(true);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/attendance/today")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("출석-02. 오늘 출석 여부 확인",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Attendance")
                        .description("오늘 출석 완료 여부 확인 (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.BOOLEAN).description("출석 여부 (true: 출석 완료)")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/attendance/monthly : 월간 출석 현황")
    void getMonthlyAttendanceTest() throws Exception {
        // given
        MonthlyAttendanceResponse response = MonthlyAttendanceResponse.builder()
            .yearMonth("2025-01")
            .totalDays(31)
            .attendedDays(15)
            .currentStreak(5)
            .maxStreak(10)
            .attendedDayList(Set.of(1, 2, 3, 5, 6, 7, 10, 11, 12, 13, 14, 20, 21, 22, 23))
            .totalExpEarned(200)
            .build();

        when(attendanceService.getMonthlyAttendance(anyString(), anyString())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/attendance/monthly")
                .with(user(MOCK_USER_ID))
                .param("yearMonth", "2025-01")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("출석-03. 월간 출석 현황",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Attendance")
                        .description("월간 출석 현황 조회 (JWT 토큰 인증 필요)")
                        .queryParameters(
                            parameterWithName("yearMonth").type(SimpleType.STRING).description("조회할 년월 (YYYY-MM 형식, 미입력시 현재 월)").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("월간 출석 정보"),
                            fieldWithPath("value.year_month").type(JsonFieldType.STRING).description("년월"),
                            fieldWithPath("value.total_days").type(JsonFieldType.NUMBER).description("해당 월 총 일수"),
                            fieldWithPath("value.attended_days").type(JsonFieldType.NUMBER).description("출석한 일수"),
                            fieldWithPath("value.current_streak").type(JsonFieldType.NUMBER).description("현재 연속 출석 일수"),
                            fieldWithPath("value.max_streak").type(JsonFieldType.NUMBER).description("최대 연속 출석 일수"),
                            fieldWithPath("value.attended_day_list").type(JsonFieldType.ARRAY).description("출석한 날짜 목록"),
                            fieldWithPath("value.total_exp_earned").type(JsonFieldType.NUMBER).description("총 획득 경험치"),
                            fieldWithPath("value.records").type(JsonFieldType.ARRAY).description("상세 출석 기록").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/attendance/streak : 연속 출석 일수 조회")
    void getCurrentStreakTest() throws Exception {
        // given
        when(attendanceService.getCurrentStreak(anyString())).thenReturn(7);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/attendance/streak")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("출석-04. 연속 출석 일수 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Attendance")
                        .description("현재 연속 출석 일수 조회 (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.NUMBER).description("연속 출석 일수")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/attendance/init : 출석 보상 설정 초기화 (관리자용)")
    void initializeRewardConfigsTest() throws Exception {
        // given
        doNothing().when(attendanceService).initializeDefaultRewardConfigs();

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/attendance/init")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("출석-05. 출석 보상 설정 초기화",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Attendance")
                        .description("출석 보상 설정 초기화 (관리자용)")
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
