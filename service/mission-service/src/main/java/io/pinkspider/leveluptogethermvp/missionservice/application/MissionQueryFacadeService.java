package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.facade.MissionQueryFacade;
import io.pinkspider.global.facade.dto.InProgressMissionDto;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionTemplate;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionTemplateRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * mission-service 외부 노출 read-only 구현체.
 * <p>
 * QA-158: 미션북 목록 응답의 has_achieved_target 와 동일 정의(exp_earned >= target_duration_minutes)로
 * 통일하기 위해 MissionExecutionRepository/DailyMissionInstanceRepository 의 JPQL 두 메서드 합집합을 사용.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "missionTransactionManager")
public class MissionQueryFacadeService implements MissionQueryFacade {

    private final MissionExecutionRepository missionExecutionRepository;
    private final DailyMissionInstanceRepository dailyMissionInstanceRepository;
    private final MissionTemplateRepository missionTemplateRepository;

    @Override
    public Optional<InProgressMissionDto> findInProgressMission(String userId, String locale) {
        Optional<MissionExecution> execution = missionExecutionRepository.findInProgressByUserId(userId);
        Optional<DailyMissionInstance> instance = dailyMissionInstanceRepository.findInProgressByUserId(userId);

        InProgressMissionDto fromExecution = execution
            .map(e -> toDto(e.getParticipant().getMission(), e.getStartedAt(), locale))
            .orElse(null);
        InProgressMissionDto fromInstance = instance
            .map(i -> toDto(i.getParticipant().getMission(), i.getStartedAt(), locale))
            .orElse(null);

        if (fromExecution == null) {
            return Optional.ofNullable(fromInstance);
        }
        if (fromInstance == null) {
            return Optional.of(fromExecution);
        }
        // 둘 다 진행중이면 최근 시작한 쪽 우선
        return Optional.of(isAfter(fromExecution.startedAt(), fromInstance.startedAt())
            ? fromExecution : fromInstance);
    }

    /**
     * LUT-275: 여러 유저의 진행중 미션 배치 조회. 단건과 동일한 병합 규칙(일반/고정 통합, 최근 시작 우선)을
     * 유저별로 적용한다. 진행중 미션이 없는 유저는 결과 맵에 포함되지 않는다.
     */
    @Override
    public Map<String, InProgressMissionDto> findInProgressMissions(
            java.util.Collection<String> userIds, String locale) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<String, InProgressMissionDto> result = new java.util.HashMap<>();
        for (MissionExecution e : missionExecutionRepository.findInProgressByUserIdIn(userIds)) {
            mergeLatest(result, e.getParticipant().getUserId(),
                toDto(e.getParticipant().getMission(), e.getStartedAt(), locale));
        }
        for (DailyMissionInstance i : dailyMissionInstanceRepository.findInProgressByUserIdIn(userIds)) {
            mergeLatest(result, i.getParticipant().getUserId(),
                toDto(i.getParticipant().getMission(), i.getStartedAt(), locale));
        }
        return result;
    }

    /**
     * LUT-297: 진행중 미션이 있는 전체 유저 조회 (실시간 랭킹용). 배치 조회와 동일한 병합 규칙(일반/고정 통합,
     * 최근 시작 우선)을 유저별로 적용한다.
     */
    @Override
    public Map<String, InProgressMissionDto> findAllInProgressMissions(String locale) {
        Map<String, InProgressMissionDto> result = new java.util.HashMap<>();
        for (MissionExecution e : missionExecutionRepository.findAllInProgress()) {
            mergeLatest(result, e.getParticipant().getUserId(),
                toDto(e.getParticipant().getMission(), e.getStartedAt(), locale));
        }
        for (DailyMissionInstance i : dailyMissionInstanceRepository.findAllInProgress()) {
            mergeLatest(result, i.getParticipant().getUserId(),
                toDto(i.getParticipant().getMission(), i.getStartedAt(), locale));
        }
        return result;
    }

    private void mergeLatest(Map<String, InProgressMissionDto> result, String userId,
                              InProgressMissionDto candidate) {
        InProgressMissionDto existing = result.get(userId);
        if (existing == null || isAfter(candidate.startedAt(), existing.startedAt())) {
            result.put(userId, candidate);
        }
    }

    // LUT-377: 미션북 미션의 진행중 미션명이 프로필/랭킹에서 한국어로만 노출되던 문제 —
    // locale 번역 제목으로 내린다 (번역 없으면 원문 폴백, getLocalizedTitle 내장 동작)
    private InProgressMissionDto toDto(Mission mission, LocalDateTime startedAt, String locale) {
        return new InProgressMissionDto(
            mission.getId(),
            mission.getCategoryId(),
            mission.getCategoryName(),
            mission.getLocalizedTitle(locale),
            mission.getVisibility() != null ? mission.getVisibility().name() : null,
            mission.getGuildId(),
            startedAt);
    }

    private boolean isAfter(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return false;
        }
        return b == null || a.isAfter(b);
    }

    @Override
    public int countClearedMissionBookTemplates(String userId) {
        return findClearedMissionBookTemplateIds(userId).size();
    }

    @Override
    public Set<Long> findClearedMissionBookTemplateIds(String userId) {
        Set<Long> ids = new HashSet<>(missionExecutionRepository.findAchievedTargetTemplateIdsByUserId(userId));
        ids.addAll(dailyMissionInstanceRepository.findAchievedTargetTemplateIdsByUserId(userId));
        return ids;
    }

    @Override
    public Map<Long, String> getMissionBookTemplateTitles(Set<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return Map.of();
        }
        return missionTemplateRepository.findAllById(templateIds).stream()
            .collect(Collectors.toMap(MissionTemplate::getId, MissionTemplate::getTitle));
    }
}
