package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerFloorAdminService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminResponse;
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

/** LUT-629: 어드민 층 CRUD 내부 API (admin-service 패스스루, X-Internal-Api-Key). 저장 거부는 400 + 1501xx. */
@RestController
@RequestMapping("/api/internal/tower/floors")
@RequiredArgsConstructor
public class TowerFloorAdminInternalController {

    private final TowerFloorAdminService towerFloorAdminService;

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
}
