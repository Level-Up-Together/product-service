package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 신고 처리(USER_SUSPENDED 액션)로 사용자를 정지시킬 때 사용.
 * 누적 정지 횟수가 permanentBanThreshold 이상이면 자동으로 영구 강퇴 처리.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record UserSuspendFromReportRequest(
    @NotBlank
    String reason,

    @NotNull
    Long adminId,

    /** 일시 정지 기간(일). 임계값 도달 시 무시되고 영구 강퇴로 전환. */
    @NotNull
    Integer durationDays,

    /** 영구 강퇴 자동 전환 임계값. 누적 정지 횟수가 이 값 이상이면 PERMANENT_BAN. */
    @NotNull
    Integer permanentBanThreshold
) {
}