package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.web.ClientIpResolver;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerChatService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerChatHistoryResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerChatMessage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerChatSendRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerReactionPresetsResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerReactionRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerReactionResponse;
import jakarta.servlet.http.HttpServletRequest;
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
 * LUT-633: 탑 채팅·리액션. 전송은 REST(거부 사유 {@code 1503xx} 를 바로 받는다), 수신은 {@code
 * /topic/tower/instances/{id}} 의 {@code CHAT}/{@code REACTION}. 비로그인 허용(방문객은 {@code visitor_key}) —
 * 점유자 판정은 서비스 403 {@code 150301}.
 */
@RestController
@RequestMapping("/api/v1/tower")
@RequiredArgsConstructor
public class TowerChatController {
    private final TowerChatService towerChatService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/instances/{instanceId}/chat")
    public ApiResult<TowerChatMessage> send(
            @CurrentUser(required = false) String userId,
            @PathVariable Long instanceId,
            @Valid @RequestBody TowerChatSendRequest request,
            HttpServletRequest httpRequest) {
        return ApiResult.<TowerChatMessage>builder()
                .value(
                        towerChatService.send(
                                instanceId,
                                userId,
                                request.visitorKey(),
                                request.content(),
                                clientIpResolver.resolve(httpRequest)))
                .build();
    }

    @PostMapping("/instances/{instanceId}/reactions")
    public ApiResult<TowerReactionResponse> react(
            @CurrentUser(required = false) String userId,
            @PathVariable Long instanceId,
            @Valid @RequestBody TowerReactionRequest request,
            HttpServletRequest httpRequest) {
        return ApiResult.<TowerReactionResponse>builder()
                .value(
                        towerChatService.react(
                                instanceId,
                                userId,
                                request.visitorKey(),
                                request.emoji(),
                                request.targetUserId(),
                                clientIpResolver.resolve(httpRequest)))
                .build();
    }

    @GetMapping("/instances/{instanceId}/chat")
    public ApiResult<TowerChatHistoryResponse> history(
            @CurrentUser(required = false) String userId,
            @PathVariable Long instanceId,
            @RequestParam(name = "visitor_key", required = false) String visitorKey) {
        return ApiResult.<TowerChatHistoryResponse>builder()
                .value(towerChatService.history(instanceId, userId, visitorKey))
                .build();
    }

    @GetMapping("/chat/reactions")
    public ApiResult<TowerReactionPresetsResponse> presets() {
        return ApiResult.<TowerReactionPresetsResponse>builder()
                .value(towerChatService.presets())
                .build();
    }
}
