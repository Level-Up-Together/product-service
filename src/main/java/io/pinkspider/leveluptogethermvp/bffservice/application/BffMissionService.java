package io.pinkspider.leveluptogethermvp.bffservice.application;

import io.pinkspider.leveluptogethermvp.bffservice.api.dto.MissionTodayDataResponse;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionExecutionQueryService;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * BFF (Backend for Frontend) 서비스 - 미션
 * 미션 관련 화면에 필요한 여러 데이터를 한 번에 조회합니다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BffMissionService {

    private final MissionService missionService;
    private final MissionExecutionQueryService missionExecutionQueryService;

    /**
     * 오늘의 미션 화면에 필요한 모든 데이터를 한 번에 조회합니다.
     *
     * @param userId 현재 로그인한 사용자 ID
     * @return MissionTodayDataResponse 오늘의 미션 데이터
     */
    public MissionTodayDataResponse getTodayMissions(String userId) {
        log.info("BFF getTodayMissions called: userId={}", userId);

        // 병렬로 모든 데이터 조회
        CompletableFuture<List<MissionResponse>> myMissionsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return missionService.getMyMissions(userId);
            } catch (Exception e) {
                log.error("Failed to fetch my missions", e);
                return Collections.emptyList();
            }
        });

        CompletableFuture<List<MissionExecutionResponse>> todayExecutionsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return missionExecutionQueryService.getTodayExecutions(userId);
            } catch (Exception e) {
                log.error("Failed to fetch today executions", e);
                return Collections.emptyList();
            }
        });

        CompletableFuture<List<MissionExecutionResponse>> completedPinnedFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return missionExecutionQueryService.getCompletedPinnedInstancesForToday(userId);
            } catch (Exception e) {
                log.error("Failed to fetch completed pinned instances", e);
                return Collections.emptyList();
            }
        });

        // 모든 결과 취합
        CompletableFuture.allOf(myMissionsFuture, todayExecutionsFuture, completedPinnedFuture).join();

        List<MissionResponse> myMissions = myMissionsFuture.join();
        List<MissionExecutionResponse> todayExecutions = todayExecutionsFuture.join();
        List<MissionExecutionResponse> completedPinnedInstances = completedPinnedFuture.join();

        // 통계 계산 (일반 미션 완료 + 고정 미션 완료 횟수)
        int regularCompletedCount = (int) todayExecutions.stream()
            .filter(e -> e.getStatus() == ExecutionStatus.COMPLETED)
            .count();
        int completedCount = regularCompletedCount + completedPinnedInstances.size();

        int inProgressCount = (int) todayExecutions.stream()
            .filter(e -> e.getStatus() == ExecutionStatus.IN_PROGRESS)
            .count();

        int pendingCount = (int) todayExecutions.stream()
            .filter(e -> e.getStatus() == ExecutionStatus.PENDING)
            .count();

        MissionTodayDataResponse response = MissionTodayDataResponse.builder()
            .myMissions(myMissions)
            .todayExecutions(todayExecutions)
            .completedPinnedInstances(completedPinnedInstances)
            .completedCount(completedCount)
            .inProgressCount(inProgressCount)
            .pendingCount(pendingCount)
            .build();

        log.info("BFF getTodayMissions completed: userId={}, missionCount={}, executionCount={}, completedPinnedCount={}",
            userId, myMissions.size(), todayExecutions.size(), completedPinnedInstances.size());
        return response;
    }
}
