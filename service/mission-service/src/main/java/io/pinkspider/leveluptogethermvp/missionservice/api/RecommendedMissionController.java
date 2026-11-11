package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionIdempotencyService;
import io.pinkspider.leveluptogethermvp.missionservice.application.RecommendedMissionService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionStartResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-782: 첫 미션 온보딩 추천 미션 — 환영 모달·미션 탭 빈 화면(LUT-784)이 부른다. 로그인 필요(두 화면 모두 로그인 뒤).
 *
 * <ul>
 *   <li>{@code GET /api/v1/missions/recommended} — 켜진 추천 미션, 이름은 Accept-Language(비면 한국어)
 *   <li>{@code POST /api/v1/missions/recommended/{id}/start} — 내 미션을 만들고 오늘 수행을 바로 시작. {@code
 *       Idempotency-Key}(UUID) 를 보내면 같은 키 재요청에 같은 미션을 돌려준다. 진행 중인 미션이 있으면 기존 시작과 같은 오류
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/missions/recommended")
@RequiredArgsConstructor
public class RecommendedMissionController {

    private final RecommendedMissionService recommendedMissionService;
    private final MissionIdempotencyService missionIdempotencyService;

    @GetMapping
    public ResponseEntity<ApiResult<List<RecommendedMissionResponse>>> getRecommended(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false)
                    String acceptLanguage) {
        return ResponseEntity.ok(
                ApiResult.<List<RecommendedMissionResponse>>builder()
                        .value(recommendedMissionService.getActive(acceptLanguage))
                        .build());
    }

    @PostMapping("/{recommendedId}/start")
    public ResponseEntity<ApiResult<RecommendedMissionStartResponse>> start(
            @PathVariable Long recommendedId,
            @CurrentUser String userId,
            @RequestHeader(value = MissionIdempotencyService.HEADER_NAME, required = false)
                    String idempotencyKey,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false)
                    String acceptLanguage) {
        return ResponseEntity.ok(
                ApiResult.<RecommendedMissionStartResponse>builder()
                        .value(
                                missionIdempotencyService.startRecommended(
                                        userId, idempotencyKey, recommendedId, acceptLanguage))
                        .build());
    }
}
