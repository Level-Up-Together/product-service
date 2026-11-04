package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerEntryService;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerSeatService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerEnterResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerLeaveRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerLeaveResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-630: 탑 입장·퇴장. 비로그인도 호출할 수 있다(SecurityConfig permitAll POST + {@code
 * SecurityConfigPublicEndpointTest}) — 허용 층 판정은 서비스가 한다. 입장 요청에 인스턴스를 지정하는 파라미터는 없다(처음 진입은 무조건 자동
 * 배정).
 */
@RestController
@RequestMapping("/api/v1/tower")
@RequiredArgsConstructor
public class TowerEntryController {

    private final TowerEntryService towerEntryService;
    private final TowerSeatService towerSeatService;

    /**
     * LUT-701: 탑 전체 동시 접속 상한에 닿으면 배정 대신 {@code queued=true} + 순번을 돌려준다. 입장 가능 신호를 받은 뒤에는 {@code
     * queue_ticket} 을 붙여 다시 요청한다.
     */
    @PostMapping("/floors/{floorNumber}/enter")
    public ApiResult<TowerEnterResponse> enter(
            @CurrentUser(required = false) String userId,
            @PathVariable int floorNumber,
            @RequestParam(value = "queue_ticket", required = false) String queueTicket) {
        return ApiResult.<TowerEnterResponse>builder()
                .value(towerEntryService.enter(userId, floorNumber, queueTicket))
                .build();
    }

    /** LUT-632/713: 자리 직접 비우기 — 미션은 계속 진행되고 아바타만 퇴장(자동 착석 자리 포함). 로그인 필요 */
    @PostMapping("/vacate")
    public ApiResult<TowerLeaveResponse> vacate(@CurrentUser String userId) {
        boolean left = towerSeatService.vacate(userId);
        return ApiResult.<TowerLeaveResponse>builder()
                .value(new TowerLeaveResponse(left, false))
                .build();
    }

    @PostMapping("/leave")
    public ApiResult<TowerLeaveResponse> leave(
            @CurrentUser(required = false) String userId,
            @RequestBody(required = false) @Valid TowerLeaveRequest request) {
        String visitorKey = request != null ? request.visitorKey() : null;
        return ApiResult.<TowerLeaveResponse>builder()
                .value(towerEntryService.leave(userId, visitorKey))
                .build();
    }
}
