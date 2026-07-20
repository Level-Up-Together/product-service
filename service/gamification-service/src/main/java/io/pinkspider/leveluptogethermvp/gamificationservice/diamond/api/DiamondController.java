package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.DiamondService;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.UserDiamondBalanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** LUT-248: 유저용 다이아 조회 API. 마이페이지 "현재 보유 다이아" 표기에 사용. */
@RestController
@RequestMapping("/api/v1/diamonds")
@RequiredArgsConstructor
public class DiamondController {

    private final DiamondService diamondService;

    /** 내 현재 보유 다이아 잔액 조회 */
    @GetMapping("/me")
    public ResponseEntity<ApiResult<UserDiamondBalanceResponse>> getMyDiamondBalance(
            @CurrentUser String userId) {
        UserDiamondBalanceResponse response =
                UserDiamondBalanceResponse.of(diamondService.getBalance(userId));
        return ResponseEntity.ok(
                ApiResult.<UserDiamondBalanceResponse>builder().value(response).build());
    }
}
