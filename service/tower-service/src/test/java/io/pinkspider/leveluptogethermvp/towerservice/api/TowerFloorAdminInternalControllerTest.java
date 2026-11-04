package io.pinkspider.leveluptogethermvp.towerservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerFloorAdminService;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminResponse;
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
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TowerFloorAdminInternalController.class,
        excludeAutoConfiguration = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class
        })
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("TowerFloorAdminInternalController 테스트 (LUT-629)")
class TowerFloorAdminInternalControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TowerFloorAdminService adminService;

    @MockitoBean
    private io.pinkspider.leveluptogethermvp.towerservice.application.TowerFloorImageService
            imageService;

    private static final String BODY =
            """
{"floor_number":2,"name":"등나무 정원","name_en":"Wisteria Garden","name_ja":"藤の庭","name_ar":"حديقة الوستارية",
 "entry_level":5,"guest_allowed":false,"background_image_url":"https://cdn/bg2.jpg","floor_mask_url":"https://cdn/mask2.png",
 "entry_point_x":1200,"entry_point_y":900,"bgm_url":null,"instance_capacity":30,"is_public":true}
""";

    private static TowerFloorAdminResponse response() {
        return new TowerFloorAdminResponse(
                2L,
                2,
                "등나무 정원",
                "Wisteria Garden",
                "藤の庭",
                "حديقة الوستارية",
                5,
                false,
                "https://cdn/bg2.jpg",
                "https://cdn/mask2.png",
                1200,
                900,
                null,
                30,
                true,
                LocalDateTime.of(2026, 11, 3, 15, 0),
                LocalDateTime.of(2026, 11, 3, 15, 0));
    }

    private static FieldDescriptor[] floorFields(String prefix) {
        return new FieldDescriptor[] {
            fieldWithPath(prefix + "id").description("층 ID"),
            fieldWithPath(prefix + "floor_number").description("층 번호 (1부터, 유일)"),
            fieldWithPath(prefix + "name").description("층 이름(ko)"),
            fieldWithPath(prefix + "name_en").description("층 이름(en)"),
            fieldWithPath(prefix + "name_ja").description("층 이름(ja)"),
            fieldWithPath(prefix + "name_ar").description("층 이름(ar)"),
            fieldWithPath(prefix + "entry_level").description("입장 레벨 (null = 제한 없음)").optional(),
            fieldWithPath(prefix + "guest_allowed").description("비로그인 입장 허용"),
            fieldWithPath(prefix + "background_image_url").description("배경 이미지 URL"),
            fieldWithPath(prefix + "floor_mask_url").description("바닥 마스크 PNG URL (붉은색 = 이동 가능)"),
            fieldWithPath(prefix + "entry_point_x").description("입장 지점 X (배경 원본 픽셀)"),
            fieldWithPath(prefix + "entry_point_y").description("입장 지점 Y (배경 원본 픽셀)"),
            fieldWithPath(prefix + "bgm_url").description("배경 음악 URL").optional(),
            fieldWithPath(prefix + "instance_capacity").description("인스턴스(방) 상한"),
            fieldWithPath(prefix + "is_public").description("공개 여부"),
            fieldWithPath(prefix + "created_at").description("생성 시각"),
            fieldWithPath(prefix + "modified_at").description("수정 시각")
        };
    }

    private static FieldDescriptor[] withEnvelope(FieldDescriptor[] fields) {
        FieldDescriptor[] all = new FieldDescriptor[fields.length + 2];
        all[0] = fieldWithPath("code").description("응답 코드");
        all[1] = fieldWithPath("message").description("응답 메시지");
        System.arraycopy(fields, 0, all, 2, fields.length);
        return all;
    }

    @Test
    @DisplayName("GET /api/internal/tower/floors : 층 번호 순 전체 목록")
    void list() throws Exception {
        when(adminService.list()).thenReturn(List.of(response()));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get("/api/internal/tower/floors")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value[0].floor_number").value(2))
                .andExpect(jsonPath("$.value[0].entry_level").value(5))
                .andDo(
                        document(
                                "internal-tower-floors-list",
                                responseFields(withEnvelope(floorFields("value[].")))));
    }

    @Test
    @DisplayName("POST /api/internal/tower/floors : 등록")
    void create() throws Exception {
        when(adminService.create(any(TowerFloorAdminRequest.class))).thenReturn(response());

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/internal/tower/floors")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.id").value(2))
                .andDo(
                        document(
                                "internal-tower-floors-create",
                                requestFields(
                                        fieldWithPath("floor_number")
                                                .description("층 번호 (1 이상, 유일)"),
                                        fieldWithPath("name").description("층 이름(ko)"),
                                        fieldWithPath("name_en").description("층 이름(en)"),
                                        fieldWithPath("name_ja").description("층 이름(ja)"),
                                        fieldWithPath("name_ar").description("층 이름(ar)"),
                                        fieldWithPath("entry_level")
                                                .description("입장 레벨 (비우면 제한 없음, 1층은 비워야 함)")
                                                .optional(),
                                        fieldWithPath("guest_allowed")
                                                .description("비로그인 입장 허용 (입장 레벨이 없는 층만)"),
                                        fieldWithPath("background_image_url")
                                                .description("배경 이미지 URL"),
                                        fieldWithPath("floor_mask_url")
                                                .description("바닥 마스크 PNG URL"),
                                        fieldWithPath("entry_point_x")
                                                .description("입장 지점 X (마스크 안이어야 함)"),
                                        fieldWithPath("entry_point_y").description("입장 지점 Y"),
                                        fieldWithPath("bgm_url")
                                                .description("배경 음악 URL")
                                                .optional(),
                                        fieldWithPath("instance_capacity")
                                                .description("인스턴스 상한 (비우면 30)")
                                                .optional(),
                                        fieldWithPath("is_public").description("공개 여부")),
                                responseFields(withEnvelope(floorFields("value.")))));
    }

    @Test
    @DisplayName("POST : 저장 규칙 위반은 400 + 1501xx (TowerExceptionHandler), @Valid 위반은 400")
    void createRejected() throws Exception {
        when(adminService.create(any(TowerFloorAdminRequest.class)))
                .thenThrow(TowerFloorException.entryLevelOrder());

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/internal/tower/floors")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150103"));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/internal/tower/floors")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY.replace("\"floor_number\":2", "\"floor_number\":0")))
                .andExpect(status().isBadRequest());

        // SSRF 방어: 이미지·마스크 URL 은 https 만 (서버가 읽는 URL)
        mockMvc.perform(
                        RestDocumentationRequestBuilders.post("/api/internal/tower/floors")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        BODY.replace(
                                                "https://cdn/mask2.png",
                                                "http://10.0.1.236:10085/x.png")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/internal/tower/floors/{id} : 수정 · GET 없음 404 · DELETE")
    void updateGetDelete() throws Exception {
        when(adminService.update(eq(2L), any(TowerFloorAdminRequest.class))).thenReturn(response());
        when(adminService.get(99L)).thenThrow(TowerFloorException.notFound());

        mockMvc.perform(
                        RestDocumentationRequestBuilders.put("/api/internal/tower/floors/{id}", 2L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.floor_number").value(2))
                .andDo(
                        document(
                                "internal-tower-floors-update",
                                pathParameters(parameterWithName("id").description("층 ID")),
                                responseFields(withEnvelope(floorFields("value.")))));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.get(
                                "/api/internal/tower/floors/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("150101"));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.delete(
                                "/api/internal/tower/floors/{id}", 2L))
                .andExpect(status().isOk())
                .andDo(
                        document(
                                "internal-tower-floors-delete",
                                pathParameters(parameterWithName("id").description("층 ID"))));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("POST /api/internal/tower/floors/images?kind=MASK : 이미지 업로드 (LUT-635)")
    void uploadImage() throws Exception {
        org.mockito.Mockito.when(
                        imageService.upload(
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.eq(
                                        io.pinkspider.leveluptogethermvp.towerservice.domain.enums
                                                .TowerImageKind.MASK)))
                .thenReturn(
                        new io.pinkspider.leveluptogethermvp.towerservice.domain.dto
                                .TowerFloorImageUploadResponse(
                                io.pinkspider.leveluptogethermvp.towerservice.domain.enums
                                        .TowerImageKind.MASK,
                                "https://dev.level-up-together.com:8443/uploads/tower/mask/m.png",
                                704,
                                384));

        mockMvc.perform(
                        RestDocumentationRequestBuilders.multipart(
                                        "/api/internal/tower/floors/images?kind=mask")
                                .file(
                                        new org.springframework.mock.web.MockMultipartFile(
                                                "file",
                                                "mask.png",
                                                "image/png",
                                                new byte[] {1, 2, 3}))
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.kind").value("MASK"))
                .andExpect(jsonPath("$.value.width").value(704))
                .andDo(
                        document(
                                "internal-tower-floors-image-upload",
                                org.springframework.restdocs.request.RequestDocumentation
                                        .queryParameters(
                                                org.springframework.restdocs.request
                                                        .RequestDocumentation.parameterWithName(
                                                                "kind")
                                                        .description(
                                                                "BACKGROUND | MASK (대소문자 무관). 마스크는"
                                                                        + " PNG 만")),
                                org.springframework.restdocs.request.RequestDocumentation
                                        .requestParts(
                                                org.springframework.restdocs.request
                                                        .RequestDocumentation.partWithName("file")
                                                        .description(
                                                                "이미지 파일 (jpg·png·webp, 15MB 이하)")),
                                responseFields(
                                        fieldWithPath("code").description("응답 코드"),
                                        fieldWithPath("message").description("응답 메시지"),
                                        fieldWithPath("value").description("업로드 결과"),
                                        fieldWithPath("value.kind").description("이미지 종류"),
                                        fieldWithPath("value.url")
                                                .description(
                                                        "절대 URL — 등록/수정의"
                                                            + " background_image_url·floor_mask_url"
                                                            + " 에 그대로"),
                                        fieldWithPath("value.width")
                                                .description("가로 픽셀 (읽을 수 없으면 null)")
                                                .optional(),
                                        fieldWithPath("value.height")
                                                .description("세로 픽셀 (읽을 수 없으면 null)")
                                                .optional())));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("POST /images : kind 없음·모르는 값은 400 150110, 서비스 거절(PNG 아님)은 400 150112")
    void uploadImageRejected() throws Exception {
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "mask.jpg", "image/jpeg", new byte[] {1});
        mockMvc.perform(
                        RestDocumentationRequestBuilders.multipart(
                                        "/api/internal/tower/floors/images")
                                .file(file)
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150110"));
        mockMvc.perform(
                        RestDocumentationRequestBuilders.multipart(
                                        "/api/internal/tower/floors/images?kind=sticker")
                                .file(file)
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150110"));

        org.mockito.Mockito.when(
                        imageService.upload(
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.eq(
                                        io.pinkspider.leveluptogethermvp.towerservice.domain.enums
                                                .TowerImageKind.MASK)))
                .thenThrow(
                        io.pinkspider.leveluptogethermvp.towerservice.application.exception
                                .TowerFloorException.maskNotPng());
        mockMvc.perform(
                        RestDocumentationRequestBuilders.multipart(
                                        "/api/internal/tower/floors/images?kind=MASK")
                                .file(file)
                                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("150112"));
    }
}
