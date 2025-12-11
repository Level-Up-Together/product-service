package io.pinkspider.leveluptogethermvp.userservice.notification.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
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

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import io.pinkspider.global.component.LmObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.userservice.notification.application.NotificationService;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.dto.NotificationPreferenceRequest;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.dto.NotificationPreferenceResponse;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.dto.NotificationResponse;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.dto.NotificationSummaryResponse;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.enums.NotificationType;
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

@WebMvcTest(controllers = NotificationController.class,
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
class NotificationControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean
    private NotificationService notificationService;

    private static final String X_USER_ID = "X-User-Id";
    private static final String MOCK_USER_ID = "test-user-123";

    private NotificationResponse createMockNotification(Long id, NotificationType type, boolean isRead) {
        return NotificationResponse.builder()
            .id(id)
            .notificationType(type)
            .category(type.getCategory())
            .title("알림 제목")
            .message("알림 메시지입니다.")
            .referenceType("MISSION")
            .referenceId(1L)
            .isRead(isRead)
            .readAt(isRead ? LocalDateTime.now() : null)
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("GET /api/v1/notifications : 알림 목록 조회")
    void getNotificationsTest() throws Exception {
        // given
        List<NotificationResponse> notifications = List.of(
            createMockNotification(1L, NotificationType.MISSION_COMPLETED, false),
            createMockNotification(2L, NotificationType.ACHIEVEMENT_UNLOCKED, true)
        );
        Page<NotificationResponse> page = new PageImpl<>(notifications, PageRequest.of(0, 20), 2);

        when(notificationService.getNotifications(anyString(), any(Pageable.class))).thenReturn(page);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/notifications")
                .header(X_USER_ID, MOCK_USER_ID)
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("알림-01. 알림 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Notification")
                        .description("알림 목록 조회 (페이징)")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
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
    @DisplayName("GET /api/v1/notifications/unread : 읽지 않은 알림")
    void getUnreadNotificationsTest() throws Exception {
        // given
        List<NotificationResponse> notifications = List.of(
            createMockNotification(1L, NotificationType.MISSION_COMPLETED, false)
        );

        when(notificationService.getUnreadNotifications(anyString())).thenReturn(notifications);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/notifications/unread")
                .header(X_USER_ID, MOCK_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("알림-02. 읽지 않은 알림",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Notification")
                        .description("읽지 않은 알림 목록 조회")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("알림 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("알림 ID"),
                            fieldWithPath("value[].notification_type").type(JsonFieldType.STRING).description("알림 타입"),
                            fieldWithPath("value[].category").type(JsonFieldType.STRING).description("카테고리"),
                            fieldWithPath("value[].title").type(JsonFieldType.STRING).description("제목"),
                            fieldWithPath("value[].message").type(JsonFieldType.STRING).description("메시지").optional(),
                            fieldWithPath("value[].reference_type").type(JsonFieldType.STRING).description("참조 타입").optional(),
                            fieldWithPath("value[].reference_id").type(JsonFieldType.NUMBER).description("참조 ID").optional(),
                            fieldWithPath("value[].action_url").type(JsonFieldType.STRING).description("액션 URL").optional(),
                            fieldWithPath("value[].icon_url").type(JsonFieldType.STRING).description("아이콘 URL").optional(),
                            fieldWithPath("value[].is_read").type(JsonFieldType.BOOLEAN).description("읽음 여부"),
                            fieldWithPath("value[].read_at").type(JsonFieldType.STRING).description("읽은 시간").optional(),
                            fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("생성일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/summary : 알림 요약")
    void getNotificationSummaryTest() throws Exception {
        // given
        NotificationSummaryResponse response = NotificationSummaryResponse.builder()
            .unreadCount(5)
            .totalCount(20)
            .build();

        when(notificationService.getNotificationSummary(anyString())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/notifications/summary")
                .header(X_USER_ID, MOCK_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("알림-03. 알림 요약",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Notification")
                        .description("알림 요약 정보")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("알림 요약"),
                            fieldWithPath("value.unread_count").type(JsonFieldType.NUMBER).description("읽지 않은 알림 수"),
                            fieldWithPath("value.total_count").type(JsonFieldType.NUMBER).description("전체 알림 수")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/notifications/{notificationId}/read : 알림 읽음 처리")
    void markAsReadTest() throws Exception {
        // given
        NotificationResponse response = createMockNotification(1L, NotificationType.MISSION_COMPLETED, true);

        when(notificationService.markAsRead(anyString(), anyLong())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/notifications/{notificationId}/read", 1L)
                .header(X_USER_ID, MOCK_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("알림-04. 알림 읽음 처리",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Notification")
                        .description("특정 알림을 읽음 처리")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
                        .pathParameters(
                            parameterWithName("notificationId").type(SimpleType.NUMBER).description("알림 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/notifications/read-all : 전체 읽음 처리")
    void markAllAsReadTest() throws Exception {
        // given
        when(notificationService.markAllAsRead(anyString())).thenReturn(5);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/notifications/read-all")
                .header(X_USER_ID, MOCK_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("알림-05. 전체 읽음 처리",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Notification")
                        .description("모든 알림을 읽음 처리")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.NUMBER).description("읽음 처리된 알림 수")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/notifications/{notificationId} : 알림 삭제")
    void deleteNotificationTest() throws Exception {
        // given
        doNothing().when(notificationService).deleteNotification(anyString(), anyLong());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/api/v1/notifications/{notificationId}", 1L)
                .header(X_USER_ID, MOCK_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("알림-06. 알림 삭제",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Notification")
                        .description("알림 삭제")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
                        .pathParameters(
                            parameterWithName("notificationId").type(SimpleType.NUMBER).description("알림 ID")
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
    @DisplayName("GET /api/v1/notifications/preferences : 알림 설정 조회")
    void getPreferencesTest() throws Exception {
        // given
        NotificationPreferenceResponse response = NotificationPreferenceResponse.builder()
            .pushEnabled(true)
            .missionNotifications(true)
            .achievementNotifications(true)
            .guildNotifications(true)
            .questNotifications(true)
            .attendanceNotifications(true)
            .rankingNotifications(false)
            .systemNotifications(true)
            .quietHoursEnabled(true)
            .quietHoursStart("22:00")
            .quietHoursEnd("08:00")
            .build();

        when(notificationService.getPreferences(anyString())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/notifications/preferences")
                .header(X_USER_ID, MOCK_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("알림-07. 알림 설정 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Notification")
                        .description("알림 설정 조회")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("알림 설정"),
                            fieldWithPath("value.push_enabled").type(JsonFieldType.BOOLEAN).description("푸시 알림 활성화"),
                            fieldWithPath("value.mission_notifications").type(JsonFieldType.BOOLEAN).description("미션 알림"),
                            fieldWithPath("value.achievement_notifications").type(JsonFieldType.BOOLEAN).description("업적 알림"),
                            fieldWithPath("value.guild_notifications").type(JsonFieldType.BOOLEAN).description("길드 알림"),
                            fieldWithPath("value.quest_notifications").type(JsonFieldType.BOOLEAN).description("퀘스트 알림"),
                            fieldWithPath("value.attendance_notifications").type(JsonFieldType.BOOLEAN).description("출석 알림"),
                            fieldWithPath("value.ranking_notifications").type(JsonFieldType.BOOLEAN).description("랭킹 알림"),
                            fieldWithPath("value.system_notifications").type(JsonFieldType.BOOLEAN).description("시스템 알림"),
                            fieldWithPath("value.quiet_hours_enabled").type(JsonFieldType.BOOLEAN).description("방해 금지 시간 활성화"),
                            fieldWithPath("value.quiet_hours_start").type(JsonFieldType.STRING).description("방해 금지 시작 시간").optional(),
                            fieldWithPath("value.quiet_hours_end").type(JsonFieldType.STRING).description("방해 금지 종료 시간").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("PUT /api/v1/notifications/preferences : 알림 설정 수정")
    void updatePreferencesTest() throws Exception {
        // given
        NotificationPreferenceRequest request = NotificationPreferenceRequest.builder()
            .pushEnabled(true)
            .missionNotifications(true)
            .achievementNotifications(true)
            .guildNotifications(true)
            .questNotifications(true)
            .attendanceNotifications(true)
            .rankingNotifications(false)
            .systemNotifications(true)
            .quietHoursEnabled(true)
            .quietHoursStart("22:00")
            .quietHoursEnd("08:00")
            .build();

        NotificationPreferenceResponse response = NotificationPreferenceResponse.builder()
            .pushEnabled(true)
            .missionNotifications(true)
            .achievementNotifications(true)
            .guildNotifications(true)
            .questNotifications(true)
            .attendanceNotifications(true)
            .rankingNotifications(false)
            .systemNotifications(true)
            .quietHoursEnabled(true)
            .quietHoursStart("22:00")
            .quietHoursEnd("08:00")
            .build();

        when(notificationService.updatePreferences(anyString(), any(NotificationPreferenceRequest.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.put("/api/v1/notifications/preferences")
                .header(X_USER_ID, MOCK_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("알림-08. 알림 설정 수정",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Notification")
                        .description("알림 설정 수정")
                        .requestHeaders(
                            headerWithName(X_USER_ID).description("사용자 ID")
                        )
                        .requestFields(
                            fieldWithPath("push_enabled").type(JsonFieldType.BOOLEAN).description("푸시 알림 활성화").optional(),
                            fieldWithPath("mission_notifications").type(JsonFieldType.BOOLEAN).description("미션 알림").optional(),
                            fieldWithPath("achievement_notifications").type(JsonFieldType.BOOLEAN).description("업적 알림").optional(),
                            fieldWithPath("guild_notifications").type(JsonFieldType.BOOLEAN).description("길드 알림").optional(),
                            fieldWithPath("quest_notifications").type(JsonFieldType.BOOLEAN).description("퀘스트 알림").optional(),
                            fieldWithPath("attendance_notifications").type(JsonFieldType.BOOLEAN).description("출석 알림").optional(),
                            fieldWithPath("ranking_notifications").type(JsonFieldType.BOOLEAN).description("랭킹 알림").optional(),
                            fieldWithPath("system_notifications").type(JsonFieldType.BOOLEAN).description("시스템 알림").optional(),
                            fieldWithPath("quiet_hours_enabled").type(JsonFieldType.BOOLEAN).description("방해 금지 시간 활성화").optional(),
                            fieldWithPath("quiet_hours_start").type(JsonFieldType.STRING).description("방해 금지 시작 시간").optional(),
                            fieldWithPath("quiet_hours_end").type(JsonFieldType.STRING).description("방해 금지 종료 시간").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
