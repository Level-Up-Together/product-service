package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.DiamondBundleService;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundleResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-356: 상점 노출용 핑크다이아 묶음상품 API.
 * 목록 GET은 비로그인 허용 (SecurityConfig permitAll — 상점 Browse-First, LUT-350 패턴).
 */
@RestController
@RequestMapping("/api/v1/diamond-bundles")
@RequiredArgsConstructor
public class DiamondBundleController {

    private final DiamondBundleService diamondBundleService;

    /** 판매중 핑크다이아 묶음상품 목록 조회 */
    @GetMapping
    public ResponseEntity<ApiResult<List<DiamondBundleResponse>>> getActiveBundles() {
        return ResponseEntity.ok(
                ApiResult.<List<DiamondBundleResponse>>builder()
                        .value(diamondBundleService.getActiveBundles())
                        .build());
    }
}
