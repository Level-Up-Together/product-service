package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.partWithName;
import static org.springframework.restdocs.request.RequestDocumentation.requestParts;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.api.exception.WidgetMessageExceptionHandler;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.WidgetMessageImageException;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.WidgetMessageImageService;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.WidgetMessageNotFoundException;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.WidgetMessageService;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageImageUploadResponse;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageListResponse;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageListResponse.WidgetMessageGapResponse;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageRequest;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto.WidgetMessageResponse;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.enums.WidgetMessageCondition;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(
        controllers = WidgetMessageInternalController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("WidgetMessageInternalController 테스트 (LUT-775)")
class WidgetMessageInternalControllerTest {

    private static final String TAG = "Widget Message - Admin Internal";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean WidgetMessageService widgetMessageService;
    @MockitoBean WidgetMessageImageService widgetMessageImageService;

    private static final String IMAGE_URL =
            "https://images.level-up-together.com/widget/0b6f8c1e-5d2a-4f0e-9a7b-3c1d2e4f5a6b.png";

    private static WidgetMessageResponse response(long id, boolean active) {
        return new WidgetMessageResponse(
                id,
                6,
                12,
                WidgetMessageCondition.NOT_DONE,
                "아침의 한 걸음이 하루를 바꿔요. 오늘 미션을 시작해 볼까요?",
                "One small step this morning can change your day.",
                "朝の一歩が一日を変えます。",
                "خطوة صغيرة في الصباح تغيّر يومك.",
                active,
                1,
                IMAGE_URL,
                LocalDateTime.of(2026, 11, 11, 0, 0),
                LocalDateTime.of(2026, 11, 11, 1, 0));
    }

    private static WidgetMessageRequest request() {
        return new WidgetMessageRequest(
                6,
                12,
                WidgetMessageCondition.NOT_DONE,
                "아침의 한 걸음이 하루를 바꿔요. 오늘 미션을 시작해 볼까요?",
                "One small step this morning can change your day.",
                "朝の一歩が一日を変えます。",
                "خطوة صغيرة في الصباح تغيّر يومك.",
                true,
                1,
                IMAGE_URL);
    }

    private static FieldDescriptor[] messageFields(String prefix) {
        return new FieldDescriptor[] {
            fieldWithPath(prefix + "id").type(JsonFieldType.NUMBER).description("문장 ID"),
            fieldWithPath(prefix + "start_hour")
                    .type(JsonFieldType.NUMBER)
                    .description("시작 시각(유저 로컬, 0~23, 포함)"),
            fieldWithPath(prefix + "end_hour")
                    .type(JsonFieldType.NUMBER)
                    .description("끝 시각(유저 로컬, 1~24, 미포함). 시작보다 작으면 자정을 넘는 구간"),
            fieldWithPath(prefix + "condition")
                    .type(JsonFieldType.STRING)
                    .description("NOT_DONE(오늘 완료 전) / DONE(완료 후) / ALL"),
            fieldWithPath(prefix + "text_ko").type(JsonFieldType.STRING).description("문장(ko)"),
            fieldWithPath(prefix + "text_en")
                    .type(JsonFieldType.STRING)
                    .description("문장(en), 없으면 null")
                    .optional(),
            fieldWithPath(prefix + "text_ja")
                    .type(JsonFieldType.STRING)
                    .description("문장(ja), 없으면 null")
                    .optional(),
            fieldWithPath(prefix + "text_ar")
                    .type(JsonFieldType.STRING)
                    .description("문장(ar), 없으면 null")
                    .optional(),
            fieldWithPath(prefix + "active").type(JsonFieldType.BOOLEAN).description("노출 여부"),
            fieldWithPath(prefix + "sort_order")
                    .type(JsonFieldType.NUMBER)
                    .description("관리 화면 정렬(같은 시작 시각 안)"),
            fieldWithPath(prefix + "background_image_url")
                    .type(JsonFieldType.STRING)
                    .description("위젯 배경 이미지 절대 URL(LUT-797). 없으면 null — 위젯은 하얀 배경")
                    .optional(),
            fieldWithPath(prefix + "created_at").type(JsonFieldType.STRING).description("생성 일시"),
            fieldWithPath(prefix + "modified_at")
                    .type(JsonFieldType.STRING)
                    .description("수정 일시")
                    .optional()
        };
    }

    private static FieldDescriptor[] singleResponseFields() {
        FieldDescriptor[] message = messageFields("value.");
        FieldDescriptor[] all = new FieldDescriptor[message.length + 2];
        all[0] = fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드");
        all[1] = fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지");
        System.arraycopy(message, 0, all, 2, message.length);
        return all;
    }

    private static FieldDescriptor[] requestFields() {
        return new FieldDescriptor[] {
            fieldWithPath("start_hour").type(JsonFieldType.NUMBER).description("시작 시각 0~23"),
            fieldWithPath("end_hour")
                    .type(JsonFieldType.NUMBER)
                    .description("끝 시각 1~24(미포함), 시작과 같을 수 없음. 시작보다 작으면 자정을 넘는 구간"),
            fieldWithPath("condition")
                    .type(JsonFieldType.STRING)
                    .description("NOT_DONE / DONE / ALL"),
            fieldWithPath("text_ko")
                    .type(JsonFieldType.STRING)
                    .description("문장(ko, 필수, 120자). {streak} 는 위젯이 연속 실천 일수로 바꾼다"),
            fieldWithPath("text_en")
                    .type(JsonFieldType.STRING)
                    .description("문장(en, 120자). 비우면 ko")
                    .optional(),
            fieldWithPath("text_ja")
                    .type(JsonFieldType.STRING)
                    .description("문장(ja, 120자). 비우면 en → ko")
                    .optional(),
            fieldWithPath("text_ar")
                    .type(JsonFieldType.STRING)
                    .description("문장(ar, 120자). 비우면 en → ko")
                    .optional(),
            fieldWithPath("active")
                    .type(JsonFieldType.BOOLEAN)
                    .description("노출 여부. 비우면 작성 = 켬, 수정 = 그대로")
                    .optional(),
            fieldWithPath("sort_order")
                    .type(JsonFieldType.NUMBER)
                    .description("정렬(0 이상). 비우면 작성 = 0, 수정 = 그대로")
                    .optional(),
            fieldWithPath("background_image_url")
                    .type(JsonFieldType.STRING)
                    .description(
                            "위젯 배경 이미지(LUT-797) — 업로드 API(POST /images)가 준 주소만(아니면 400 080205)."
                                    + " 수정에서 비우면(null) 그대로, 빈 문자열이면 지움(하얀 배경)")
                    .optional()
        };
    }

    @Test
    @DisplayName("GET /api/internal/widget-messages : 목록 + 빈 시간대")
    void list() throws Exception {
        when(widgetMessageService.list(WidgetMessageCondition.NOT_DONE))
                .thenReturn(
                        new WidgetMessageListResponse(
                                List.of(response(1, true)),
                                List.of(
                                        new WidgetMessageGapResponse(
                                                WidgetMessageCondition.NOT_DONE, 2, 6))));

        FieldDescriptor[] message = messageFields("value.messages[].");
        FieldDescriptor[] fields = new FieldDescriptor[message.length + 6];
        fields[0] = fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드");
        fields[1] = fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지");
        fields[2] =
                fieldWithPath("value.messages")
                        .type(JsonFieldType.ARRAY)
                        .description("문장 목록(시작 시각 → 정렬 → ID 순)");
        System.arraycopy(message, 0, fields, 3, message.length);
        fields[message.length + 3] =
                fieldWithPath("value.gaps[].condition")
                        .type(JsonFieldType.STRING)
                        .description("NOT_DONE(완료 전 화면) / DONE(완료 후 화면)");
        fields[message.length + 4] =
                fieldWithPath("value.gaps[].start_hour")
                        .type(JsonFieldType.NUMBER)
                        .description("빈 구간 시작(포함)");
        fields[message.length + 5] =
                fieldWithPath("value.gaps[].end_hour")
                        .type(JsonFieldType.NUMBER)
                        .description("빈 구간 끝(미포함)");

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get("/api/internal/widget-messages")
                                .param("condition", "NOT_DONE"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.value.messages[0].id").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.value.gaps[0].start_hour").value(2))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "내부API-위젯문장-01. 목록",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description(
                                                        "홈 화면 위젯 시간대별 문장 목록 + 빈 시간대(활성 문장 전체 기준,"
                                                                + " 필터와 무관) — Admin Backend 전용")
                                                .queryParameters(
                                                        parameterWithName("condition")
                                                                .type(SimpleType.STRING)
                                                                .description(
                                                                        "조건 필터 NOT_DONE / DONE /"
                                                                                + " ALL, 비우면 전체")
                                                                .optional())
                                                .responseFields(fields)
                                                .build())));
    }

    @Test
    @DisplayName("GET /api/internal/widget-messages/{id} : 단건")
    void get() throws Exception {
        when(widgetMessageService.get(1L)).thenReturn(response(1, true));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                "/api/internal/widget-messages/{id}", 1))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.value.start_hour").value(6))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "내부API-위젯문장-02. 단건",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description("위젯 문장 단건 — 없으면 404 080201")
                                                .pathParameters(
                                                        parameterWithName("id")
                                                                .description("문장 ID"))
                                                .responseFields(singleResponseFields())
                                                .build())));
    }

    @Test
    @DisplayName("POST /api/internal/widget-messages : 작성")
    void create() throws Exception {
        when(widgetMessageService.create(any())).thenReturn(response(1, true));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/internal/widget-messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request())))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.value.id").value(1))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "내부API-위젯문장-03. 작성",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description(
                                                        "위젯 문장 작성 — 입력 오류 400. 저장하면 위젯 조회 메모(노드별"
                                                                + " 60초)를 비운다")
                                                .requestFields(requestFields())
                                                .responseFields(singleResponseFields())
                                                .build())));
    }

    @Test
    @DisplayName("PUT /api/internal/widget-messages/{id} : 수정")
    void update() throws Exception {
        when(widgetMessageService.update(eq(1L), any())).thenReturn(response(1, true));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.put(
                                        "/api/internal/widget-messages/{id}", 1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request())))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "내부API-위젯문장-04. 수정",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description(
                                                        "위젯 문장 수정 — active·sort_order 를 비우면 그대로."
                                                                + " 없으면 404 080201")
                                                .pathParameters(
                                                        parameterWithName("id")
                                                                .description("문장 ID"))
                                                .requestFields(requestFields())
                                                .responseFields(singleResponseFields())
                                                .build())));
    }

    @Test
    @DisplayName("PATCH /api/internal/widget-messages/{id}/toggle-active : 활성 토글")
    void toggleActive() throws Exception {
        when(widgetMessageService.toggleActive(1L)).thenReturn(response(1, false));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.patch(
                                "/api/internal/widget-messages/{id}/toggle-active", 1))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.value.active").value(false))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "내부API-위젯문장-05. 활성 토글",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description("위젯 문장 노출 여부 뒤집기")
                                                .pathParameters(
                                                        parameterWithName("id")
                                                                .description("문장 ID"))
                                                .responseFields(singleResponseFields())
                                                .build())));
    }

    @Test
    @DisplayName("DELETE /api/internal/widget-messages/{id} : 삭제")
    void delete() throws Exception {
        mockMvc.perform(
                        RestDocumentationRequestBuilders.delete(
                                "/api/internal/widget-messages/{id}", 1))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "내부API-위젯문장-06. 삭제",
                                preprocessRequest(prettyPrint()),
                                preprocessResponse(prettyPrint()),
                                resource(
                                        ResourceSnippetParameters.builder()
                                                .tag(TAG)
                                                .description("위젯 문장 삭제 — 없으면 404 080201")
                                                .pathParameters(
                                                        parameterWithName("id")
                                                                .description("문장 ID"))
                                                .build())));
        verify(widgetMessageService).delete(1L);
    }

    @Test
    @DisplayName("없는 문장은 404 080201")
    void notFound() throws Exception {
        doThrow(new WidgetMessageNotFoundException(99L)).when(widgetMessageService).delete(99L);

        mockMvc.perform(
                        RestDocumentationRequestBuilders.delete(
                                "/api/internal/widget-messages/{id}", 99))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.code")
                                .value(WidgetMessageExceptionHandler.CODE_NOT_FOUND));
    }

    @Test
    @DisplayName("조건 필터가 허용값 밖이면 400 080202")
    void invalidCondition() throws Exception {
        mockMvc.perform(
                        RestDocumentationRequestBuilders.get("/api/internal/widget-messages")
                                .param("condition", "SOMETIMES"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.code")
                                .value(WidgetMessageExceptionHandler.CODE_INVALID_PARAM));
        verifyNoInteractions(widgetMessageService);
    }

    @Test
    @DisplayName("시작과 끝이 같으면 400")
    void sameHours() throws Exception {
        WidgetMessageRequest same =
                new WidgetMessageRequest(
                        6, 6, WidgetMessageCondition.ALL, "문장", null, null, null, null, null, null);

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/internal/widget-messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(same)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        verifyNoInteractions(widgetMessageService);
    }

    @Test
    @DisplayName("ko 문장이 비었거나 시각이 범위 밖이면 400")
    void invalidFields() throws Exception {
        String body = "{\"start_hour\":24,\"end_hour\":0,\"condition\":\"ALL\",\"text_ko\":\" \"}";

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/internal/widget-messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        verifyNoInteractions(widgetMessageService);
    }

    @Test
    @DisplayName("POST /api/internal/widget-messages/images : 배경 이미지 업로드 (LUT-797)")
    void uploadImage() throws Exception {
        when(widgetMessageImageService.upload(any()))
                .thenReturn(new WidgetMessageImageUploadResponse(IMAGE_URL, 1024, 1024, 312_480L));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.multipart(
                                        "/api/internal/widget-messages/images")
                                .file(
                                        new MockMultipartFile(
                                                "file",
                                                "morning.png",
                                                "image/png",
                                                new byte[] {1, 2, 3}))
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.value.url").value(IMAGE_URL))
                .andExpect(MockMvcResultMatchers.jsonPath("$.value.width").value(1024))
                .andExpect(MockMvcResultMatchers.jsonPath("$.value.size_bytes").value(312480))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-widget-message-image-upload",
                                ResourceSnippetParameters.builder()
                                        .tag(TAG)
                                        .description(
                                                "위젯 문장 배경 이미지 업로드 (LUT-797) — JPG·PNG, 1MB 이하. 절대"
                                                        + " 주소를 돌려준다. 거절 400 080203, 저장 실패"
                                                        + " 500 080204"),
                                requestParts(
                                        partWithName("file")
                                                .description("이미지 파일 (JPG·PNG, 1MB 이하)")),
                                responseFields(
                                        fieldWithPath("code")
                                                .type(JsonFieldType.STRING)
                                                .description("응답 코드"),
                                        fieldWithPath("message")
                                                .type(JsonFieldType.STRING)
                                                .description("응답 메시지"),
                                        fieldWithPath("value.url")
                                                .type(JsonFieldType.STRING)
                                                .description(
                                                        "절대 URL — 문장 작성·수정의"
                                                                + " background_image_url 에 그대로"),
                                        fieldWithPath("value.width")
                                                .type(JsonFieldType.NUMBER)
                                                .description("가로 픽셀(헤더를 못 읽으면 null)")
                                                .optional(),
                                        fieldWithPath("value.height")
                                                .type(JsonFieldType.NUMBER)
                                                .description("세로 픽셀(헤더를 못 읽으면 null)")
                                                .optional(),
                                        fieldWithPath("value.size_bytes")
                                                .type(JsonFieldType.NUMBER)
                                                .description("파일 크기(바이트)"))));
    }

    @Test
    @DisplayName("배경 이미지 — 형식·크기 거절은 400 080203, 저장 실패는 500 080204")
    void uploadImageErrors() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "a.gif", "image/gif", new byte[] {1});
        when(widgetMessageImageService.upload(any()))
                .thenThrow(WidgetMessageImageException.invalid())
                .thenThrow(WidgetMessageImageException.saveFailed());

        mockMvc.perform(
                        RestDocumentationRequestBuilders.multipart(
                                        "/api/internal/widget-messages/images")
                                .file(file)
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.code")
                                .value(WidgetMessageImageException.CODE_INVALID));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.multipart(
                                        "/api/internal/widget-messages/images")
                                .file(file)
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.code")
                                .value(WidgetMessageImageException.CODE_SAVE_FAILED));
    }

    @Test
    @DisplayName("배경 이미지 — file 파트가 없으면 400 080203")
    void uploadImageWithoutFile() throws Exception {
        mockMvc.perform(
                        RestDocumentationRequestBuilders.multipart(
                                        "/api/internal/widget-messages/images")
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.code")
                                .value(WidgetMessageImageException.CODE_INVALID));
        verifyNoInteractions(widgetMessageImageService);
    }

    @Test
    @DisplayName("업로드 API 가 발급하지 않은 배경 주소로 저장하면 400 080205")
    void foreignImageUrl() throws Exception {
        when(widgetMessageService.create(any())).thenThrow(WidgetMessageImageException.notOwned());

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/internal/widget-messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request())))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(
                        MockMvcResultMatchers.jsonPath("$.code")
                                .value(WidgetMessageImageException.CODE_NOT_OWNED));
    }
}
