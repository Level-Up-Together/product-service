package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source;

import java.time.LocalDateTime;

/** LUT-779: 유저 1명에게 일어난 일 1건(UTC 시각) — 첫날 미션 완료·상호작용 판정 원천 */
public record UserEvent(String userId, LocalDateTime at) {}
