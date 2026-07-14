package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.enums.GuildExpSourceType;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-236: 자동종료 길드 경험치 소급을 <b>수행 기록 1건 단위 트랜잭션</b>으로 처리한다.
 *
 * <p>길드 경험치(guild_db)와 마커(mission_db)는 서로 다른 DB라 원자적일 수 없다.
 * 전체를 하나의 트랜잭션으로 묶으면 중간 실패 시 마커만 롤백되고 길드 경험치는 커밋되어
 * 재실행에서 이중 지급된다. 따라서 건별 REQUIRES_NEW 로 지급+마커를 한 단위로 커밋한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuildExpBackfillExecutor {

    private final MissionExecutionRepository executionRepository;
    private final GuildQueryFacade guildQueryFacade;

    /**
     * @return 소급 지급한 길드 경험치 (지급 없으면 0)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "missionTransactionManager")
    public int grantForExecution(Long executionId) {
        MissionExecution execution = executionRepository.findById(executionId).orElse(null);
        if (execution == null || Boolean.TRUE.equals(execution.getGuildExpGranted())) {
            return 0; // 이미 처리됨 (재실행/동시성 안전)
        }

        Mission mission = execution.getParticipant().getMission();
        Long guildId = mission != null ? mission.getGuildIdAsLong() : null;
        if (guildId == null) {
            execution.setGuildExpGranted(true);
            return 0;
        }

        int exp = execution.getExpEarned();
        if (exp <= 0) {
            execution.setGuildExpGranted(true); // 지급할 것 없음 — 대상에서 제외
            return 0;
        }

        guildQueryFacade.addGuildExperience(
            guildId,
            exp,
            GuildExpSourceType.GUILD_MISSION_EXECUTION,
            mission.getId(),
            execution.getParticipant().getUserId(),
            "미션 자동 종료 길드 경험치 소급: " + mission.getTitle());
        execution.setGuildExpGranted(true);
        return exp;
    }
}
