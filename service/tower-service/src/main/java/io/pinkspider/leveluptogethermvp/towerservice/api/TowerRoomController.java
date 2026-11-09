package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerRoomService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerEnterResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerRoomListResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerRoomMoveRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-738: 같은 층 안의 방 목록·방 이동. 비로그인(방문객)도 그 층에 있으면 부른다(SecurityConfig permitAll + {@code
 * SecurityConfigPublicEndpointTest}) — 본인 확인은 visitor_key, 판정은 서비스.
 */
@RestController
@RequestMapping("/api/v1/tower")
@RequiredArgsConstructor
public class TowerRoomController {

    private final TowerRoomService roomService;

    /** 방 목록 — 시트를 열어 둔 동안 프론트가 10초마다 다시 부른다 */
    @GetMapping("/floors/{floorNumber}/instances")
    public ApiResult<TowerRoomListResponse> list(
            @CurrentUser(required = false) String userId,
            @PathVariable int floorNumber,
            @RequestParam(value = "visitor_key", required = false) String visitorKey) {
        return ApiResult.<TowerRoomListResponse>builder()
                .value(roomService.list(floorNumber, userId, visitorKey))
                .build();
    }

    /** 방 이동 — 응답은 입장 응답과 같은 모양(새 instance_id 로 토픽을 바꿔 구독·join 하고 스냅샷을 다시 받는다) */
    @PostMapping("/instances/{instanceId}/move-in")
    public ApiResult<TowerEnterResponse> moveIn(
            @CurrentUser(required = false) String userId,
            @PathVariable Long instanceId,
            @RequestBody(required = false) @Valid TowerRoomMoveRequest body) {
        return ApiResult.<TowerEnterResponse>builder()
                .value(
                        roomService.moveIn(
                                instanceId, userId, body != null ? body.visitorKey() : null))
                .build();
    }
}
