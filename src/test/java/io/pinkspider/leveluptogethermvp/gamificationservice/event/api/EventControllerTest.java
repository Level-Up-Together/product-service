package io.pinkspider.leveluptogethermvp.gamificationservice.event.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import io.pinkspider.leveluptogethermvp.gamificationservice.event.api.dto.EventResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.application.EventImageStorageService;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.application.EventService;
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
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = EventController.class,
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
class EventControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private EventImageStorageService eventImageStorageService;

    private static final String MOCK_USER_ID = "test-user-123";

    private EventResponse createMockEventResponse() {
        return EventResponse.builder()
            .id(1L)
            .name("2026 신년 맞이 이벤트")
            .nameEn("2026 New Year Event")
            .nameAr("حدث رأس السنة 2026")
            .description("새해를 맞아 특별한 미션을 완료하고 보상을 받으세요!")
            .descriptionEn("Complete special missions for the new year and get rewards!")
            .descriptionAr("أكمل المهام الخاصة للعام الجديد واحصل على المكافآت!")
            .imageUrl("/uploads/events/new-year-2026.png")
            .startAt(LocalDateTime.of(2026, 1, 1, 0, 0))
            .endAt(LocalDateTime.of(2026, 1, 31, 23, 59, 59))
            .rewardTitleId(10L)
            .rewardTitleName("신년 챌린저")
            .status("IN_PROGRESS")
            .statusName("진행중")
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("GET /api/v1/events/current : 현재 진행중인 이벤트 목록 조회")
    void getCurrentEventsTest() throws Exception {
        // given
        List<EventResponse> events = List.of(createMockEventResponse());
        when(eventService.getCurrentEvents(anyString())).thenReturn(events);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/events/current")
                .with(user(MOCK_USER_ID))
                .header("Accept-Language", "ko")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("이벤트-01. 현재 진행중인 이벤트 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Event")
                        .description("현재 진행중인 이벤트 목록 조회")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.ARRAY).description("이벤트 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("이벤트 ID"),
                            fieldWithPath("value[].name").type(JsonFieldType.STRING).description("이벤트명"),
                            fieldWithPath("value[].name_en").type(JsonFieldType.STRING).description("이벤트명 (영어)").optional(),
                            fieldWithPath("value[].name_ar").type(JsonFieldType.STRING).description("이벤트명 (아랍어)").optional(),
                            fieldWithPath("value[].description").type(JsonFieldType.STRING).description("설명").optional(),
                            fieldWithPath("value[].description_en").type(JsonFieldType.STRING).description("설명 (영어)").optional(),
                            fieldWithPath("value[].description_ar").type(JsonFieldType.STRING).description("설명 (아랍어)").optional(),
                            fieldWithPath("value[].image_url").type(JsonFieldType.STRING).description("이미지 URL").optional(),
                            fieldWithPath("value[].start_at").type(JsonFieldType.STRING).description("시작일시"),
                            fieldWithPath("value[].end_at").type(JsonFieldType.STRING).description("종료일시"),
                            fieldWithPath("value[].reward_title_id").type(JsonFieldType.NUMBER).description("보상 칭호 ID").optional(),
                            fieldWithPath("value[].reward_title_name").type(JsonFieldType.STRING).description("보상 칭호명").optional(),
                            fieldWithPath("value[].status").type(JsonFieldType.STRING).description("상태 (SCHEDULED, IN_PROGRESS, ENDED)"),
                            fieldWithPath("value[].status_name").type(JsonFieldType.STRING).description("상태 표시명"),
                            fieldWithPath("value[].is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부"),
                            fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("생성일시"),
                            fieldWithPath("value[].modified_at").type(JsonFieldType.STRING).description("수정일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/events/active : 활성 또는 예정된 이벤트 목록 조회")
    void getActiveOrUpcomingEventsTest() throws Exception {
        // given
        List<EventResponse> events = List.of(createMockEventResponse());
        when(eventService.getActiveOrUpcomingEvents(anyString())).thenReturn(events);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/events/active")
                .with(user(MOCK_USER_ID))
                .header("Accept-Language", "ko")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("이벤트-02. 활성 또는 예정된 이벤트 목록 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Event")
                        .description("현재 진행중이거나 예정된 이벤트 목록 조회 (Home 화면 표시용)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.ARRAY).description("이벤트 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("이벤트 ID"),
                            fieldWithPath("value[].name").type(JsonFieldType.STRING).description("이벤트명"),
                            fieldWithPath("value[].name_en").type(JsonFieldType.STRING).description("이벤트명 (영어)").optional(),
                            fieldWithPath("value[].name_ar").type(JsonFieldType.STRING).description("이벤트명 (아랍어)").optional(),
                            fieldWithPath("value[].description").type(JsonFieldType.STRING).description("설명").optional(),
                            fieldWithPath("value[].description_en").type(JsonFieldType.STRING).description("설명 (영어)").optional(),
                            fieldWithPath("value[].description_ar").type(JsonFieldType.STRING).description("설명 (아랍어)").optional(),
                            fieldWithPath("value[].image_url").type(JsonFieldType.STRING).description("이미지 URL").optional(),
                            fieldWithPath("value[].start_at").type(JsonFieldType.STRING).description("시작일시"),
                            fieldWithPath("value[].end_at").type(JsonFieldType.STRING).description("종료일시"),
                            fieldWithPath("value[].reward_title_id").type(JsonFieldType.NUMBER).description("보상 칭호 ID").optional(),
                            fieldWithPath("value[].reward_title_name").type(JsonFieldType.STRING).description("보상 칭호명").optional(),
                            fieldWithPath("value[].status").type(JsonFieldType.STRING).description("상태 (SCHEDULED, IN_PROGRESS, ENDED)"),
                            fieldWithPath("value[].status_name").type(JsonFieldType.STRING).description("상태 표시명"),
                            fieldWithPath("value[].is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부"),
                            fieldWithPath("value[].created_at").type(JsonFieldType.STRING).description("생성일시"),
                            fieldWithPath("value[].modified_at").type(JsonFieldType.STRING).description("수정일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/events/{id} : 이벤트 상세 조회")
    void getEventTest() throws Exception {
        // given
        EventResponse eventResponse = createMockEventResponse();
        when(eventService.getEvent(anyLong(), anyString())).thenReturn(eventResponse);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/events/{id}", 1L)
                .with(user(MOCK_USER_ID))
                .header("Accept-Language", "ko")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("이벤트-03. 이벤트 상세 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Event")
                        .description("이벤트 상세 정보 조회")
                        .pathParameters(
                            parameterWithName("id").type(SimpleType.NUMBER).description("이벤트 ID")
                        )
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("이벤트 정보"),
                            fieldWithPath("value.id").type(JsonFieldType.NUMBER).description("이벤트 ID"),
                            fieldWithPath("value.name").type(JsonFieldType.STRING).description("이벤트명"),
                            fieldWithPath("value.name_en").type(JsonFieldType.STRING).description("이벤트명 (영어)").optional(),
                            fieldWithPath("value.name_ar").type(JsonFieldType.STRING).description("이벤트명 (아랍어)").optional(),
                            fieldWithPath("value.description").type(JsonFieldType.STRING).description("설명").optional(),
                            fieldWithPath("value.description_en").type(JsonFieldType.STRING).description("설명 (영어)").optional(),
                            fieldWithPath("value.description_ar").type(JsonFieldType.STRING).description("설명 (아랍어)").optional(),
                            fieldWithPath("value.image_url").type(JsonFieldType.STRING).description("이미지 URL").optional(),
                            fieldWithPath("value.start_at").type(JsonFieldType.STRING).description("시작일시"),
                            fieldWithPath("value.end_at").type(JsonFieldType.STRING).description("종료일시"),
                            fieldWithPath("value.reward_title_id").type(JsonFieldType.NUMBER).description("보상 칭호 ID").optional(),
                            fieldWithPath("value.reward_title_name").type(JsonFieldType.STRING).description("보상 칭호명").optional(),
                            fieldWithPath("value.status").type(JsonFieldType.STRING).description("상태 (SCHEDULED, IN_PROGRESS, ENDED)"),
                            fieldWithPath("value.status_name").type(JsonFieldType.STRING).description("상태 표시명"),
                            fieldWithPath("value.is_active").type(JsonFieldType.BOOLEAN).description("활성화 여부"),
                            fieldWithPath("value.created_at").type(JsonFieldType.STRING).description("생성일시"),
                            fieldWithPath("value.modified_at").type(JsonFieldType.STRING).description("수정일시")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/events/upload-image : 이벤트 이미지 업로드")
    void uploadEventImageTest() throws Exception {
        // given
        MockMultipartFile imageFile = new MockMultipartFile(
            "file",
            "event-image.png",
            "image/png",
            "test image content".getBytes()
        );

        when(eventImageStorageService.store(any())).thenReturn("/uploads/events/test-uuid.png");

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.multipart("/api/v1/events/upload-image")
                .file(imageFile)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.MULTIPART_FORM_DATA)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("이벤트-04. 이벤트 이미지 업로드",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Event")
                        .description("이벤트 이미지 업로드 (Admin용). 최대 10MB, 허용 확장자: jpg, jpeg, png, gif, webp")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("업로드 결과"),
                            fieldWithPath("value.image_url").type(JsonFieldType.STRING).description("업로드된 이미지 URL")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
