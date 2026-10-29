package io.pinkspider.leveluptogethermvp.bffservice.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.SeasonDetailResponse;
import io.pinkspider.leveluptogethermvp.bffservice.application.BffSeasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** BFF (Backend for Frontend) 시즌 컨트롤러 시즌 상세 화면에 필요한 데이터를 한 번의 API 호출로 제공합니다. */
@RestController
@RequestMapping("/api/v1/bff/season")
@RequiredArgsConstructor
public class BffSeasonController {

    private final BffSeasonService bffSeasonService;

    /**
     * 시즌 상세 데이터 조회 (BFF)
     *
     * <p>다음 데이터를 한 번에 조회합니다: - 시즌 정보 (제목, 기간, 상태) - 순위별 보상 목록 - 플레이어 랭킹 (1-10위) - 길드 랭킹 (1-10위) - 내
     * 랭킹 정보 - 미션 카테고리 목록 (탭용)
     *
     * @param userId 인증된 사용자 ID
     * @param seasonId 시즌 ID
     * @param categoryName 카테고리명 (선택적, null이면 전체)
     * @param acceptLanguage Accept-Language 헤더 (다국어 지원)
     * @return SeasonDetailResponse
     */
    @GetMapping("/{seasonId}")
    public ResponseEntity<ApiResult<SeasonDetailResponse>> getSeasonDetail(
            @CurrentUser String userId,
            @PathVariable Long seasonId,
            @RequestParam(required = false) String categoryName,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false)
                    String acceptLanguage) {
        SeasonDetailResponse response =
                bffSeasonService.getSeasonDetail(seasonId, userId, categoryName, acceptLanguage);
        return ResponseEntity.ok(ApiResult.<SeasonDetailResponse>builder().value(response).build());
    }

    /**
     * 현재 활성 시즌 상세 데이터 조회 (BFF)
     *
     * <p>현재 활성화된 시즌의 상세 정보를 조회합니다. 활성 시즌이 없는 경우 에러를 반환합니다.
     *
     * @param userId 인증된 사용자 ID
     * @param categoryName 카테고리명 (선택적, null이면 전체)
     * @param acceptLanguage Accept-Language 헤더 (다국어 지원)
     * @return SeasonDetailResponse
     */
    @GetMapping("/current")
    public ResponseEntity<ApiResult<SeasonDetailResponse>> getCurrentSeasonDetail(
            @CurrentUser String userId,
            @RequestParam(required = false) String categoryName,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false)
                    String acceptLanguage) {
        SeasonDetailResponse response =
                bffSeasonService.getCurrentSeasonDetail(userId, categoryName, acceptLanguage);
        return ResponseEntity.ok(ApiResult.<SeasonDetailResponse>builder().value(response).build());
    }
}
