package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerRoomService;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerEnterResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerRoomListResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerRoomView;
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
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TowerRoomController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("TowerRoomController 테스트 (LUT-738)")
class TowerRoomControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TowerRoomService roomService;

    private static FieldDescriptor[] enterFields() {
        return new FieldDescriptor[] {
            fieldWithPath("code").description("응답 코드"),
            fieldWithPath("message").description("응답 메시지"),
            fieldWithPath("value.floor_number").description("층 번호"),
            fieldWithPath("value.instance_id").description("새 방 ID — 이 방 토픽으로 바꿔 구독·join 한다"),
            fieldWithPath("value.occupant_id").description("점유자 ID (그대로)"),
            fieldWithPath("value.resumed").description("true (자리를 이어 쓴다)"),
            fieldWithPath("value.mission_active").description("미션 중 — 방을 옮겨도 그대로"),
            fieldWithPath("value.visitor_key")
                    .type(JsonFieldType.STRING)
                    .description("방문객 키 (방문객만)")
                    .optional(),
            fieldWithPath("value.entry_point_x").description("층 입장 지점 X"),
            fieldWithPath("value.entry_point_y").description("층 입장 지점 Y"),
            fieldWithPath("value.seat_x").description("내 자리 X (그대로)"),
            fieldWithPath("value.seat_y").description("내 자리 Y (그대로)"),
            fieldWithPath("value.population").description("층 전체 인원"),
            fieldWithPath("value.visitor_no")
                    .type(JsonFieldType.NUMBER)
                    .description("방문객 이름 번호")
                    .optional(),
            fieldWithPath("value.auto_seat_continued").description("false"),
            fieldWithPath("value.queued").description("false"),
            fieldWithPath("value.queue").type(JsonFieldType.OBJECT).description("null").optional(),
            fieldWithPath("value.first_visit")
                    .type(JsonFieldType.BOOLEAN)
                    .description("null (방 이동은 입장이 아니다)")
                    .optional()
        };
    }

    @Test
    @DisplayName(
            "GET /api/v1/tower/floors/{n}/instances · POST /api/v1/tower/instances/{id}/move-in : 방"
                    + " 목록·방 이동 (REST Docs)")
    void listAndMove() throws Exception {
        when(roomService.list(eq(1), any(), eq("vk-1")))
                .thenReturn(
                        new TowerRoomListResponse(
                                1,
                                41L,
                                30,
                                List.of(
                                        new TowerRoomView(11L, 1, 8L, 9L, 30, false, true),
                                        new TowerRoomView(10L, 2, 30L, 32L, 30, true, false))));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                        "/api/v1/tower/floors/{floorNumber}/instances", 1)
                                .param("visitor_key", "vk-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.instances[0].instance_no").value(1))
                .andDo(
                        document(
                                "tower-floor-instances",
                                pathParameters(
                                        parameterWithName("floorNumber").description("층 번호")),
                                queryParameters(
                                        parameterWithName("visitor_key")
                                                .description(
                                                        "방문객 키 (비로그인만) — 그 층에 있어야 한다(아니면 403"
                                                                + " 150205)")
                                                .optional()),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.floor_number").description("층 번호"),
                                        fieldWithPath("value.population")
                                                .description("층 전체 인원 (모든 종류, NPC 제외) — 시트 제목"),
                                        fieldWithPath("value.capacity").description("방 정원"),
                                        fieldWithPath("value.instances[].instance_id")
                                                .description("방 ID"),
                                        fieldWithPath("value.instances[].instance_no")
                                                .description(
                                                        "방 번호 (층 안에서 1부터, 화면의 n번 방) — 이 순서로 내린다"),
                                        fieldWithPath("value.instances[].population")
                                                .description(
                                                        "정원에 세는 인원 (직접 입장·방문객, 자동 착석 제외) — capacity"
                                                                + " 와 견준다"),
                                        fieldWithPath("value.instances[].total_population")
                                                .description("그 방에 보이는 전체 (자동 착석 포함)"),
                                        fieldWithPath("value.instances[].capacity")
                                                .description("정원"),
                                        fieldWithPath("value.instances[].full")
                                                .description("가득 참 — 이동할 수 없다"),
                                        fieldWithPath("value.instances[].current")
                                                .description("내가 있는 방"))));

        when(roomService.moveIn(eq(12L), any(), eq("vk-1")))
                .thenReturn(
                        new TowerEnterResponse(
                                1, 12L, 77L, true, true, "vk-1", 1400, 1000, 1200, 900, 41L, 4821,
                                false, false, null, null));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/instances/{instanceId}/move-in", 12L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"visitor_key\":\"vk-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.instance_id").value(12))
                .andDo(
                        document(
                                "tower-instances-move-in",
                                pathParameters(
                                        parameterWithName("instanceId").description("옮겨 갈 방 ID")),
                                requestFields(
                                        fieldWithPath("visitor_key")
                                                .description("방문객 키 (비로그인만)")
                                                .optional()),
                                responseFields(enterFields())));
    }

    @Test
    @DisplayName(
            "방 이동 거절: 가득 참 409 150207 · 10초 안 429 150208(value.retry_after_seconds) · 다른 층 400"
                    + " 150209 · 없는 방 404 150210")
    void moveRejected() throws Exception {
        when(roomService.moveIn(eq(20L), any(), any())).thenThrow(TowerEntryException.roomFull());
        when(roomService.moveIn(eq(21L), any(), any()))
                .thenThrow(TowerEntryException.roomMoveTooSoon(6));
        when(roomService.moveIn(eq(22L), any(), any()))
                .thenThrow(TowerEntryException.roomOtherFloor());
        when(roomService.moveIn(eq(23L), any(), any()))
                .thenThrow(TowerEntryException.roomNotFound());
        when(roomService.list(anyInt(), any(), any()))
                .thenThrow(TowerEntryException.notInInstance());

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                "/api/v1/tower/instances/{id}/move-in", 20L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("150207"));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                "/api/v1/tower/instances/{id}/move-in", 21L))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("150208"))
                .andExpect(jsonPath("$.value.retry_after_seconds").value(6));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                "/api/v1/tower/instances/{id}/move-in", 22L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150209"));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                "/api/v1/tower/instances/{id}/move-in", 23L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("150210"));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                "/api/v1/tower/floors/{n}/instances", 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("150205"));
    }
}
