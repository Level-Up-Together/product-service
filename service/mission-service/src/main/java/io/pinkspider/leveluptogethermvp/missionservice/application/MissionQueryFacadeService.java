package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.facade.MissionQueryFacade;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import java.util.HashSet;
import java.util.Set;
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
}
