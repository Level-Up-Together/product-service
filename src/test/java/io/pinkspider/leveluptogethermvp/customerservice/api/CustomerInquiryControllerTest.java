package io.pinkspider.leveluptogethermvp.customerservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.customerservice.application.CustomerInquiryService;
import io.pinkspider.leveluptogethermvp.customerservice.domain.dto.CustomerInquiryListResponse;
import io.pinkspider.leveluptogethermvp.customerservice.domain.dto.CustomerInquiryRequest;
import io.pinkspider.leveluptogethermvp.customerservice.domain.dto.CustomerInquiryResponse;
import io.pinkspider.leveluptogethermvp.customerservice.domain.enums.InquiryStatus;
import io.pinkspider.leveluptogethermvp.customerservice.domain.enums.InquiryType;
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

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerInquiryService customerInquiryService;

    private static final String MOCK_USER_ID = "test-user-123";

    @Test
    @DisplayName("GET /api/v1/inquiries/types : 문의 유형 목록 조회")
    void getInquiryTypesTest() throws Exception {
        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/inquiries/types")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("고객문의-01. 문의 유형 목록 조회",
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
                            fieldWithPath("value[].value").type(JsonFieldType.STRING).description("문의 유형 값"),
                            fieldWithPath("value[].label").type(JsonFieldType.STRING).description("문의 유형 라벨")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/inquiries : 문의 생성")
    void createInquiryTest() throws Exception {
        // given
        CustomerInquiryRequest request = CustomerInquiryRequest.builder()
            .inquiryType(InquiryType.SUGGESTION)
            .title("테스트 문의 제목")
            .content("테스트 문의 내용입니다.")
            .build();

        CustomerInquiryResponse response = CustomerInquiryResponse.builder()
            .id(1L)
            .inquiryType(InquiryType.SUGGESTION)
            .inquiryTypeDescription("건의사항")
            .title("테스트 문의 제목")
            .content("테스트 문의 내용입니다.")
            .status(InquiryStatus.PENDING)
            .statusDescription("대기중")
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .replies(List.of())
            .build();

        when(customerInquiryService.createInquiry(anyString(), any(CustomerInquiryRequest.class)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/inquiries")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("고객문의-02. 문의 생성",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Customer Inquiry")
                        .description("새로운 문의 생성 (JWT 토큰 인증 필요)")
                        .requestFields(
                            fieldWithPath("inquiry_type").type(JsonFieldType.STRING).description("문의 유형 (SUGGESTION, BUG_REPORT, ACCOUNT, PAYMENT, OTHER)"),
                            fieldWithPath("title").type(JsonFieldType.STRING).description("문의 제목"),
                            fieldWithPath("content").type(JsonFieldType.STRING).description("문의 내용")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("생성된 문의 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("문의 ID"),
                            fieldWithPath("value.inquiry_type").type(JsonFieldType.STRING).description("문의 유형"),
                            fieldWithPath("value.inquiry_type_description").type(JsonFieldType.STRING).description("문의 유형 설명"),
                            fieldWithPath("value.title").type(JsonFieldType.STRING).description("문의 제목"),
                            fieldWithPath("value.content").type(JsonFieldType.STRING).description("문의 내용"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("문의 상태"),
                            fieldWithPath("value.status_description").type(JsonFieldType.STRING).description("문의 상태 설명"),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시"),
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
    @DisplayName("GET /api/v1/inquiries : 내 문의 목록 조회")
    void getMyInquiriesTest() throws Exception {
        // given
        CustomerInquiryListResponse listResponse = CustomerInquiryListResponse.builder()
            .id(1L)
            .inquiryType(InquiryType.SUGGESTION)
            .inquiryTypeDescription("건의사항")
            .title("테스트 문의 제목")
            .status(InquiryStatus.PENDING)
            .statusDescription("대기중")
            .hasReply(false)
            .createdAt(LocalDateTime.now())
            .build();

        when(customerInquiryService.getMyInquiries(anyString()))
            .thenReturn(List.of(listResponse));

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/inquiries")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("고객문의-03. 내 문의 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Customer Inquiry")
                        .description("내가 작성한 문의 목록 조회 (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.ARRAY).description("문의 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("문의 ID"),
                            fieldWithPath("value[].inquiry_type").type(JsonFieldType.STRING).description("문의 유형"),
                            fieldWithPath("value[].inquiry_type_description").type(JsonFieldType.STRING).description("문의 유형 설명"),
                            fieldWithPath("value[].title").type(JsonFieldType.STRING).description("문의 제목"),
                            fieldWithPath("value[].status").type(JsonFieldType.STRING).description("문의 상태"),
                            fieldWithPath("value[].status_description").type(JsonFieldType.STRING).description("문의 상태 설명"),
                            fieldWithPath("value[].has_reply").type(JsonFieldType.BOOLEAN).description("답변 존재 여부"),
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
    @DisplayName("GET /api/v1/inquiries/{inquiryId} : 문의 상세 조회")
    void getInquiryDetailTest() throws Exception {
        // given
        CustomerInquiryResponse response = CustomerInquiryResponse.builder()
            .id(1L)
            .inquiryType(InquiryType.SUGGESTION)
            .inquiryTypeDescription("건의사항")
            .title("테스트 문의 제목")
            .content("테스트 문의 내용입니다.")
            .status(InquiryStatus.PENDING)
            .statusDescription("대기중")
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .replies(List.of())
            .build();

        when(customerInquiryService.getInquiryDetail(anyString(), eq(1L)))
            .thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/inquiries/{inquiryId}", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("고객문의-04. 문의 상세 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Customer Inquiry")
                        .description("문의 상세 정보 조회 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("inquiryId").type(SimpleType.NUMBER).description("문의 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("문의 상세 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("문의 ID"),
                            fieldWithPath("value.inquiry_type").type(JsonFieldType.STRING).description("문의 유형"),
                            fieldWithPath("value.inquiry_type_description").type(JsonFieldType.STRING).description("문의 유형 설명"),
                            fieldWithPath("value.title").type(JsonFieldType.STRING).description("문의 제목"),
                            fieldWithPath("value.content").type(JsonFieldType.STRING).description("문의 내용"),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("문의 상태"),
                            fieldWithPath("value.status_description").type(JsonFieldType.STRING).description("문의 상태 설명"),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시"),
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
}
