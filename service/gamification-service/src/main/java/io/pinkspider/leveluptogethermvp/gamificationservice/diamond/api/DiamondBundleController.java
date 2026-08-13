package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.DiamondBundlePurchaseService;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.DiamondBundleService;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundlePurchaseRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundlePurchaseResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundleResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-356: 상점 노출용 핑크다이아 묶음상품 API.
 * 목록 GET은 비로그인 허용 (SecurityConfig permitAll — 상점 Browse-First, LUT-350 패턴).
 * 구매 POST는 인증 필요 (LUT-354: IAP 영수증 검증 + 멱등 지급).
 */
@RestController
@RequestMapping("/api/v1/diamond-bundles")
@RequiredArgsConstructor
public class DiamondBundleController {

    private final DiamondBundleService diamondBundleService;
    private final DiamondBundlePurchaseService diamondBundlePurchaseService;

    /** 판매중 핑크다이아 묶음상품 목록 조회 */
    @GetMapping
    public ResponseEntity<ApiResult<List<DiamondBundleResponse>>> getActiveBundles() {
        return ResponseEntity.ok(
                ApiResult.<List<DiamondBundleResponse>>builder()
                        .value(diamondBundleService.getActiveBundles())
                        .build());
    }

    /** LUT-354: IAP 구매 — RN이 스토어 결제 후 영수증을 전달하면 검증·멱등 지급한다 */
    @PostMapping("/{bundleId}/purchase")
    public ResponseEntity<ApiResult<DiamondBundlePurchaseResponse>> purchaseBundle(
            @CurrentUser String userId,
            @PathVariable Long bundleId,
            @Valid @RequestBody DiamondBundlePurchaseRequest request) {
        return ResponseEntity.ok(
                ApiResult.<DiamondBundlePurchaseResponse>builder()
                        .value(diamondBundlePurchaseService.purchase(userId, bundleId, request))
                        .build());
    }
}
