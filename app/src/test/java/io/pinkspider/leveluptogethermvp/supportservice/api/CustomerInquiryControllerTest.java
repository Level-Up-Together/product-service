package io.pinkspider.leveluptogethermvp.supportservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.global.component.LmObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryCreateRequest;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryResponse;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryStatus;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryType;
import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryTypeOption;
import io.pinkspider.leveluptogethermvp.supportservice.application.CustomerInquiryService;
import io.pinkspider.leveluptogethermvp.supportservice.core.feignclient.AdminInquiryPageApiResponse;
import java.time.LocalDateTime;
import java.util.Arrays;
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

@WebMvcTest(controllers = CustomerInquiryController.class,
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
class CustomerInquiryControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    @MockitoBean
    private CustomerInquiryService customerInquiryService;

    private static final String MOCK_USER_ID = "test-user-123";

    private InquiryResponse createMockInquiryResponse() {
        return InquiryResponse.builder()
            .id(1L)
            .inquiryType(InquiryType.BUG)
            .inquiryTypeName("버그 신고")
            .title("앱이 자꾸 튕겨요")
            .content("미션 완료 버튼을 누르면 앱이 종료됩니다.")
            .status(InquiryStatus.PENDING)
            .statusName("대기중")
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .replies(List.of())
            .build();
    }

    @Test
    @DisplayName("POST /api/v1/support/inquiries : 문의 등록")
    void createInquiryTest() throws Exception {
        // given
        InquiryCreateRequest request = InquiryCreateRequest.builder()
            .title("앱이 자꾸 튕겨요")
            .content("미션 완료 버튼을 누르면 앱이 종료됩니다.")
            .inquiryType(InquiryType.BUG)
            .build();

        when(customerInquiryService.createInquiry(anyString(), any(InquiryCreateRequest.class)))
            .thenReturn(createMockInquiryResponse());

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/support/inquiries")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("문의-01. 문의 등록",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Customer Inquiry")
                        .description("새 문의 등록 (JWT 토큰 인증 필요)")
                        .requestFields(
                            fieldWithPath("title").type(JsonFieldType.STRING)
                                .description("문의 제목 (최대 200자)"),
                            fieldWithPath("content").type(JsonFieldType.STRING)
                                .description("문의 내용"),
                            fieldWithPath("inquiry_type").type(JsonFieldType.STRING)
                                .description("문의 유형 (ACCOUNT, PAYMENT, BUG, SUGGESTION, GUILD, MISSION, OTHER)")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("문의 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("문의 ID"),
                            fieldWithPath("value.inquiry_type").type(JsonFieldType.STRING).description("문의 유형"),
                            fieldWithPath("value.inquiry_type_name").type(JsonFieldType.STRING).description("문의 유형명"),
                            fieldWithPath("value.title").type(JsonFieldType.STRING).description("문의 제목"),
                            fieldWithPath("value.content").type(JsonFieldType.STRING).description("문의 내용"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("문의 상태"),
                            fieldWithPath("value.status_name").type(JsonFieldType.STRING).description("문의 상태명"),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("등록일시"),
                            fieldWithPath("value.modified_at").type(JsonFieldType.STRING).description("수정일시"),
                            fieldWithPath("value.replies").type(JsonFieldType.ARRAY).description("답변 목록")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/support/inquiries : 내 문의 목록 조회")
    void getMyInquiriesTest() throws Exception {
        // given
        AdminInquiryPageApiResponse.PageValue pageValue = new AdminInquiryPageApiResponse.PageValue(
            List.of(createMockInquiryResponse()),
            1,
            1,
            20,
            0,
            true,
            true,
            false
        );

        when(customerInquiryService.getMyInquiries(anyString(), anyInt(), anyInt()))
            .thenReturn(pageValue);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/support/inquiries")
                .with(user(MOCK_USER_ID))
                .param("page", "0")
                .param("size", "20")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("문의-02. 내 문의 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Customer Inquiry")
                        .description("내 문의 목록 조회 (JWT 토큰 인증 필요)")
                        .queryParameters(
                            parameterWithName("page").description("페이지 번호 (기본값: 0)").optional(),
                            parameterWithName("size").description("페이지 크기 (기본값: 20)").optional()
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("페이지 정보"),
                            fieldWithPath("value.content").type(JsonFieldType.ARRAY).description("문의 목록"),
                            fieldWithPath("value.content[].id").type(JsonFieldType.NUMBER).description("문의 ID"),
                            fieldWithPath("value.content[].inquiry_type").type(JsonFieldType.STRING).description("문의 유형"),
                            fieldWithPath("value.content[].inquiry_type_name").type(JsonFieldType.STRING).description("문의 유형명"),
                            fieldWithPath("value.content[].title").type(JsonFieldType.STRING).description("문의 제목"),
                            fieldWithPath("value.content[].content").type(JsonFieldType.STRING).description("문의 내용"),
                            fieldWithPath("value.content[].status").type(JsonFieldType.STRING).description("문의 상태"),
                            fieldWithPath("value.content[].status_name").type(JsonFieldType.STRING).description("문의 상태명"),
                            fieldWithPath("value.content[].created_at").type(JsonFieldType.STRING).description("등록일시"),
                            fieldWithPath("value.content[].modified_at").type(JsonFieldType.STRING).description("수정일시"),
                            fieldWithPath("value.content[].replies").type(JsonFieldType.ARRAY).description("답변 목록"),
                            fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                            fieldWithPath("value.total_elements").type(JsonFieldType.NUMBER).description("전체 요소 수"),
                            fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                            fieldWithPath("value.number").type(JsonFieldType.NUMBER).description("현재 페이지 번호"),
                            fieldWithPath("value.first").type(JsonFieldType.BOOLEAN).description("첫 페이지 여부"),
                            fieldWithPath("value.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부"),
                            fieldWithPath("value.empty").type(JsonFieldType.BOOLEAN).description("빈 페이지 여부")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/support/inquiries/{id} : 문의 상세 조회")
    void getInquiryTest() throws Exception {
        // given
        InquiryResponse response = InquiryResponse.builder()
            .id(1L)
            .inquiryType(InquiryType.BUG)
            .inquiryTypeName("버그 신고")
            .title("앱이 자꾸 튕겨요")
            .content("미션 완료 버튼을 누르면 앱이 종료됩니다.")
            .status(InquiryStatus.RESOLVED)
            .statusName("해결됨")
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .replies(List.of(
                InquiryResponse.ReplyResponse.builder()
                    .id(1L)
                    .content("안녕하세요. 해당 버그를 확인하여 수정했습니다. 최신 버전으로 업데이트 부탁드립니다.")
                    .createdAt(LocalDateTime.now())
                    .build()
            ))
            .build();

        when(customerInquiryService.getInquiry(anyLong(), anyString()))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/support/inquiries/{id}", 1L)
                .with(user(MOCK_USER_ID))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("문의-03. 문의 상세 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Customer Inquiry")
                        .description("문의 상세 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("id").description("문의 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("문의 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("문의 ID"),
                            fieldWithPath("value.inquiry_type").type(JsonFieldType.STRING).description("문의 유형"),
                            fieldWithPath("value.inquiry_type_name").type(JsonFieldType.STRING).description("문의 유형명"),
                            fieldWithPath("value.title").type(JsonFieldType.STRING).description("문의 제목"),
                            fieldWithPath("value.content").type(JsonFieldType.STRING).description("문의 내용"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("문의 상태"),
                            fieldWithPath("value.status_name").type(JsonFieldType.STRING).description("문의 상태명"),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("등록일시"),
                            fieldWithPath("value.modified_at").type(JsonFieldType.STRING).description("수정일시"),
                            fieldWithPath("value.replies").type(JsonFieldType.ARRAY).description("답변 목록"),
                            fieldWithPath("value.replies[].id").type(JsonFieldType.NUMBER).description("답변 ID"),
                            fieldWithPath("value.replies[].content").type(JsonFieldType.STRING).description("답변 내용"),
                            fieldWithPath("value.replies[].created_at").type(JsonFieldType.STRING).description("답변 등록일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/support/inquiries/{id} : 문의 상세 조회 - 존재하지 않는 문의")
    void getInquiryNotFoundTest() throws Exception {
        // given
        when(customerInquiryService.getInquiry(anyLong(), anyString()))
            .thenReturn(null);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/support/inquiries/{id}", 999L)
                .with(user(MOCK_USER_ID))
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/support/inquiries/types : 문의 유형 목록 조회")
    void getInquiryTypesTest() throws Exception {
        // given
        InquiryTypeOption[] typeOptions = Arrays.stream(InquiryType.values())
            .map(InquiryTypeOption::from)
            .toArray(InquiryTypeOption[]::new);

        when(customerInquiryService.getInquiryTypeOptions())
            .thenReturn(typeOptions);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/support/inquiries/types")
                .with(user(MOCK_USER_ID))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("문의-04. 문의 유형 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Customer Inquiry")
                        .description("문의 유형 목록 조회")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.ARRAY).description("문의 유형 목록"),
                            fieldWithPath("value[].value").type(JsonFieldType.STRING).description("문의 유형 코드"),
                            fieldWithPath("value[].label").type(JsonFieldType.STRING).description("문의 유형 라벨")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
