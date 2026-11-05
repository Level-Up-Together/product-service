package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerSettingService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerSettingsRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerSettingsResponse;
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
        controllers = TowerSettingAdminInternalController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("TowerSettingAdminInternalController 테스트 (LUT-635)")
class TowerSettingAdminInternalControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private TowerSettingService settingService;

    @Test
    @DisplayName("GET /api/internal/tower/settings : 동시 접속 상한 (null = 제한 없음)")
    void get() throws Exception {
        when(settingService.get()).thenReturn(new TowerSettingsResponse(300, 100));

        mockMvc.perform(RestDocumentationRequestBuilders.get("/api/internal/tower/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.max_concurrent_users").value(300))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-settings-get",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description(
                                                "탑 공통 설정 조회 (LUT-635) — 동시 접속 상한(null = 제한 없음)·NPC"
                                                        + " 노출 비율(LUT-693)"),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value").description("탑 공통 설정"),
                                        fieldWithPath("value.max_concurrent_users")
                                                .description(
                                                        "탑 전체 동시 접속 상한 (null = 제한 없음, 대기열 LUT-701"
                                                                + " 기준)")
                                                .optional(),
                                        fieldWithPath("value.npc_exposure_percent")
                                                .description(
                                                        "NPC 를 보여 줄 유저 비율 0~100 (A/B, 기본 100 —"
                                                                + " LUT-693)"))));
    }

    @Test
    @DisplayName("PUT /api/internal/tower/settings : 저장 즉시 반영, 0 이하는 400")
    void update() throws Exception {
        when(settingService.update(any(TowerSettingsRequest.class)))
                .thenReturn(new TowerSettingsResponse(500, 40));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.put("/api/internal/tower/settings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"max_concurrent_users\":500,\"npc_exposure_percent\":40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.max_concurrent_users").value(500))
                .andExpect(jsonPath("$.value.npc_exposure_percent").value(40))
                .andDo(
                        MockMvcRestDocumentationWrapper.document(
                                "internal-tower-settings-update",
                                ResourceSnippetParameters.builder()
                                        .tag("Tower - Admin Internal")
                                        .description(
                                                "탑 공통 설정 저장 (LUT-635/693) — 저장 즉시 반영, null 인 항목은"
                                                        + " 바꾸지 않는다"),
                                requestFields(
                                        fieldWithPath("max_concurrent_users")
                                                .description("탑 전체 동시 접속 상한 (1 이상, null 이면 제한 없음)")
                                                .optional(),
                                        fieldWithPath("npc_exposure_percent")
                                                .description(
                                                        "NPC 노출 유저 비율 0~100 (LUT-693, null 이면 바꾸지"
                                                                + " 않음)")
                                                .optional()),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value").description("탑 공통 설정"),
                                        fieldWithPath("value.max_concurrent_users")
                                                .description("저장된 값")
                                                .optional(),
                                        fieldWithPath("value.npc_exposure_percent")
                                                .description("저장된 NPC 노출 비율"))));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.put("/api/internal/tower/settings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"max_concurrent_users\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.put("/api/internal/tower/settings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"npc_exposure_percent\":101}"))
                .andExpect(status().isBadRequest());
    }
}
