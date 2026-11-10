package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerNpcAdminService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcAdminResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-693: 어드민 NPC CRUD 내부 API (admin-service 패스스루 LUT-694, X-Internal-Api-Key). 층마다 등록, 저장 거부는 400
 * + 1504xx(층 없음 404 150101, NPC 없음 404 150401). 프로필 이미지는 층 이미지 업로드 API {@code kind=NPC} 로 올린다.
 */
@RestController
@RequestMapping("/api/internal/tower/floors/{floorId}/npcs")
@RequiredArgsConstructor
public class TowerNpcAdminInternalController {

    private final TowerNpcAdminService towerNpcAdminService;

    @GetMapping
    public ApiResult<List<TowerNpcAdminResponse>> list(@PathVariable Long floorId) {
        return ApiResult.<List<TowerNpcAdminResponse>>builder()
                .value(towerNpcAdminService.list(floorId))
                .build();
    }

    @PostMapping
    public ApiResult<TowerNpcAdminResponse> create(
            @PathVariable Long floorId, @Valid @RequestBody TowerNpcAdminRequest request) {
        return ApiResult.<TowerNpcAdminResponse>builder()
                .value(towerNpcAdminService.create(floorId, request))
                .build();
    }

    @PutMapping("/{npcId}")
    public ApiResult<TowerNpcAdminResponse> update(
            @PathVariable Long floorId,
            @PathVariable Long npcId,
            @Valid @RequestBody TowerNpcAdminRequest request) {
        return ApiResult.<TowerNpcAdminResponse>builder()
                .value(towerNpcAdminService.update(floorId, npcId, request))
                .build();
    }

    @DeleteMapping("/{npcId}")
    public ApiResult<Void> delete(@PathVariable Long floorId, @PathVariable Long npcId) {
        towerNpcAdminService.delete(floorId, npcId);
        return ApiResult.<Void>builder().build();
    }
}
