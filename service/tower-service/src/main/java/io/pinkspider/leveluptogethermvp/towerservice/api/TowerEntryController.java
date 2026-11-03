package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerEntryService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerEnterResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerLeaveRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerLeaveResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PostMapping("/floors/{floorNumber}/enter")
    public ApiResult<TowerEnterResponse> enter(
            @CurrentUser(required = false) String userId, @PathVariable int floorNumber) {
        return ApiResult.<TowerEnterResponse>builder()
                .value(towerEntryService.enter(userId, floorNumber))
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
