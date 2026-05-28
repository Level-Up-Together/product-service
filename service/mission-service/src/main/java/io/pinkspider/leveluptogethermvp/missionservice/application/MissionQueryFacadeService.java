package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.facade.MissionQueryFacade;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * mission-service 외부 노출 read-only 구현체.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "missionTransactionManager")
public class MissionQueryFacadeService implements MissionQueryFacade {

    private final MissionRepository missionRepository;

    @Override
    public int countClearedMissionBookTemplates(String userId) {
        return findClearedMissionBookTemplateIds(userId).size();
    }

    @Override
    public Set<Long> findClearedMissionBookTemplateIds(String userId) {
        return new HashSet<>(missionRepository.findClearedMissionTemplateIdsByUserId(userId));
    }
}
