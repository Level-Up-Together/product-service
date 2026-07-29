package io.pinkspider.leveluptogethermvp.gamificationservice.shop.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.UserItemService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.UserItemResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** LUT-296: 아이템 인벤토리 — 내 보유 아이템 조회/장착 */
@RestController
@RequestMapping("/api/v1/user-items")
@RequiredArgsConstructor
public class UserItemController {

    private final UserItemService userItemService;

    // 내 보유 아이템 목록 (기본 아이템 미보유 시 lazy 지급)
    @GetMapping
    public ResponseEntity<ApiResult<List<UserItemResponse>>> getMyItems(
        @CurrentUser String userId) {
        List<UserItemResponse> responses = userItemService.getMyItems(userId);
        return ResponseEntity.ok(ApiResult.<List<UserItemResponse>>builder().value(responses).build());
    }

    // 아이템 장착 (같은 타입 기존 장착은 해제 — 타입당 1개)
    @PostMapping("/{shopItemId}/equip")
    public ResponseEntity<ApiResult<UserItemResponse>> equipItem(
        @CurrentUser String userId,
        @PathVariable Long shopItemId) {
        UserItemResponse response = userItemService.equipItem(userId, shopItemId);
        return ResponseEntity.ok(ApiResult.<UserItemResponse>builder().value(response).build());
    }
}
