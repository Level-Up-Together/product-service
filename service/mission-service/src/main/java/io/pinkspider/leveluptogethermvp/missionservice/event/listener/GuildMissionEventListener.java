package io.pinkspider.leveluptogethermvp.missionservice.event.listener;

import static io.pinkspider.global.config.AsyncConfig.EVENT_EXECUTOR;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.global.event.GuildJoinedEvent;
import io.pinkspider.global.event.GuildMemberRemovedEvent;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionParticipantService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 길드 멤버 변경 이벤트 리스너
 * - 길드 가입 시: 해당 길드의 활성 미션에 자동 참여
 * - 길드 탈퇴/추방 시: 해당 길드 미션 참여 철회
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GuildMissionEventListener {

    private final MissionRepository missionRepository;
    private final MissionParticipantRepository participantRepository;
    private final MissionParticipantService participantService;

    /**
     * 길드 가입 시 해당 길드의 활성 미션에 자동 참여
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(transactionManager = "missionTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void handleGuildMemberJoined(GuildJoinedEvent event) {
        String userId = event.userId();
        Long guildId = event.guildId();

        log.info("길드 가입 이벤트 수신 - 길드 고정 미션 자동 참여 처리: userId={}, guildId={}", userId, guildId);

        // LUT-518: 가입 시에는 진행중인 고정 미션만 자동 생성 (일반 길드 미션은 제외)
        List<Mission> guildMissions = missionRepository.findActivePinnedGuildMissions(
            String.valueOf(guildId),
            List.of(MissionStatus.OPEN, MissionStatus.IN_PROGRESS)
        );

        if (guildMissions.isEmpty()) {
            log.debug("활성 고정 길드 미션 없음: guildId={}", guildId);
            return;
        }

        int enrolled = 0;
        for (Mission mission : guildMissions) {
            try {
                participantService.addGuildMemberAsParticipant(mission, userId);
                enrolled++;
            } catch (Exception e) {
                log.warn("길드 미션 자동 참여 실패: missionId={}, userId={}, error={}",
                    mission.getId(), userId, e.getMessage());
            }
        }

        log.info("길드 미션 자동 참여 완료: userId={}, guildId={}, enrolled={}/{}",
            userId, guildId, enrolled, guildMissions.size());
    }

    /**
     * 길드 탈퇴/추방 시 해당 길드 미션 참여 철회
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(transactionManager = "missionTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void handleGuildMemberRemoved(GuildMemberRemovedEvent event) {
        String userId = event.userId();
        Long guildId = event.guildId();

        log.info("길드 멤버 제거 이벤트 수신 - 길드 미션 참여 정리: userId={}, guildId={}", userId, guildId);

        List<MissionParticipant> participations = participantRepository.findActiveGuildMissionParticipations(
            userId, String.valueOf(guildId)
        );

        if (participations.isEmpty()) {
            log.debug("정리할 길드 미션 참여 없음: userId={}, guildId={}", userId, guildId);
            return;
        }

        int withdrawn = 0;
        for (MissionParticipant participant : participations) {
            try {
                participant.withdraw();
                withdrawn++;
            } catch (Exception e) {
                log.warn("길드 미션 참여 철회 실패: participantId={}, missionId={}, userId={}, error={}",
                    participant.getId(), participant.getMission().getId(), userId, e.getMessage());
            }
        }

        log.info("길드 미션 참여 정리 완료: userId={}, guildId={}, withdrawn={}/{}",
            userId, guildId, withdrawn, participations.size());
    }
}
