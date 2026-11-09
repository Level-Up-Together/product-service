package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerPresenceQueryService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerPresenceSnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-631: 입장 스냅샷. 비로그인 허용 GET(방문객은 {@code visitor_key} 쿼리) — 그 방의 점유자가 아니면 403 {@code 150205}.
 * 클라이언트는 토픽 구독·join 뒤에 부른다(사이 이벤트는 upsert 로 흡수).
 */
@RestController
@RequestMapping("/api/v1/tower")
@RequiredArgsConstructor
public class TowerPresenceController {

    private final TowerPresenceQueryService presenceQueryService;

    @GetMapping("/instances/{instanceId}/presence")
    public ApiResult<TowerPresenceSnapshotResponse> snapshot(
            @CurrentUser(required = false) String userId,
            @PathVariable Long instanceId,
            @RequestParam(name = "visitor_key", required = false) String visitorKey) {
        return ApiResult.<TowerPresenceSnapshotResponse>builder()
                .value(presenceQueryService.snapshot(instanceId, userId, visitorKey))
                .build();
    }
}
