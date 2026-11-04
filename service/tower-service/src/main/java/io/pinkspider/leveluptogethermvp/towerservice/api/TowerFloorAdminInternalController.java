package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerFloorAdminService;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerFloorAudioService;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerFloorImageService;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAudioUploadResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorImageUploadResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerImageKind;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** LUT-629: 어드민 층 CRUD 내부 API (admin-service 패스스루, X-Internal-Api-Key). 저장 거부는 400 + 1501xx. */
@RestController
@RequestMapping("/api/internal/tower/floors")
@RequiredArgsConstructor
public class TowerFloorAdminInternalController {

    private final TowerFloorAdminService towerFloorAdminService;
    private final TowerFloorImageService towerFloorImageService;
    private final TowerFloorAudioService towerFloorAudioService;

    @GetMapping
    public ApiResult<List<TowerFloorAdminResponse>> list() {
        return ApiResult.<List<TowerFloorAdminResponse>>builder()
                .value(towerFloorAdminService.list())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResult<TowerFloorAdminResponse> get(@PathVariable Long id) {
        return ApiResult.<TowerFloorAdminResponse>builder()
                .value(towerFloorAdminService.get(id))
                .build();
    }

    @PostMapping
    public ApiResult<TowerFloorAdminResponse> create(
            @Valid @RequestBody TowerFloorAdminRequest request) {
        return ApiResult.<TowerFloorAdminResponse>builder()
                .value(towerFloorAdminService.create(request))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResult<TowerFloorAdminResponse> update(
            @PathVariable Long id, @Valid @RequestBody TowerFloorAdminRequest request) {
        return ApiResult.<TowerFloorAdminResponse>builder()
                .value(towerFloorAdminService.update(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        towerFloorAdminService.delete(id);
        return ApiResult.<Void>builder().build();
    }

    /**
     * LUT-635: 층 이미지 업로드 — kind=BACKGROUND|MASK(대소문자 무관). 응답 url 을 등록/수정 본문에 쓴다. kind 가 없거나 모르는 값이면
     * 400 150110 (Spring 바인딩 오류는 플랫폼 핸들러가 500 으로 바꾸므로 직접 판정).
     */
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<TowerFloorImageUploadResponse> uploadImage(
            @RequestParam(value = "kind", required = false) String kind,
            @RequestPart("file") MultipartFile file) {
        TowerImageKind imageKind =
                TowerImageKind.parse(kind)
                        .filter(k -> k != TowerImageKind.BGM) // 배경 음악은 /audio 로
                        .orElseThrow(TowerFloorException::imageInvalid);
        return ApiResult.<TowerFloorImageUploadResponse>builder()
                .value(towerFloorImageService.upload(file, imageKind))
                .build();
    }

    /**
     * LUT-637: 배경 음악 업로드 — mp3·aac·m4a, 10MB 이하(150113). 응답 url 을 등록/수정 본문의 bgm_url 에 쓴다. 공개 층은
     * bgm_url 이 필수(150114).
     */
    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<TowerFloorAudioUploadResponse> uploadAudio(
            @RequestPart("file") MultipartFile file) {
        return ApiResult.<TowerFloorAudioUploadResponse>builder()
                .value(towerFloorAudioService.upload(file))
                .build();
    }
}
