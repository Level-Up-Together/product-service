package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorReportService;
import io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorSanctionService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerAdminPage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorReportAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorSanctionRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorSanctionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-705: 탑 방문객 운영 내부 API (admin-service 패스스루·화면은 후속). 신고 목록(원문·IP·기기 ID) → 제재 등록(기기·IP 단위 채팅
 * 금지·입장 금지) → 해제.
 */
@RestController
@RequestMapping("/api/internal/tower")
@RequiredArgsConstructor
public class TowerVisitorAdminInternalController {
    private final TowerVisitorSanctionService sanctionService;
    private final TowerVisitorReportService reportService;

    @GetMapping("/visitor-reports")
    public ApiResult<TowerAdminPage<TowerVisitorReportAdminResponse>> reports(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<TowerAdminPage<TowerVisitorReportAdminResponse>>builder()
                .value(reportService.list(page, size))
                .build();
    }

    @GetMapping("/visitor-sanctions")
    public ApiResult<TowerAdminPage<TowerVisitorSanctionResponse>> sanctions(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<TowerAdminPage<TowerVisitorSanctionResponse>>builder()
                .value(sanctionService.list(page, size))
                .build();
    }

    @PostMapping("/visitor-sanctions")
    public ApiResult<TowerVisitorSanctionResponse> create(
            @Valid @RequestBody TowerVisitorSanctionRequest request) {
        return ApiResult.<TowerVisitorSanctionResponse>builder()
                .value(sanctionService.create(request))
                .build();
    }

    /** 해제(멱등) — 이력은 남는다 */
    @DeleteMapping("/visitor-sanctions/{sanctionId}")
    public ApiResult<TowerVisitorSanctionResponse> lift(@PathVariable Long sanctionId) {
        return ApiResult.<TowerVisitorSanctionResponse>builder()
                .value(sanctionService.lift(sanctionId))
                .build();
    }
}
