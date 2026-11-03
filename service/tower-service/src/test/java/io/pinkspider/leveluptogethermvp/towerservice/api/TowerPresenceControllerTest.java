package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerPresenceQueryService;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerPresenceSnapshotResponse;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerAvatarItem;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceView;
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
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TowerPresenceController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("TowerPresenceController 테스트 (LUT-631)")
class TowerPresenceControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TowerPresenceQueryService queryService;

    @Test
    @DisplayName("GET /api/v1/tower/instances/{instanceId}/presence : 입장 스냅샷 (방문객은 visitor_key)")
    void snapshot() throws Exception {
        when(queryService.snapshot(eq(9L), any(), eq("k-1")))
                .thenReturn(
                        new TowerPresenceSnapshotResponse(
                                9L,
                                1,
                                8L,
                                List.of(
                                        new TowerPresenceView(
                                                7L,
                                                "user-1",
                                                "DIRECT",
                                                null,
                                                "닉네임",
                                                "https://cdn/p.jpg",
                                                12,
                                                List.of(
                                                        new TowerAvatarItem(
                                                                "BASIC",
                                                                "https://cdn/wing.png",
                                                                "BACK",
                                                                null,
                                                                "RARE")),
                                                1400,
                                                1000,
                                                "MISSION"),
                                        new TowerPresenceView(
                                                8L, null, "VISITOR", 4821, null, null, null,
                                                List.of(), 1380, 1010, "REST"))));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                        "/api/v1/tower/instances/{instanceId}/presence", 9L)
                                .queryParam("visitor_key", "k-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.me").value(8))
                .andExpect(jsonPath("$.value.occupants[0].level").value(12))
                .andExpect(jsonPath("$.value.occupants[1].visitor_no").value(4821))
                .andDo(
                        document(
                                "tower-instance-presence",
                                pathParameters(
                                        parameterWithName("instanceId")
                                                .description("인스턴스(방) ID — 입장 응답 instance_id")),
                                queryParameters(
                                        parameterWithName("visitor_key")
                                                .description("방문객 키 (비로그인만)")
                                                .optional()),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value.instance_id").description("인스턴스 ID"),
                                        fieldWithPath("value.floor_number").description("층 번호"),
                                        fieldWithPath("value.me").description("요청자의 점유자 ID"),
                                        fieldWithPath("value.occupants[].occupant_id")
                                                .description("점유자 ID (이동·퇴장 이벤트의 키)"),
                                        fieldWithPath("value.occupants[].user_id")
                                                .description("유저 ID (방문객은 없음)")
                                                .optional(),
                                        fieldWithPath("value.occupants[].kind")
                                                .description("DIRECT / VISITOR / AUTO_SEATED"),
                                        fieldWithPath("value.occupants[].visitor_no")
                                                .description(
                                                        "방문객 번호 — 클라이언트가 locale 로 '방문객 1234' 조립")
                                                .optional(),
                                        fieldWithPath("value.occupants[].nickname")
                                                .description("닉네임 (방문객은 없음)")
                                                .optional(),
                                        fieldWithPath("value.occupants[].picture")
                                                .description("프로필 썸네일 URL")
                                                .optional(),
                                        fieldWithPath("value.occupants[].level")
                                                .description("레벨 (방문객은 없음, LEVEL 이벤트로 갱신)")
                                                .optional(),
                                        fieldWithPath("value.occupants[].items")
                                                .description("장착 아이템 목록 (방문객은 빈 배열)"),
                                        fieldWithPath("value.occupants[].items[].item_type")
                                                .description(
                                                        "장착 아이템 종류 (BASIC/FULL/HEAD/EFFECT/ETC)"),
                                        fieldWithPath("value.occupants[].items[].image_url")
                                                .description("아이템 이미지"),
                                        fieldWithPath("value.occupants[].items[].image_position")
                                                .description("FRONT/BACK")
                                                .optional(),
                                        fieldWithPath("value.occupants[].items[].effect_code")
                                                .description("이펙트 코드")
                                                .optional(),
                                        fieldWithPath("value.occupants[].items[].rarity")
                                                .description("희귀도")
                                                .optional(),
                                        fieldWithPath("value.occupants[].x")
                                                .description("위치 X (배경 원본 픽셀, 썸네일 맨 아래 중앙)"),
                                        fieldWithPath("value.occupants[].y").description("위치 Y"),
                                        fieldWithPath("value.occupants[].state")
                                                .description("MISSION / REST"))));
    }

    @Test
    @DisplayName("그 방의 점유자가 아니면 403 150205")
    void notInInstance() throws Exception {
        when(queryService.snapshot(eq(10L), any(), any()))
                .thenThrow(TowerEntryException.notInInstance());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                "/api/v1/tower/instances/{instanceId}/presence", 10L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("150205"));
    }
}
