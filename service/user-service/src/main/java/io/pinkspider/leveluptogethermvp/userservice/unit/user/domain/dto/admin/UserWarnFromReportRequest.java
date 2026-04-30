package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 신고 처리(WARNING 액션)로 사용자에게 경고를 줄 때 사용.
 * 누적 경고가 suspensionThreshold 이상이면 자동으로 USER_SUSPENDED 처리(30일 정지).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record UserWarnFromReportRequest(
    @NotBlank
    String reason,

    @NotNull
    Long adminId,

    /** 자동 정지 전환 임계값(누적 경고 횟수). */
    @NotNull
    Integer suspensionThreshold,

    /** 자동 전환 시 정지 기간(일). */
    @NotNull
    Integer suspensionDays,

    /** 정지 누적이 이 값 이상이면 영구 강퇴 (suspendFromReport에 그대로 위임). */
    @NotNull
    Integer permanentBanThreshold
) {
}