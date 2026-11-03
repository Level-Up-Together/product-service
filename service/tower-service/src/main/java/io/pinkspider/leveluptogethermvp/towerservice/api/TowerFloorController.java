package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerFloorQueryService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-629: 유저용 층 목록. 비로그인도 호출할 수 있다(browse-first — SecurityConfig permitAll GET + {@code
 * SecurityConfigPublicEndpointTest}). 방문객에게는 비로그인 허용 층만 열리고 나머지는 잠금으로 내려간다.
 */
@RestController
@RequestMapping("/api/v1/tower")
@RequiredArgsConstructor
public class TowerFloorController {

    private final TowerFloorQueryService towerFloorQueryService;

    @GetMapping("/floors")
    public ApiResult<TowerFloorListResponse> floors(@CurrentUser(required = false) String userId) {
        return ApiResult.<TowerFloorListResponse>builder()
                .value(towerFloorQueryService.listForUser(userId))
                .build();
    }
}
