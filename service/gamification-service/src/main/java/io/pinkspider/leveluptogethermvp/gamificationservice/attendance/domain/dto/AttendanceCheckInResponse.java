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

    /**
     * LUT-708: 이번 출석의 연속 일수가 어드민 설정 마일스톤(기본 7·14·30)과 같아 웹이 스토어 인앱 평가 팝업을 요청해야 하는지. 이미 출석한 응답에서는
     * false. 구웹은 이 필드를 몰라도 되고(없으면 7·14·30 폴백), 값이 있으면 이 판정만 따른다.
     */
    private Boolean storeReviewDue;

    public static AttendanceCheckInResponse alreadyCheckedIn(AttendanceResponse existing) {
        return AttendanceCheckInResponse.builder()
                .attendance(existing)
                .consecutiveDays(existing.getConsecutiveDays())
                .baseExp(0)
                .bonusExp(0)
                .totalExp(0)
                .isAlreadyCheckedIn(true)
                .message("오늘은 이미 출석 체크를 완료했습니다.")
                .storeReviewDue(false)
                .build();
    }

    public static AttendanceCheckInResponse success(
            AttendanceResponse attendance, int baseExp, int bonusExp, List<String> bonusReasons) {
        return success(attendance, baseExp, bonusExp, bonusReasons, false);
    }

    public static AttendanceCheckInResponse success(
            AttendanceResponse attendance,
            int baseExp,
            int bonusExp,
            List<String> bonusReasons,
            boolean storeReviewDue) {
        return AttendanceCheckInResponse.builder()
                .attendance(attendance)
                .consecutiveDays(attendance.getConsecutiveDays())
                .baseExp(baseExp)
                .bonusExp(bonusExp)
                .totalExp(baseExp + bonusExp)
                .bonusReasons(bonusReasons)
                .isAlreadyCheckedIn(false)
                .message(String.format("출석 체크 완료! %d일 연속 출석입니다.", attendance.getConsecutiveDays()))
                .storeReviewDue(storeReviewDue)
                .build();
    }
}
