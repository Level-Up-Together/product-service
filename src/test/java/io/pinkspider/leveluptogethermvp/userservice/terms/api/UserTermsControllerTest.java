package io.pinkspider.leveluptogethermvp.userservice.terms.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.userservice.terms.application.UserTermsService;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.request.AgreementTermsByUserRequestDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.RecentTermsResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.TermAgreementsByUserResponseDto;
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
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = UserTermsController.class,
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
class UserTermsControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    private UserTermsService userTermsService;


    @Test
    @DisplayName("GET /terms/list : 최신버전 약관 전체 읽기")
    void getRecentAllTermsTest() throws Exception {
        // given
        List<RecentTermsResponseDto> mockRecentTermsResponseDtoList = MockUtil.convertJsonToProjectionList(
            "fixture/userservice/terms/mockRecentTermsResponseDtoList.json",
            new TypeReference<List<RecentTermsResponseDto>>() {
            });

        when(userTermsService.getRecentAllTerms())
            .thenReturn(mockRecentTermsResponseDtoList);

        // then
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/terms/list")
                .contentType("application/json;charset=UTF-8")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("01. 최신버전 약관 전체 읽기",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .description("01. 최신버전 약관 전체 읽기")
                        .requestHeaders(
                            headerWithName(HttpHeaders.CONTENT_TYPE).description("application/json; charset=UTF-8")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("code"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("message"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("value"),
                            fieldWithPath("value[].term_id").type(JsonFieldType.STRING).description("term_id"),
                            fieldWithPath("value[].term_title").type(JsonFieldType.STRING).description("term_title"),
                            fieldWithPath("value[].code").type(JsonFieldType.STRING).description("code"),
                            fieldWithPath("value[].type").type(JsonFieldType.STRING).description("type"),
                            fieldWithPath("value[].is_required").type(JsonFieldType.BOOLEAN).description("is_required"),
                            fieldWithPath("value[].version_id").type(JsonFieldType.STRING).description("version_id"),
                            fieldWithPath("value[].version").type(JsonFieldType.STRING).description("version"),
                            fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("created_at").optional(),
                            fieldWithPath("value[].content").type(JsonFieldType.STRING).description("content")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /terms/agreements/{userId}")
    void getTermAgreementsByUser() throws Exception {
        // given
        String mockUserId = "mockUserId";
        List<TermAgreementsByUserResponseDto> mockTermAgreementsByUserResponseDtoList = MockUtil.convertJsonToProjectionList(
            "fixture/userservice/terms/mockTermAgreementsByUserResponseDtoList.json",
            new TypeReference<List<TermAgreementsByUserResponseDto>>() {
            });

        when(userTermsService.getTermAgreementsByUser(anyString()))
            .thenReturn(mockTermAgreementsByUserResponseDtoList);

        // then
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/terms/agreements/{userId}", mockUserId)
                .contentType("application/json;charset=UTF-8")
        ).andDo(
            MockMvcRestDocumentationWrapper.document("02. 최신버전 약관에 대하여 약관전체 목록 및 user의 동의 여부",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .description("02. 최신버전 약관에 대하여 약관전체 목록 및 user의 동의 여부")
                        .requestHeaders(
                            headerWithName(HttpHeaders.CONTENT_TYPE).description("application/json; charset=UTF-8")
                        )
                        .pathParameters(
                            parameterWithName("userId").type(SimpleType.STRING).description("user id")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("code"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("message"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("value"),
                            fieldWithPath("value[].term_id").type(JsonFieldType.STRING).description("term_id"),
                            fieldWithPath("value[].term_title").type(JsonFieldType.STRING).description("term_title"),
                            fieldWithPath("value[].is_required").type(JsonFieldType.BOOLEAN).description("is_required"),
                            fieldWithPath("value[].latest_version_id").type(JsonFieldType.STRING).description("latest_version_id"),
                            fieldWithPath("value[].version").type(JsonFieldType.STRING).description("version"),
                            fieldWithPath("value[].is_agreed").type(JsonFieldType.BOOLEAN).description("is_agreed"),
                            fieldWithPath("value[].agreed_at").type(JsonFieldType.STRING).description("agreed_at")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /terms/agreements/{userId}")
    void agreementTermsByUser() throws Exception {
        // given
        String mockUserId = "mockUserId";

        AgreementTermsByUserRequestDto mockAgreementTermsByUserRequestDto = MockUtil.readJsonFileToClass(
            "fixture/userservice/terms/mockAgreementTermsByUserRequestDto.json", AgreementTermsByUserRequestDto.class);

        doNothing()
            .when(userTermsService)
            .agreementTermsByUser(anyString(), any());

        // then
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/terms/agreements/{userId}", mockUserId)
                .contentType("application/json;charset=UTF-8")
                .content(objectMapper.writeValueAsString(mockAgreementTermsByUserRequestDto))
        ).andDo(
            MockMvcRestDocumentationWrapper.document("03. 약관 동의하기",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .description("03. 약관 동의하기")
                        .requestHeaders(
                            headerWithName(HttpHeaders.CONTENT_TYPE).description("application/json; charset=UTF-8")
                        )
                        .pathParameters(
                            parameterWithName("userId").type(SimpleType.STRING).description("user id")
                        )
                        .requestFields(
                            fieldWithPath("agreement_terms_list[]").type(JsonFieldType.ARRAY).description("code"),
                            fieldWithPath("agreement_terms_list[].term_version_id").type(JsonFieldType.NUMBER).description("term_version_id"),
                            fieldWithPath("agreement_terms_list[].is_agreed").type(JsonFieldType.BOOLEAN).description("is_agreed")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("code"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("message")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());

    }
}
