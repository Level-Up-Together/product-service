package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * LUT-320: 타 유저 프로필 주간 캘린더 응답 DTO.
 *
 * <p>비로그인 포함 누구나 조회할 수 있으므로, 미션 공개범위에 따라 비노출 미션은 미션명/카테고리를
 * null 로 마스킹하고 is_visible=false 로 내린다 (LUT-257 프로필 진행중 미션과 동일 규칙).
 * 시간 필드는 마스킹과 무관하게 유지해 프론트가 시간표 블록을 배치할 수 있게 한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WeeklyCalendarResponse {

    /** 주 시작일 (사용자 타임존 기준 월요일, "yyyy-MM-dd") */
    private String startDate;

    /** 주 마지막일 (일요일, "yyyy-MM-dd") */
    private String endDate;

    /** 날짜별 완료된 미션 목록 (key: "yyyy-MM-dd") */
    private Map<String, List<CalendarMission>> dailyMissions;

    /** 완료된 미션이 있는 날짜 목록 (요일 헤더 하이라이트용) */
    private List<String> completedDates;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class CalendarMission {
        /** 미션 ID (비노출 시 null) */
        private Long missionId;

        /** 미션명 (비노출 시 null — 프론트가 공개범위별 마스킹 문구로 치환) */
        private String missionTitle;

        /** 카테고리명 (비노출 시 null) */
        private String categoryName;

        private Integer expEarned;
        private Integer durationMinutes;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;

        /** 미션 공개범위 원본 값 — 마스킹 문구 선택용 */
        private String visibility;

        /** 조회자 노출 여부 — false 면 미션 식별 정보가 null 마스킹됨 */
        @JsonProperty("is_visible")
        private Boolean isVisible;
    }
}
