package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorReportService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorReportRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorReportResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-705: 방문객 신고(로그인 필요) — 계정이 없는 방문객은 기존 유저 신고(admin 신고 처리)에 올릴 수 없어 탑이 따로 받는다. 원문·기기 정보는 운영
 * 전용({@code /api/internal/tower/visitor-reports}).
 */
@RestController
@RequestMapping("/api/v1/tower")
@RequiredArgsConstructor
public class TowerVisitorReportController {
    private final TowerVisitorReportService reportService;

    @PostMapping("/instances/{instanceId}/visitor-reports")
    public ApiResult<TowerVisitorReportResponse> report(
            @CurrentUser String userId,
            @PathVariable Long instanceId,
            @Valid @RequestBody TowerVisitorReportRequest request) {
        return ApiResult.<TowerVisitorReportResponse>builder()
                .value(reportService.report(userId, instanceId, request))
                .build();
    }
}
