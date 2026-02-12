package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.leveluptogethermvp.missionservice.application.strategy.MissionExecutionStrategyResolver;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyCalendarResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyCalendarResponse.DailyMission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "missionTransactionManager", readOnly = true)
public class MissionExecutionQueryService {

    private final MissionExecutionRepository executionRepository;
    private final MissionParticipantRepository participantRepository;
    private final DailyMissionInstanceRepository dailyMissionInstanceRepository;
    private final MissionExecutionStrategyResolver strategyResolver;

    public List<MissionExecutionResponse> getExecutionsByParticipant(Long participantId) {
        return executionRepository.findByParticipantId(participantId).stream()
            .map(MissionExecutionResponse::from)
            .toList();
    }

    public List<MissionExecutionResponse> getExecutionsByMissionAndUser(Long missionId, String userId) {
        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));

        return executionRepository.findByParticipantId(participant.getId()).stream()
            .map(MissionExecutionResponse::from)
            .toList();
    }

    public List<MissionExecutionResponse> getExecutionsForMission(Long missionId, String userId) {
        return getExecutionsByMissionAndUser(missionId, userId);
    }

    public MissionExecutionResponse getExecutionByDate(Long missionId, String userId, LocalDate date) {
        return strategyResolver.resolve(missionId, userId).getExecutionByDate(missionId, userId, date);
    }

    public List<MissionExecutionResponse> getExecutionsByDateRange(Long missionId, String userId,
                                                                    LocalDate startDate, LocalDate endDate) {
        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));

        return executionRepository.findByParticipantIdAndExecutionDateBetween(
                participant.getId(), startDate, endDate).stream()
            .map(MissionExecutionResponse::from)
            .toList();
    }

    public double getCompletionRate(Long missionId, String userId) {
        MissionParticipant participant = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("미션 참여 정보를 찾을 수 없습니다."));

        long totalExecutions = executionRepository.findByParticipantId(participant.getId()).size();
        if (totalExecutions == 0) {
            return 0.0;
        }

        long completedExecutions = executionRepository.countByParticipantIdAndStatus(
            participant.getId(), ExecutionStatus.COMPLETED);

        return (double) completedExecutions / totalExecutions * 100;
    }

    /**
     * 사용자의 현재 진행 중인 미션 조회
     */
    public MissionExecutionResponse getInProgressExecution(String userId) {
        return executionRepository.findInProgressByUserId(userId)
            .map(MissionExecutionResponse::from)
            .orElse(null);
    }

    /**
     * 오늘 실행해야 할 미션 목록 조회
     * 일반 미션(MissionExecution)과 고정 미션(DailyMissionInstance) 모두 포함
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public List<MissionExecutionResponse> getTodayExecutions(String userId) {
        LocalDate today = LocalDate.now();

        // 고정 미션의 오늘 execution 자동 생성 (일반 MissionExecution용)
        ensurePinnedMissionExecutionsForToday(userId, today);

        List<MissionExecutionResponse> responses = new ArrayList<>();

        // 일반 미션 Execution 조회
        List<MissionExecutionResponse> regularExecutions = executionRepository
            .findByUserIdAndExecutionDate(userId, today).stream()
            .map(MissionExecutionResponse::from)
            .toList();
        responses.addAll(regularExecutions);

        // 고정 미션 DailyMissionInstance 조회
        List<DailyMissionInstance> dailyInstances = dailyMissionInstanceRepository
            .findByUserIdAndInstanceDateWithMission(userId, today);
        List<MissionExecutionResponse> instanceResponses = dailyInstances.stream()
            .map(MissionExecutionResponse::fromDailyMissionInstance)
            .toList();
        responses.addAll(instanceResponses);

        log.info("getTodayExecutions: userId={}, regularCount={}, instanceCount={}",
            userId, regularExecutions.size(), instanceResponses.size());

        return responses;
    }

    /**
     * 오늘 완료된 고정 미션 인스턴스 조회 (오늘 수행 기록용)
     *
     * 고정 미션은 하루에 여러 번 수행 가능하므로, 완료된 인스턴스를 별도로 반환합니다.
     * 프론트엔드에서 '오늘 수행 기록' 섹션에 표시하는 데 사용됩니다.
     *
     * @param userId 사용자 ID
     * @return 완료된 고정 미션 인스턴스 목록
     */
    public List<MissionExecutionResponse> getCompletedPinnedInstancesForToday(String userId) {
        LocalDate today = LocalDate.now();

        List<DailyMissionInstance> completedInstances = dailyMissionInstanceRepository
            .findCompletedByUserIdAndInstanceDate(userId, today);

        List<MissionExecutionResponse> responses = completedInstances.stream()
            .map(MissionExecutionResponse::fromDailyMissionInstance)
            .toList();

        log.info("getCompletedPinnedInstancesForToday: userId={}, count={}", userId, responses.size());

        return responses;
    }

    /**
     * 월별 캘린더 데이터 조회
     * 해당 월의 완료된 미션 실행 내역과 총 획득 경험치 반환
     * 일반 미션(MissionExecution)과 고정 미션(DailyMissionInstance) 모두 포함
     */
    public MonthlyCalendarResponse getMonthlyCalendarData(String userId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // 1. 일반 미션 - 완료된 미션 실행 내역 조회
        List<MissionExecution> completedExecutions = executionRepository
            .findCompletedByUserIdAndDateRange(userId, startDate, endDate);

        // 2. 고정 미션 - 완료된 인스턴스 조회
        List<DailyMissionInstance> completedInstances = dailyMissionInstanceRepository
            .findCompletedByUserIdAndDateRange(userId, startDate, endDate);

        // 3. 월별 총 획득 경험치 조회 (일반 미션 + 고정 미션)
        int regularMissionExp = executionRepository.sumExpEarnedByUserIdAndDateRange(userId, startDate, endDate);
        int pinnedMissionExp = dailyMissionInstanceRepository.sumExpEarnedByUserIdAndDateRange(userId, startDate, endDate);
        int totalExp = regularMissionExp + pinnedMissionExp;

        // 4. 날짜별 미션 그룹화
        Map<String, List<DailyMission>> dailyMissions = new HashMap<>();

        // 4-1. 일반 미션 추가
        for (MissionExecution execution : completedExecutions) {
            String dateKey = execution.getExecutionDate().toString();

            Integer durationMinutes = null;
            if (execution.getStartedAt() != null && execution.getCompletedAt() != null) {
                durationMinutes = (int) java.time.Duration.between(
                    execution.getStartedAt(), execution.getCompletedAt()).toMinutes();
            }

            DailyMission dailyMission = DailyMission.builder()
                .missionId(execution.getParticipant().getMission().getId())
                .missionTitle(execution.getParticipant().getMission().getTitle())
                .expEarned(execution.getExpEarned())
                .durationMinutes(durationMinutes)
                .build();

            dailyMissions.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(dailyMission);
        }

        // 4-2. 고정 미션 추가
        for (DailyMissionInstance instance : completedInstances) {
            String dateKey = instance.getInstanceDate().toString();

            Integer durationMinutes = null;
            if (instance.getStartedAt() != null && instance.getCompletedAt() != null) {
                durationMinutes = (int) java.time.Duration.between(
                    instance.getStartedAt(), instance.getCompletedAt()).toMinutes();
            }

            DailyMission dailyMission = DailyMission.builder()
                .missionId(instance.getParticipant().getMission().getId())
                .missionTitle(instance.getMissionTitle())
                .expEarned(instance.getExpEarned())
                .durationMinutes(durationMinutes)
                .build();

            dailyMissions.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(dailyMission);
        }

        // 5. 완료된 미션이 있는 날짜 목록
        List<String> completedDates = new ArrayList<>(dailyMissions.keySet());
        completedDates.sort(String::compareTo);

        log.info("월별 캘린더 데이터 조회: userId={}, year={}, month={}, totalExp={}, completedDays={}, regularMissions={}, pinnedMissions={}",
            userId, year, month, totalExp, completedDates.size(), completedExecutions.size(), completedInstances.size());

        return MonthlyCalendarResponse.builder()
            .year(year)
            .month(month)
            .totalExp(totalExp)
            .dailyMissions(dailyMissions)
            .completedDates(completedDates)
            .build();
    }

    /**
     * 고정 미션(isPinned=true)에 대해 오늘 날짜의 DailyMissionInstance가 없으면 자동 생성
     * 스케줄러가 매일 자동 생성하지만, 스케줄러 실행 전 접근 시를 대비
     */
    private void ensurePinnedMissionExecutionsForToday(String userId, LocalDate today) {
        List<MissionParticipant> pinnedParticipants = participantRepository.findPinnedMissionParticipants(userId);

        for (MissionParticipant participant : pinnedParticipants) {
            // 오늘 날짜의 DailyMissionInstance가 있는지 확인
            boolean hasInstance = dailyMissionInstanceRepository
                .existsByParticipantIdAndInstanceDate(participant.getId(), today);

            if (!hasInstance) {
                // 고정 미션의 오늘 DailyMissionInstance 자동 생성
                DailyMissionInstance instance = DailyMissionInstance.createFrom(participant, today);
                dailyMissionInstanceRepository.saveAndFlush(instance);

                log.info("고정 미션 오늘 인스턴스 자동 생성: missionId={}, userId={}, date={}",
                    participant.getMission().getId(), userId, today);
            }
        }
    }

}
