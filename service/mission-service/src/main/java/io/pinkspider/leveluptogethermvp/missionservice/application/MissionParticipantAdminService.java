package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.UserMissionHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.UserMissionHistoryAdminResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "missionTransactionManager")
public class MissionParticipantAdminService {

    private final MissionParticipantRepository participantRepository;
    private final MissionExecutionRepository executionRepository;

    public UserMissionHistoryAdminPageResponse getUserMissionHistory(String userId, Pageable pageable) {
        Page<MissionParticipant> participantPage = participantRepository.findByUserIdWithMissionPaged(userId, pageable);

        List<UserMissionHistoryAdminResponse> content = participantPage.getContent().stream()
            .map(participant -> {
                long totalExecutions = executionRepository.countByParticipantIdAndStatus(
                        participant.getId(), ExecutionStatus.COMPLETED)
                    + executionRepository.countByParticipantIdAndStatus(participant.getId(), ExecutionStatus.PENDING)
                    + executionRepository.countByParticipantIdAndStatus(participant.getId(), ExecutionStatus.IN_PROGRESS)
                    + executionRepository.countByParticipantIdAndStatus(participant.getId(), ExecutionStatus.MISSED);
                long completedExecutions = executionRepository.countByParticipantIdAndStatus(
                    participant.getId(), ExecutionStatus.COMPLETED);
                Integer totalExpEarned = executionRepository.sumExpEarnedByParticipantId(participant.getId());

                return UserMissionHistoryAdminResponse.from(
                    participant,
                    totalExecutions,
                    completedExecutions,
                    totalExpEarned != null ? totalExpEarned : 0
                );
            })
            .toList();

        return UserMissionHistoryAdminPageResponse.from(participantPage, content);
    }

    public long countParticipantsByUserId(String userId) {
        return participantRepository.countByUserId(userId);
    }

    public long countExecutionsByUserId(String userId) {
        return executionRepository.countByUserId(userId);
    }

    public long countCompletedExecutionsByUserId(String userId) {
        return executionRepository.countCompletedByUserId(userId);
    }
}
