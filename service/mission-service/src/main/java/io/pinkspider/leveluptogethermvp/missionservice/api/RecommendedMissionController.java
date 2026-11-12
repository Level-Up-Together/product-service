package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.ratelimit.PerUserRateLimit;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionIdempotencyService;
import io.pinkspider.leveluptogethermvp.missionservice.application.RecommendedMissionService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionCompleteResponse;
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
 *   <li>{@code POST /api/v1/missions/recommended/{id}/complete}(LUT-803) — <b>체크 미션 전용</b>. 내 미션을
 *       만들고 바로 완료한다(수행 중을 거치지 않는다). 타이머 추천이면 400 {@code 050503}. 멱등 키 규칙은 시작과 같다
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

    /**
     * LUT-803: 체크(SIMPLE) 추천 미션 원탭 완료 — 내 미션 생성 + 시작 + 완료를 한 번에. 화면은 확인("다 했으면 눌러 주세요")을 누른 뒤에 부른다.
     * 완료 횟수 제한은 기존 완료 API 와 같은 한도를 함께 쓴다.
     */
    @PostMapping("/{recommendedId}/complete")
    @PerUserRateLimit(name = "missionCompletion", limit = 10, windowSeconds = 60)
    public ResponseEntity<ApiResult<RecommendedMissionCompleteResponse>> complete(
            @PathVariable Long recommendedId,
            @CurrentUser String userId,
            @RequestHeader(value = MissionIdempotencyService.HEADER_NAME, required = false)
                    String idempotencyKey,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false)
                    String acceptLanguage) {
        return ResponseEntity.ok(
                ApiResult.<RecommendedMissionCompleteResponse>builder()
                        .value(
                                missionIdempotencyService.completeRecommended(
                                        userId, idempotencyKey, recommendedId, acceptLanguage))
                        .build());
    }
}
