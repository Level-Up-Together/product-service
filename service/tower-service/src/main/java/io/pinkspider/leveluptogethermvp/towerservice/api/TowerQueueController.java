package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.queue.TowerQueueService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerQueueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-701: 탑 입장 대기 — 대기 화면이 주기적으로(몇 초마다) 상태를 조회한다(순번·예상 대기·층 인원, 자리가 나면 ADMITTED). 조회가 끊기면 화면을 벗어난
 * 것으로 보고 줄에서 뺀다. 비로그인 허용(방문객은 표가 본인 확인), 로그인 표는 주인만. 없거나 끝난 표는 404 150206.
 */
@RestController
@RequestMapping("/api/v1/tower/queue")
@RequiredArgsConstructor
public class TowerQueueController {

    private final TowerQueueService towerQueueService;

    @GetMapping("/{ticket}")
    public ApiResult<TowerQueueStatus> status(
            @CurrentUser(required = false) String userId, @PathVariable String ticket) {
        return ApiResult.<TowerQueueStatus>builder()
                .value(towerQueueService.status(ticket, userId))
                .build();
    }

    /** 대기 그만하기 — 즉시 줄에서 뺀다(없어도 성공) */
    @DeleteMapping("/{ticket}")
    public ApiResult<Void> leave(
            @CurrentUser(required = false) String userId, @PathVariable String ticket) {
        towerQueueService.leave(ticket, userId);
        return ApiResult.<Void>builder().build();
    }
}
