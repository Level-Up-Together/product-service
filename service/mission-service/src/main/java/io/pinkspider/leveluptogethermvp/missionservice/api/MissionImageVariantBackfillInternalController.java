package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionImageVariantBackfillService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionImageVariantBackfillResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-409: Admin 내부 API - 과거 업로드 이미지 thumb/medium 변형 백필.
 * 인증은 InternalApiKeyFilter (/api/internal/**) 가 담당한다.
 */
@RestController
@RequestMapping("/api/internal/mission-images")
@RequiredArgsConstructor
public class MissionImageVariantBackfillInternalController {

    private final MissionImageVariantBackfillService backfillService;

    /**
     * 변형 없는 과거 업로드 이미지에 thumb/medium 변형을 생성한다 (일회성 수동 트리거).
     * 멱등 — 재실행해도 이미 생성된 변형은 건너뛴다. limit 로 분할 실행 가능.
     */
    @PostMapping("/backfill-variants")
    public ApiResult<MissionImageVariantBackfillResultResponse> backfillVariants(
            @RequestParam(required = false) Integer limit) {
        return ApiResult.<MissionImageVariantBackfillResultResponse>builder()
            .value(backfillService.backfillVariants(limit))
            .build();
    }
}
