package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;

/**
 * LUT-860: 완료한 미션 수행 취소 결과.
 *
 * @param expCancelled 되돌린 미션 경험치 (한도에 걸려 0 을 받았던 수행이면 0)
 * @param milestoneExpCancelled 같이 되돌린 일일 체크 미션 마일스톤 보너스 (없으면 0)
 * @param expDebt 취소 뒤 남은 누적 마이너스 경험치 — 다음 미션 경험치에서 먼저 뺀다
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MissionExecutionCancelResponse(
        Long missionId,
        LocalDate executionDate,
        int expCancelled,
        int milestoneExpCancelled,
        int expDebt) {}
