package io.pinkspider.leveluptogethermvp.missionservice.application.strategy;

import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MissionExecutionStrategyResolver {

    private final MissionParticipantRepository participantRepository;
    private final RegularMissionExecutionStrategy regularStrategy;
    private final PinnedMissionExecutionStrategy pinnedStrategy;

    public MissionExecutionStrategy resolve(Long missionId, String userId) {
        boolean isPinned = participantRepository.findByMissionIdAndUserId(missionId, userId)
            .map(participant -> Boolean.TRUE.equals(participant.getMission().getIsPinned()))
            .orElse(false);
        return isPinned ? pinnedStrategy : regularStrategy;
    }
}
