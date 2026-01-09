package io.pinkspider.leveluptogethermvp.userservice.attendance.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.AttendanceRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AttendanceResponse {
    private Long id;
    private String userId;
    private LocalDate attendanceDate;
    private Integer consecutiveDays;
    private Integer rewardExp;
    private Integer bonusRewardExp;
    private Integer totalRewardExp;
    private LocalDateTime createdAt;

    public static AttendanceResponse from(AttendanceRecord record) {
        return AttendanceResponse.builder()
            .id(record.getId())
            .userId(record.getUserId())
            .attendanceDate(record.getAttendanceDate())
            .consecutiveDays(record.getConsecutiveDays())
            .rewardExp(record.getRewardExp())
            .bonusRewardExp(record.getBonusRewardExp())
            .totalRewardExp(record.getTotalRewardExp())
            .createdAt(record.getCreatedAt())
            .build();
    }
}
