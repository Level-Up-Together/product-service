package io.pinkspider.leveluptogethermvp.userservice.unit.user.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
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
import io.pinkspider.leveluptogethermvp.userservice.unit.user.application.UserAdminInternalService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserAdminPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserBlacklistAdminRequest;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserBlacklistAdminResponse;
import io.pinkspider.util.MockUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

@WebMvcTest(controllers = UserAdminInternalController.class,
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
class UserAdminInternalControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    private UserAdminInternalService userAdminInternalService;

    @Nested
    @DisplayName("GET /api/internal/users")
    class SearchUsersTest {

        @Test
        @DisplayName("GET /api/internal/users : 사용자 목록 검색")
        void searchUsers() throws Exception {
            // given
            UserAdminPageResponse response = MockUtil.readJsonFileToClass(
                "fixture/userservice/admin/mockUserAdminPageResponse.json",
                UserAdminPageResponse.class);
            when(userAdminInternalService.searchUsers(isNull(), isNull(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(response);

            // when
            ResultActions resultActions = mockMvc.perform(
                RestDocumentationRequestBuilders.get("/api/internal/users")
                    .contentType(MediaType.APPLICATION_JSON)
            ).andDo(
                MockMvcRestDocumentationWrapper.document("어드민-01. 사용자 목록 검색",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Admin User")
                            .description("사용자 목록 검색")
                            .responseFields(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("value").type(JsonFieldType.OBJECT).description("응답 데이터"),
                                fieldWithPath("value.content").type(JsonFieldType.ARRAY).description("사용자 목록"),
                                fieldWithPath("value.content[].id").type(JsonFieldType.STRING).description("사용자 ID"),
                                fieldWithPath("value.content[].nickname").type(JsonFieldType.STRING).description("닉네임"),
                                fieldWithPath("value.content[].email").type(JsonFieldType.STRING).description("이메일").optional(),
                                fieldWithPath("value.content[].picture").type(JsonFieldType.STRING).description("프로필 사진").optional(),
                                fieldWithPath("value.content[].provider").type(JsonFieldType.STRING).description("OAuth 제공자").optional(),
                                fieldWithPath("value.content[].status").type(JsonFieldType.STRING).description("상태").optional(),
                                fieldWithPath("value.content[].last_login_ip").type(JsonFieldType.STRING).description("마지막 로그인 IP").optional(),
                                fieldWithPath("value.content[].last_login_country").type(JsonFieldType.STRING).description("마지막 로그인 국가").optional(),
                                fieldWithPath("value.content[].last_login_country_code").type(JsonFieldType.STRING).description("국가 코드").optional(),
                                fieldWithPath("value.content[].last_login_at").type(JsonFieldType.STRING).description("마지막 로그인 시각").optional(),
                                fieldWithPath("value.content[].warning_count").type(JsonFieldType.NUMBER).description("신고 처리 경고 누적 횟수").optional(),
                                fieldWithPath("value.content[].suspension_count").type(JsonFieldType.NUMBER).description("신고 처리 정지 누적 횟수").optional(),
                                fieldWithPath("value.content[].created_at").type(JsonFieldType.STRING).description("생성 시각").optional(),
                                fieldWithPath("value.content[].modified_at").type(JsonFieldType.STRING).description("수정 시각").optional(),
                                fieldWithPath("value.page").type(JsonFieldType.NUMBER).description("현재 페이지"),
                                fieldWithPath("value.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                                fieldWithPath("value.total_elements").type(JsonFieldType.NUMBER).description("전체 건수"),
                                fieldWithPath("value.total_pages").type(JsonFieldType.NUMBER).description("전체 페이지"),
                                fieldWithPath("value.first").type(JsonFieldType.BOOLEAN).description("첫 페이지 여부"),
                                fieldWithPath("value.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부")
                            )
                            .build()
                    )
                )
            );

            // then
            resultActions.andExpect(MockMvcResultMatchers.status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/internal/users/{userId}")
    class GetUserTest {

        @Test
        @DisplayName("GET /api/internal/users/{userId} : 사용자 단건 조회")
        void getUser() throws Exception {
            // given
            UserAdminResponse response = MockUtil.readJsonFileToClass(
                "fixture/userservice/admin/mockUserAdminResponse.json",
                UserAdminResponse.class);
            when(userAdminInternalService.getUser("user-001")).thenReturn(response);

            // when
            ResultActions resultActions = mockMvc.perform(
                RestDocumentationRequestBuilders.get("/api/internal/users/{userId}", "user-001")
                    .contentType(MediaType.APPLICATION_JSON)
            ).andDo(
                MockMvcRestDocumentationWrapper.document("어드민-02. 사용자 단건 조회",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Admin User")
                            .description("사용자 단건 조회")
                            .pathParameters(
                                parameterWithName("userId").type(SimpleType.STRING).description("사용자 ID")
                            )
                            .responseFields(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("value").type(JsonFieldType.OBJECT).description("응답 데이터").optional(),
                                fieldWithPath("value.id").type(JsonFieldType.STRING).description("사용자 ID").optional(),
                                fieldWithPath("value.nickname").type(JsonFieldType.STRING).description("닉네임").optional(),
                                fieldWithPath("value.email").type(JsonFieldType.STRING).description("이메일").optional(),
                                fieldWithPath("value.picture").type(JsonFieldType.STRING).description("프로필 사진").optional(),
                                fieldWithPath("value.provider").type(JsonFieldType.STRING).description("OAuth 제공자").optional(),
                                fieldWithPath("value.status").type(JsonFieldType.STRING).description("상태").optional(),
                                fieldWithPath("value.last_login_ip").type(JsonFieldType.STRING).description("마지막 로그인 IP").optional(),
                                fieldWithPath("value.last_login_country").type(JsonFieldType.STRING).description("국가").optional(),
                                fieldWithPath("value.last_login_country_code").type(JsonFieldType.STRING).description("국가 코드").optional(),
                                fieldWithPath("value.last_login_at").type(JsonFieldType.STRING).description("마지막 로그인 시각").optional(),
                                fieldWithPath("value.warning_count").type(JsonFieldType.NUMBER).description("신고 처리 경고 누적 횟수").optional(),
                                fieldWithPath("value.suspension_count").type(JsonFieldType.NUMBER).description("신고 처리 정지 누적 횟수").optional(),
                                fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성 시각").optional(),
                                fieldWithPath("value.modified_at").type(JsonFieldType.STRING).description("수정 시각").optional()
                            )
                            .build()
                    )
                )
            );

            // then
            resultActions.andExpect(MockMvcResultMatchers.status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /api/internal/users/{userId}/blacklist")
    class AddToBlacklistTest {

        @Test
        @DisplayName("POST /api/internal/users/{userId}/blacklist : 블랙리스트 등록")
        void addToBlacklist() throws Exception {
            // given
            UserBlacklistAdminRequest request = new UserBlacklistAdminRequest(
                "PERMANENT_BAN", "규정 위반", null, 1L);
            UserBlacklistAdminResponse response = new UserBlacklistAdminResponse(
                1L, "user-001", "PERMANENT_BAN", "규정 위반", 1L,
                LocalDateTime.of(2025, 1, 1, 0, 0), null, true, null
            );
            when(userAdminInternalService.addToBlacklist(eq("user-001"), any(UserBlacklistAdminRequest.class)))
                .thenReturn(response);

            // when
            ResultActions resultActions = mockMvc.perform(
                RestDocumentationRequestBuilders.post("/api/internal/users/{userId}/blacklist", "user-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            ).andDo(
                MockMvcRestDocumentationWrapper.document("어드민-03. 블랙리스트 등록",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Admin User")
                            .description("사용자 블랙리스트 등록")
                            .pathParameters(
                                parameterWithName("userId").type(SimpleType.STRING).description("사용자 ID")
                            )
                            .requestFields(
                                fieldWithPath("blacklist_type").type(JsonFieldType.STRING).description("블랙리스트 유형"),
                                fieldWithPath("reason").type(JsonFieldType.STRING).description("사유"),
                                fieldWithPath("ended_at").type(JsonFieldType.STRING).description("종료 일시").optional(),
                                fieldWithPath("admin_id").type(JsonFieldType.NUMBER).description("관리자 ID")
                            )
                            .responseFields(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("value").type(JsonFieldType.OBJECT).description("응답 데이터").optional(),
                                fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("ID").optional(),
                                fieldWithPath("value.user_id").type(JsonFieldType.STRING).description("사용자 ID").optional(),
                                fieldWithPath("value.blacklist_type").type(JsonFieldType.STRING).description("블랙리스트 유형").optional(),
                                fieldWithPath("value.reason").type(JsonFieldType.STRING).description("사유").optional(),
                                fieldWithPath("value.admin_id").type(JsonFieldType.NUMBER).description("관리자 ID").optional(),
                                fieldWithPath("value.started_at").type(JsonFieldType.STRING).description("시작 일시").optional(),
                                fieldWithPath("value.ended_at").type(JsonFieldType.STRING).description("종료 일시").optional(),
                                fieldWithPath("value.is_active").type(JsonFieldType.BOOLEAN).description("활성 여부").optional(),
                                fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성 시각").optional()
                            )
                            .build()
                    )
                )
            );

            // then
            resultActions.andExpect(MockMvcResultMatchers.status().isOk());
        }
    }

    @Nested
    @DisplayName("DELETE /api/internal/users/{userId}/blacklist")
    class RemoveFromBlacklistTest {

        @Test
        @DisplayName("DELETE /api/internal/users/{userId}/blacklist : 블랙리스트 해제")
        void removeFromBlacklist() throws Exception {
            // given
            doNothing().when(userAdminInternalService)
                .removeFromBlacklist(eq("user-001"), eq(1L), anyString());

            // when
            ResultActions resultActions = mockMvc.perform(
                RestDocumentationRequestBuilders.delete("/api/internal/users/{userId}/blacklist", "user-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .param("adminId", "1")
                    .param("reason", "해제 사유")
            ).andDo(
                MockMvcRestDocumentationWrapper.document("어드민-04. 블랙리스트 해제",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Admin User")
                            .description("사용자 블랙리스트 해제")
                            .pathParameters(
                                parameterWithName("userId").type(SimpleType.STRING).description("사용자 ID")
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
    }

    @Nested
    @DisplayName("POST /api/internal/users/batch")
    class GetUsersByIdsTest {

        @Test
        @DisplayName("POST /api/internal/users/batch : 사용자 일괄 조회")
        void getUsersByIds() throws Exception {
            // given
            List<String> userIds = List.of("user-001", "user-002");
            when(userAdminInternalService.getUsersByIds(any())).thenReturn(Map.of());

            // when
            ResultActions resultActions = mockMvc.perform(
                RestDocumentationRequestBuilders.post("/api/internal/users/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(userIds))
            ).andDo(
                MockMvcRestDocumentationWrapper.document("어드민-05. 사용자 일괄 조회",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Admin User")
                            .description("사용자 ID 목록으로 일괄 조회")
                            .responseFields(
                                fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                fieldWithPath("value").type(JsonFieldType.OBJECT).description("응답 데이터")
                            )
                            .build()
                    )
                )
            );

            // then
            resultActions.andExpect(MockMvcResultMatchers.status().isOk());
        }
    }
}
