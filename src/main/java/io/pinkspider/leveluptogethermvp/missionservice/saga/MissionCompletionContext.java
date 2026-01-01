package io.pinkspider.leveluptogethermvp.missionservice.saga;

import io.pinkspider.global.saga.SagaContext;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import lombok.Getter;
import lombok.Setter;

/**
 * 미션 완료 Saga 컨텍스트
 *
 * 미션 수행 완료 시 필요한 모든 데이터를 담고 있음
 */
@Getter
@Setter
public class MissionCompletionContext extends SagaContext {

    public static final String SAGA_TYPE = "MISSION_COMPLETION";

    // === Input Data ===
    private Long executionId;
    private String userId;
    private String note;

    // === Loaded Data ===
    private MissionExecution execution;
    private MissionParticipant participant;
    private Mission mission;

    // === Calculated Data ===
    private int userExpEarned;
    private int guildExpEarned;
    private boolean isGuildMission;
    private Long guildId;

    // === Feed Data ===
    private boolean shareToFeed = false;  // 피드 공유 여부
    private Long createdFeedId;           // 생성된 피드 ID

    // === Result Data ===
    private Integer userLevelBefore;
    private Integer userLevelAfter;
    private Long userExperienceHistoryId;
    private Long guildExperienceHistoryId;
    private boolean fullCompletionBonusGranted;
    private int fullCompletionBonusExp;

    public MissionCompletionContext(String userId) {
        super(SAGA_TYPE, userId);
        this.userId = userId;
    }

    public MissionCompletionContext(Long executionId, String userId, String note) {
        super(SAGA_TYPE, userId);
        this.executionId = executionId;
        this.userId = userId;
        this.note = note;
    }

    public MissionCompletionContext(Long executionId, String userId, String note, boolean shareToFeed) {
        super(SAGA_TYPE, userId);
        this.executionId = executionId;
        this.userId = userId;
        this.note = note;
        this.shareToFeed = shareToFeed;
    }

    /**
     * 길드 미션 여부 확인
     */
    public boolean isGuildMission() {
        return mission != null && mission.isGuildMission() && mission.getGuildId() != null;
    }

    /**
     * 보상 데이터 키 상수
     */
    public static class CompensationKeys {
        public static final String USER_EXP_BEFORE = "userExpBefore";
        public static final String USER_LEVEL_BEFORE = "userLevelBefore";
        public static final String GUILD_EXP_BEFORE = "guildExpBefore";
        public static final String GUILD_LEVEL_BEFORE = "guildLevelBefore";
        public static final String EXECUTION_STATUS_BEFORE = "executionStatusBefore";
        public static final String PARTICIPANT_PROGRESS_BEFORE = "participantProgressBefore";
        public static final String PARTICIPANT_STATUS_BEFORE = "participantStatusBefore";
        public static final String USER_STATS_BEFORE = "userStatsBefore";
    }
}
