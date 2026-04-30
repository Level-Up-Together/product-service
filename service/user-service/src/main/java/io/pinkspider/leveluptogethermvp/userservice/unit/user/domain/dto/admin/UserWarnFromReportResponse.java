package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 신고 WARNING 처리 결과.
 *
 * @param warningCount   처리 후 사용자의 경고 누적 횟수 (정지 전환 시 0으로 리셋된 값)
 * @param escalated      자동 정지 전환 여부
 * @param blacklist      정지 전환 시 생성된 블랙리스트 정보 (escalated=true일 때만)
 */
@JsonNaming(SnakeCaseStrategy.class)
public record UserWarnFromReportResponse(
    int warningCount,
    boolean escalated,
    UserBlacklistAdminResponse blacklist
) {
    public static UserWarnFromReportResponse warningOnly(int warningCount) {
        return new UserWarnFromReportResponse(warningCount, false, null);
    }

    public static UserWarnFromReportResponse escalated(UserBlacklistAdminResponse blacklist) {
        return new UserWarnFromReportResponse(0, true, blacklist);
    }
}
