package io.pinkspider.leveluptogethermvp.noticeservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.noticeservice.api.dto.NoticeResponse;
import io.pinkspider.leveluptogethermvp.noticeservice.application.NoticeService;
import io.pinkspider.leveluptogethermvp.noticeservice.domain.enums.NoticeType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
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

@WebMvcTest(controllers = NoticeController.class,
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        FeignAutoConfiguration.class
    }
)
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class NoticeControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    private NoticeService noticeService;

    private NoticeResponse createMockNoticeResponse(Long id, NoticeType type) {
        return NoticeResponse.builder()
            .id(id)
            .title("테스트 공지사항 " + id)
            .content("테스트 공지사항 내용입니다.")
            .noticeType(type)
            .noticeTypeName(type.getDescription())
            .priority(1)
            .startAt(LocalDateTime.now().minusDays(1))
            .endAt(LocalDateTime.now().plusDays(30))
            .isActive(true)
            .isPopup(false)
            .createdBy("admin")
            .modifiedBy("admin")
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("GET /api/v1/notices : 활성 공지사항 목록 조회")
    void getActiveNoticesTest() throws Exception {
        // given
        List<NoticeResponse> notices = List.of(
            createMockNoticeResponse(1L, NoticeType.GENERAL),
            createMockNoticeResponse(2L, NoticeType.EVENT),
            createMockNoticeResponse(3L, NoticeType.MAINTENANCE)
        );

        when(noticeService.getActiveNotices()).thenReturn(notices);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/notices")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("공지사항-01. 활성 공지사항 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Notice")
                        .description("현재 활성화된 공지사항 목록 조회 (홈 화면에서 표시)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("공지사항 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("공지사항 ID"),
                            fieldWithPath("value[].title").type(JsonFieldType.STRING).description("공지사항 제목"),
                            fieldWithPath("value[].content").type(JsonFieldType.STRING).description("공지사항 내용").optional(),
                            fieldWithPath("value[].notice_type").type(JsonFieldType.STRING).description("공지 유형 (GENERAL, EVENT, MAINTENANCE, UPDATE)"),
                            fieldWithPath("value[].notice_type_name").type(JsonFieldType.STRING).description("공지 유형 표시명").optional(),
                            fieldWithPath("value[].priority").type(JsonFieldType.NUMBER).description("우선순위").optional(),
                            fieldWithPath("value[].start_at").type(JsonFieldType.STRING).description("게시 시작일시").optional(),
                            fieldWithPath("value[].end_at").type(JsonFieldType.STRING).description("게시 종료일시").optional(),
                            fieldWithPath("value[].is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부").optional(),
                            fieldWithPath("value[].is_popup").type(JsonFieldType.BOOLEAN).description("팝업 표시 여부").optional(),
                            fieldWithPath("value[].created_by").type(JsonFieldType.STRING).description("작성자").optional(),
                            fieldWithPath("value[].modified_by").type(JsonFieldType.STRING).description("수정자").optional(),
                            fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value[].modified_at").type(JsonFieldType.STRING).description("수정일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/notices : 활성 공지사항이 없을 경우 빈 목록 반환")
    void getActiveNoticesEmptyTest() throws Exception {
        // given
        when(noticeService.getActiveNotices()).thenReturn(List.of());

        // when & then
        mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/notices")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/notices/{id} : 공지사항 상세 조회")
    void getNoticeByIdTest() throws Exception {
        // given
        Long noticeId = 1L;
        NoticeResponse notice = createMockNoticeResponse(noticeId, NoticeType.GENERAL);

        when(noticeService.getNoticeById(anyLong())).thenReturn(notice);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/notices/{id}", noticeId)
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("공지사항-02. 공지사항 상세 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Notice")
                        .description("공지사항 상세 내용 조회")
                        .pathParameters(
                            parameterWithName("id").type(SimpleType.NUMBER).description("공지사항 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("공지사항 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("공지사항 ID"),
                            fieldWithPath("value.title").type(JsonFieldType.STRING).description("공지사항 제목"),
                            fieldWithPath("value.content").type(JsonFieldType.STRING).description("공지사항 내용").optional(),
                            fieldWithPath("value.notice_type").type(JsonFieldType.STRING).description("공지 유형 (GENERAL, EVENT, MAINTENANCE, UPDATE)"),
                            fieldWithPath("value.notice_type_name").type(JsonFieldType.STRING).description("공지 유형 표시명").optional(),
                            fieldWithPath("value.priority").type(JsonFieldType.NUMBER).description("우선순위").optional(),
                            fieldWithPath("value.start_at").type(JsonFieldType.STRING).description("게시 시작일시").optional(),
                            fieldWithPath("value.end_at").type(JsonFieldType.STRING).description("게시 종료일시").optional(),
                            fieldWithPath("value.is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부").optional(),
                            fieldWithPath("value.is_popup").type(JsonFieldType.BOOLEAN).description("팝업 표시 여부").optional(),
                            fieldWithPath("value.created_by").type(JsonFieldType.STRING).description("작성자").optional(),
                            fieldWithPath("value.modified_by").type(JsonFieldType.STRING).description("수정자").optional(),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시").optional(),
                            fieldWithPath("value.modified_at").type(JsonFieldType.STRING).description("수정일시").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/notices/{id} : 존재하지 않는 공지사항 조회 시 404 반환")
    void getNoticeByIdNotFoundTest() throws Exception {
        // given
        Long noticeId = 999L;
        when(noticeService.getNoticeById(anyLong())).thenReturn(null);

        // when & then
        mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/notices/{id}", noticeId)
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isNotFound());
    }
}
