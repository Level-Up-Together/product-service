package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerEntryService;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerEnterResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerLeaveResponse;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TowerEntryController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TowerEntryController 테스트 (LUT-630)")
class TowerEntryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TowerEntryService entryService;

    @MockitoBean
    private io.pinkspider.leveluptogethermvp.towerservice.application.TowerSeatService seatService;

    @Test
    @DisplayName("POST /api/v1/tower/floors/{floorNumber}/enter : 입장·배정 (인스턴스 지정 파라미터 없음)")
    void enter() throws Exception {
        when(entryService.enter(any(), eq(2)))
                .thenReturn(
                        new TowerEnterResponse(
                                2, 10L, 77L, false, false, null, 1200, 900, 1200, 900, 12L));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                        "/api/v1/tower/floors/{floorNumber}/enter", 2)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.instance_id").value(10))
                .andExpect(jsonPath("$.value.resumed").value(false))
                .andDo(
                        document(
                                "tower-floors-enter",
                                pathParameters(
                                        parameterWithName("floorNumber").description("층 번호")),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.floor_number").description("입장한 층 번호"),
                                        fieldWithPath("value.instance_id")
                                                .description("배정된 인스턴스(방) ID"),
                                        fieldWithPath("value.occupant_id").description("점유자 ID"),
                                        fieldWithPath("value.resumed")
                                                .description("자리 유지 중이던 방(또는 자동 착석 자리)으로 복귀했는가"),
                                        fieldWithPath("value.mission_active")
                                                .description("미션 중(자리 유지) 여부"),
                                        fieldWithPath("value.visitor_key")
                                                .description("방문객(비로그인)에게만 — 퇴장 때 본인 확인용")
                                                .optional(),
                                        fieldWithPath("value.entry_point_x")
                                                .description("층 입장 지점 X (배경 원본 픽셀)"),
                                        fieldWithPath("value.entry_point_y")
                                                .description("층 입장 지점 Y"),
                                        fieldWithPath("value.seat_x").description("내 자리 X (입장 시점)"),
                                        fieldWithPath("value.seat_y").description("내 자리 Y"),
                                        fieldWithPath("value.population")
                                                .description(
                                                        "층 전체 인원 (모든 인스턴스, NPC 제외, 30초 메모)"))));
    }

    @Test
    @DisplayName(
            "비로그인 입장은 visitor_key 를 받는다 · 레벨 부족 403 150203 · 없는 층 404 150201 · 비허용 층 403 150202")
    void guestAndErrors() throws Exception {
        when(entryService.enter(any(), eq(1)))
                .thenReturn(
                        new TowerEnterResponse(
                                1, 10L, 78L, false, false, "k-1", 1400, 1000, 1400, 1000, 3L));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                "/api/v1/tower/floors/{floorNumber}/enter", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.visitor_key").value("k-1"));

        when(entryService.enter(any(), eq(3))).thenThrow(TowerEntryException.levelInsufficient());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                "/api/v1/tower/floors/{floorNumber}/enter", 3))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("150203"));

        when(entryService.enter(any(), eq(9))).thenThrow(TowerEntryException.floorNotAvailable());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                "/api/v1/tower/floors/{floorNumber}/enter", 9))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("150201"));

        when(entryService.enter(any(), eq(4))).thenThrow(TowerEntryException.loginRequired());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post(
                                "/api/v1/tower/floors/{floorNumber}/enter", 4))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("150202"));
    }

    @Test
    @DisplayName("POST /api/v1/tower/leave : 퇴장 (방문객은 visitor_key, 로그인 유저는 본문 없이)")
    void leave() throws Exception {
        when(entryService.leave(any(), eq("k-1"))).thenReturn(new TowerLeaveResponse(true, false));
        when(entryService.leave(any(), isNull())).thenReturn(new TowerLeaveResponse(false, true));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/v1/tower/leave")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"visitor_key\":\"k-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.left").value(true))
                .andDo(
                        document(
                                "tower-leave",
                                requestFields(
                                        fieldWithPath("visitor_key")
                                                .description("방문객 키 (입장 응답, 비로그인만 — 로그인 유저는 본문 생략)")
                                                .optional()),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.left").description("자리를 비웠는가"),
                                        fieldWithPath("value.seat_held")
                                                .description("미션 중이라 자리를 남겼는가 (돌아오면 같은 방으로 복귀)"))));

        mockMvc.perform(RestDocumentationRequestBuilders.post("/api/v1/tower/leave"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.seat_held").value(true));
        verify(entryService).leave(any(), isNull());
    }

    @Test
    @DisplayName("방문객 키 없는 비로그인 퇴장은 400 150204")
    void leaveWithoutKey() throws Exception {
        when(entryService.leave(any(), isNull()))
                .thenThrow(TowerEntryException.visitorKeyRequired());
        mockMvc.perform(RestDocumentationRequestBuilders.post("/api/v1/tower/leave"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150204"));
    }

    @Test
    @DisplayName("POST /api/v1/tower/vacate : 자리 직접 비우기 — 미션은 계속, 아바타만 퇴장 (LUT-632/713)")
    void vacate() throws Exception {
        when(seatService.vacate(any())).thenReturn(true);

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/v1/tower/vacate")
                                .with(user("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.left").value(true))
                .andExpect(jsonPath("$.value.seat_held").value(false))
                .andDo(
                        document(
                                "tower-vacate",
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.left")
                                                .description("자리를 비웠는가 (탑에 없었으면 false)"),
                                        fieldWithPath("value.seat_held")
                                                .description("항상 false — 미션 중이어도 자리를 남기지 않는다"))));
    }
}
