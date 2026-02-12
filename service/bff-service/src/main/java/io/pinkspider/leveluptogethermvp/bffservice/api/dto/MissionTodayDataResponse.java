package io.pinkspider.leveluptogethermvp.bffservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 미션 오늘 BFF 응답 DTO
 * 내 미션 목록과 오늘의 미션 실행 현황을 한 번에 반환합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class MissionTodayDataResponse {

    /**
     * 내 미션 목록
     */
    private List<MissionResponse> myMissions;

    /**
     * 오늘의 미션 실행 현황
     * 고정 미션의 경우 PENDING 인스턴스만 포함 (해야할 미션에 표시용)
     */
    private List<MissionExecutionResponse> todayExecutions;

    /**
     * 오늘 완료된 고정 미션 인스턴스 목록 (오늘 수행 기록에 표시용)
     * 고정 미션은 하루에 여러 번 수행 가능하므로, 완료된 내역을 별도로 반환합니다.
     * 프론트엔드에서 이 목록을 '오늘 수행 기록' 섹션에 표시해야 합니다.
     */
    private List<MissionExecutionResponse> completedPinnedInstances;

    /**
     * 오늘 완료한 미션 수 (일반 미션 + 고정 미션 완료 횟수)
     */
    private int completedCount;

    /**
     * 오늘 진행 중인 미션 수
     */
    private int inProgressCount;

    /**
     * 오늘 미완료 미션 수
     */
    private int pendingCount;
}
