package io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public record SeasonRewardProcessResult(
    Long seasonId,
    String status,
    int successCount,
    int failCount,
    int skipCount,
    String message
) {
    public static SeasonRewardProcessResult completed(Long seasonId, int success, int fail, int skip) {
        return new SeasonRewardProcessResult(
            seasonId,
            "COMPLETED",
            success,
            fail,
            skip,
            String.format("처리 완료: 성공=%d, 실패=%d, 건너뜀=%d", success, fail, skip)
        );
    }

    public static SeasonRewardProcessResult alreadyProcessed(Long seasonId) {
        return new SeasonRewardProcessResult(
            seasonId,
            "ALREADY_PROCESSED",
            0,
            0,
            0,
            "이미 보상이 처리된 시즌입니다."
        );
    }

    public static SeasonRewardProcessResult noRewardsConfigured(Long seasonId) {
        return new SeasonRewardProcessResult(
            seasonId,
            "NO_REWARDS",
            0,
            0,
            0,
            "시즌에 설정된 보상이 없습니다."
        );
    }
}
