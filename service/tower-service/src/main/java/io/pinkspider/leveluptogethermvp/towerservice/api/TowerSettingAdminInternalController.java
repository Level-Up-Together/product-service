package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerSettingService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerSettingsRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerSettingsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** LUT-635: 탑 공통 설정 내부 API (admin-service 패스스루). 동시 접속 상한은 null = 제한 없음. */
@RestController
@RequestMapping("/api/internal/tower/settings")
@RequiredArgsConstructor
public class TowerSettingAdminInternalController {
    private final TowerSettingService towerSettingService;

    @GetMapping
    public ApiResult<TowerSettingsResponse> get() {
        return ApiResult.<TowerSettingsResponse>builder().value(towerSettingService.get()).build();
    }

    @PutMapping
    public ApiResult<TowerSettingsResponse> update(
            @Valid @RequestBody TowerSettingsRequest request) {
        return ApiResult.<TowerSettingsResponse>builder()
                .value(towerSettingService.update(request))
                .build();
    }
}
