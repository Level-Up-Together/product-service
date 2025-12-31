package io.pinkspider.leveluptogethermvp.supportservice.report.api;

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
import io.pinkspider.global.component.LmObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportCreateRequest;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportResponse;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportTargetType;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportType;
import io.pinkspider.leveluptogethermvp.supportservice.report.application.ReportService;
import java.time.LocalDateTime;
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

@WebMvcTest(controllers = ReportController.class,
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
class ReportControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean
    private ReportService reportService;

    private static final String MOCK_USER_ID = "test-user-123";

    private ReportResponse createMockReportResponse() {
        return ReportResponse.builder()
            .id(1L)
            .targetType(ReportTargetType.USER_PROFILE)
            .targetId("target-user-456")
            .reportType(ReportType.INAPPROPRIATE_IMAGE)
            .reason("부적절한 프로필 이미지")
            .status("PENDING")
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("POST /api/v1/reports : 신고 생성")
    void createReportTest() throws Exception {
        // given
        ReportCreateRequest request = new ReportCreateRequest(
            ReportTargetType.USER_PROFILE,
            "target-user-456",
            "target-user-456",
            ReportType.INAPPROPRIATE_IMAGE,
            "부적절한 프로필 이미지"
        );

        when(reportService.createReport(anyString(), any(ReportCreateRequest.class)))
            .thenReturn(createMockReportResponse());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/reports")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("신고-01. 신고 생성",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Report")
                        .description("새 신고 생성 (JWT 토큰 인증 필요)")
                        .requestFields(
                            fieldWithPath("target_type").type(JsonFieldType.STRING)
                                .description("신고 대상 유형 (USER_PROFILE, FEED, FEED_COMMENT, GUILD)"),
                            fieldWithPath("target_id").type(JsonFieldType.STRING)
                                .description("신고 대상 ID"),
                            fieldWithPath("target_user_id").type(JsonFieldType.STRING)
                                .description("신고 대상 사용자 ID (선택)").optional(),
                            fieldWithPath("report_type").type(JsonFieldType.STRING)
                                .description("신고 유형 (INAPPROPRIATE_IMAGE, SPAM, HARASSMENT, HATE_SPEECH, IMPERSONATION, VIOLENCE, OTHER)"),
                            fieldWithPath("reason").type(JsonFieldType.STRING)
                                .description("신고 사유 (선택)").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("신고 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("신고 ID"),
                            fieldWithPath("value.target_type").type(JsonFieldType.STRING).description("신고 대상 유형"),
                            fieldWithPath("value.target_id").type(JsonFieldType.STRING).description("신고 대상 ID"),
                            fieldWithPath("value.report_type").type(JsonFieldType.STRING).description("신고 유형"),
                            fieldWithPath("value.reason").type(JsonFieldType.STRING).description("신고 사유").optional(),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("신고 상태"),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("신고 일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/reports : 피드 신고 생성")
    void createFeedReportTest() throws Exception {
        // given
        ReportCreateRequest request = new ReportCreateRequest(
            ReportTargetType.FEED,
            "12345",
            "feed-author-789",
            ReportType.SPAM,
            "스팸 게시물"
        );

        ReportResponse response = ReportResponse.builder()
            .id(2L)
            .targetType(ReportTargetType.FEED)
            .targetId("12345")
            .reportType(ReportType.SPAM)
            .reason("스팸 게시물")
            .status("PENDING")
            .createdAt(LocalDateTime.now())
            .build();

        when(reportService.createReport(anyString(), any(ReportCreateRequest.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/reports")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("신고-02. 피드 신고 생성",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Report")
                        .description("피드 신고 생성 (JWT 토큰 인증 필요)")
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/reports : 댓글 신고 생성")
    void createCommentReportTest() throws Exception {
        // given
        ReportCreateRequest request = new ReportCreateRequest(
            ReportTargetType.FEED_COMMENT,
            "comment-67890",
            "comment-author-111",
            ReportType.HARASSMENT,
            "욕설 및 비방"
        );

        ReportResponse response = ReportResponse.builder()
            .id(3L)
            .targetType(ReportTargetType.FEED_COMMENT)
            .targetId("comment-67890")
            .reportType(ReportType.HARASSMENT)
            .reason("욕설 및 비방")
            .status("PENDING")
            .createdAt(LocalDateTime.now())
            .build();

        when(reportService.createReport(anyString(), any(ReportCreateRequest.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/reports")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("신고-03. 댓글 신고 생성",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Report")
                        .description("댓글 신고 생성 (JWT 토큰 인증 필요)")
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/reports : 길드 신고 생성")
    void createGuildReportTest() throws Exception {
        // given
        ReportCreateRequest request = new ReportCreateRequest(
            ReportTargetType.GUILD,
            "15",
            null,
            ReportType.HATE_SPEECH,
            "혐오 발언을 조장하는 길드"
        );

        ReportResponse response = ReportResponse.builder()
            .id(4L)
            .targetType(ReportTargetType.GUILD)
            .targetId("15")
            .reportType(ReportType.HATE_SPEECH)
            .reason("혐오 발언을 조장하는 길드")
            .status("PENDING")
            .createdAt(LocalDateTime.now())
            .build();

        when(reportService.createReport(anyString(), any(ReportCreateRequest.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/reports")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("신고-04. 길드 신고 생성",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Report")
                        .description("길드 신고 생성 (JWT 토큰 인증 필요)")
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
