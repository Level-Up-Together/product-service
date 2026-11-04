package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerFloorGuideService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorGuideAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorGuideAdminResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** LUT-734: 층별 첫 방문 안내 어드민 내부 API (admin-service 패스스루). 저장 거부는 400 + 1506xx, 층 없음 404 150101. */
@RestController
@RequestMapping("/api/internal/tower/floors/{floorId}/guide")
@RequiredArgsConstructor
public class TowerFloorGuideAdminInternalController {

    private final TowerFloorGuideService guideService;

    @GetMapping
    public ApiResult<TowerFloorGuideAdminResponse> get(@PathVariable Long floorId) {
        return ApiResult.<TowerFloorGuideAdminResponse>builder()
                .value(guideService.get(floorId))
                .build();
    }

    @PutMapping
    public ApiResult<TowerFloorGuideAdminResponse> save(
            @PathVariable Long floorId, @Valid @RequestBody TowerFloorGuideAdminRequest request) {
        return ApiResult.<TowerFloorGuideAdminResponse>builder()
                .value(guideService.save(floorId, request))
                .build();
    }
}
