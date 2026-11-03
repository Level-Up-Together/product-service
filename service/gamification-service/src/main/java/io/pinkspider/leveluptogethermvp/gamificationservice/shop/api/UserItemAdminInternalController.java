package io.pinkspider.leveluptogethermvp.gamificationservice.shop.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.UserItemService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.UserItemAdminResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API — 유저 보유 아이템 (LUT-657). admin-service `GET /api/admin/users/{userId}/items` 가 그대로
 * 읽는다. 칭호(`/api/internal/users/{userId}/titles`, user-service)와 같은 모양이지만 아이템은 gamification_db 소관이라
 * 여기 둔다. 내부 키({@code X-Internal-Api-Key})로 보호.
 */
@RestController
@RequestMapping("/api/internal/users/{userId}/items")
@RequiredArgsConstructor
public class UserItemAdminInternalController {

    private final UserItemService userItemService;

    /** 보유 아이템 전부(착용·미착용) — 기본 아이템 lazy 지급 없이 있는 그대로 */
    @GetMapping
    public ApiResult<List<UserItemAdminResponse>> getUserItems(@PathVariable String userId) {
        return ApiResult.<List<UserItemAdminResponse>>builder()
                .value(userItemService.getItemsForAdmin(userId))
                .build();
    }
}
