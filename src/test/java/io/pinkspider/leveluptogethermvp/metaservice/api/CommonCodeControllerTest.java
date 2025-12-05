package io.pinkspider.leveluptogethermvp.metaservice.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.fasterxml.jackson.core.type.TypeReference;
import io.pinkspider.global.component.CommonCodeHelper;
import io.pinkspider.global.constants.msaapiuri.MetaServiceUriContants;
import io.pinkspider.global.domain.dto.CommonCodeDto;
import io.pinkspider.leveluptogethermvp.config.BaseTestController;
import io.pinkspider.util.MockUtil;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = CommonCodeController.class)
@ActiveProfiles("test")
class CommonCodeControllerTest extends BaseTestController {

    @Test
    @DisplayName("GET " + MetaServiceUriContants.COMMON_CODE_BY_PARENT_ID + ": 해당 parent-id의 자식 common_code 조회")
    void getChildCommonCodeByParentIdTest() throws Exception {
        // given
        String parentId = "AG10";

        List<CommonCodeDto> commonCodeDtoList = MockUtil.readJsonFileToClassList("fixture/metaService/commonCodeDtoList.json",
            new TypeReference<List<CommonCodeDto>>() {
            });

        mockStaticCommonCodeHelper.when(() -> CommonCodeHelper.getChildCommonCodeByParentId(parentId))
            .thenReturn(commonCodeDtoList);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get(MetaServiceUriContants.COMMON_CODE_BY_PARENT_ID, parentId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("해당 parent-id의 자식 common_code 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .description("해당 parent-id의 자식 common_code 조회")
                        .pathParameters(
                            parameterWithName("parent-id").description("parent-id")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("code"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("message"),
                            fieldWithPath("value").type(JsonFieldType.ARRAY).description("value"),
                            fieldWithPath("value[].id").type(JsonFieldType.STRING).description("공통코드 id"),
                            fieldWithPath("value[].code_name").type(JsonFieldType.STRING).description("공통코드 이름"),
                            fieldWithPath("value[].code_title").type(JsonFieldType.STRING).description("공통코드 타이틀"),
                            fieldWithPath("value[].description").type(JsonFieldType.STRING).description("공통코드 설명"),
                            fieldWithPath("value[].parent_id").type(JsonFieldType.STRING).description("부모 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET " + MetaServiceUriContants.COMMON_CODE_BY_ID + " : 공통코드 id로 common_code 조회")
    void getCommonCodeByIdTest() throws Exception {
        // given
        String id = "AG11";

        CommonCodeDto commonCodeDto = MockUtil.readJsonFileToClass("fixture/metaService/commonCodeDto.json", CommonCodeDto.class);

        mockStaticCommonCodeHelper.when(() -> CommonCodeHelper.getCommonCodeById(id))
            .thenReturn(commonCodeDto);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get(MetaServiceUriContants.COMMON_CODE_BY_ID, id)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("공통코드 id로 common_code 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .description("공통코드 id로 common_code 조회")
                        .pathParameters(
                            parameterWithName("id").description("공통코드 id")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("code"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("message"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("value"),
                            fieldWithPath("value.id").type(JsonFieldType.STRING).description("공통코드 id"),
                            fieldWithPath("value.code_name").type(JsonFieldType.STRING).description("공통코드 이름"),
                            fieldWithPath("value.code_title").type(JsonFieldType.STRING).description("공통코드 타이틀"),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("공통코드 설명"),
                            fieldWithPath("value.parent_id").type(JsonFieldType.STRING).description("부모 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
