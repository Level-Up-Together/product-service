package io.pinkspider.leveluptogethermvp.gamificationservice.attendance.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AttendanceCheckInResponse {
    private AttendanceResponse attendance;
    private Integer consecutiveDays;
    private Integer baseExp;
    private Integer bonusExp;
    private Integer totalExp;
    private List<String> bonusReasons;
    private boolean isAlreadyCheckedIn;
    private String message;

    public static AttendanceCheckInResponse alreadyCheckedIn(AttendanceResponse existing) {
        return AttendanceCheckInResponse.builder()
            .attendance(existing)
            .consecutiveDays(existing.getConsecutiveDays())
            .baseExp(0)
            .bonusExp(0)
            .totalExp(0)
            .isAlreadyCheckedIn(true)
            .message("오늘은 이미 출석 체크를 완료했습니다.")
            .build();
    }

    public static AttendanceCheckInResponse success(AttendanceResponse attendance,
                                                     int baseExp, int bonusExp,
                                                     List<String> bonusReasons) {
        return AttendanceCheckInResponse.builder()
            .attendance(attendance)
            .consecutiveDays(attendance.getConsecutiveDays())
            .baseExp(baseExp)
            .bonusExp(bonusExp)
            .totalExp(baseExp + bonusExp)
            .bonusReasons(bonusReasons)
            .isAlreadyCheckedIn(false)
            .message(String.format("출석 체크 완료! %d일 연속 출석입니다.", attendance.getConsecutiveDays()))
            .build();
    }
}
