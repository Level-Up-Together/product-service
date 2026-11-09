package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.web.ClientIpResolver;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerNpcEntryService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryRandomResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryReportRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryReportResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-760: NPC 카드 입력 받기 — 글 남기기·공개 조회·신고. 세 경로 모두 비로그인 허용(방문객은 {@code visitor_key}). 로그인 전용 NPC 에
 * 방문객이 제출하면 403 {@code 150411}, 하루 1회 초과 429 {@code 150414}, 입력을 받지 않는 NPC 400 {@code 150410}.
 */
@RestController
@RequestMapping("/api/v1/tower/npcs")
@RequiredArgsConstructor
public class TowerNpcEntryController {

    private final TowerNpcEntryService entryService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/{npcId}/entries")
    public ApiResult<TowerNpcEntryView> submit(
            @CurrentUser(required = false) String userId,
            @PathVariable Long npcId,
            @Valid @RequestBody TowerNpcEntryRequest request,
            HttpServletRequest httpRequest) {
        return ApiResult.<TowerNpcEntryView>builder()
                .value(
                        entryService.submit(
                                npcId, userId, request, clientIpResolver.resolve(httpRequest)))
                .build();
    }

    /** 덜 보여 준 글부터 최대 50개. 다 보면 받은 ID 를 {@code exclude}(쉼표 구분) 로 넘겨 더 받는다 */
    @GetMapping("/{npcId}/entries/random")
    public ApiResult<TowerNpcEntryRandomResponse> random(
            @PathVariable Long npcId,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit,
            @RequestParam(name = "exclude", required = false) List<Long> exclude) {
        return ApiResult.<TowerNpcEntryRandomResponse>builder()
                .value(entryService.random(npcId, limit, exclude))
                .build();
    }

    @PostMapping("/entries/{entryId}/reports")
    public ApiResult<TowerNpcEntryReportResponse> report(
            @CurrentUser(required = false) String userId,
            @PathVariable Long entryId,
            @Valid @RequestBody(required = false) TowerNpcEntryReportRequest request) {
        return ApiResult.<TowerNpcEntryReportResponse>builder()
                .value(
                        entryService.report(
                                entryId, userId, request != null ? request.visitorKey() : null))
                .build();
    }
}
