package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.missionservice.application.GuildExpBackfillService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.GuildExpBackfillResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-236: Admin 내부 API - 자동종료 길드 경험치 소급 보정.
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll)
 */
@RestController
@RequestMapping("/api/internal/guilds/exp")
@RequiredArgsConstructor
public class GuildExpBackfillInternalController {

    private final GuildExpBackfillService guildExpBackfillService;

    /**
     * 자동종료로 누락된 길드 경험치를 소급 지급한다 (일회성 수동 트리거).
     * 멱등 — 재실행해도 이미 지급된 건은 건너뛴다.
     */
    @PostMapping("/backfill-auto-complete")
    public ApiResult<GuildExpBackfillResultResponse> backfillAutoCompleteGuildExp() {
        return ApiResult.<GuildExpBackfillResultResponse>builder()
            .value(guildExpBackfillService.backfillAutoCompletedGuildExp())
            .build();
    }
}
