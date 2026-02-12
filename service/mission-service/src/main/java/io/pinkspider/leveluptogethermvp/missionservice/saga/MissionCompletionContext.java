package io.pinkspider.leveluptogethermvp.missionservice.saga;

import io.pinkspider.global.saga.SagaContext;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * 미션 완료 Saga 통합 컨텍스트
 *
 * 일반 미션(MissionExecution)과 고정 미션(DailyMissionInstance) 모두 처리
 * isPinned 플래그로 분기
 */
@Getter
@Setter
public class MissionCompletionContext extends SagaContext {

    public static final String SAGA_TYPE = "MISSION_COMPLETION";

    // === Common Input Data ===
    private String userId;
    private String note;
    private boolean shareToFeed = false;
    private boolean pinned = false;

    // === Regular Mission Input ===
    private Long executionId;

    // === Pinned Mission Input ===
    private Long instanceId;

    // === Common Loaded Data ===
    private MissionParticipant participant;
    private Mission mission;

    // === Regular Loaded Data ===
    private MissionExecution execution;

    // === Pinned Loaded Data ===
    private DailyMissionInstance instance;
    private String missionTitle;
    private Long categoryId;
    private String categoryName;
    private LocalDate instanceDate;

    // === Common Calculated Data ===
    private int userExpEarned;

    // === Regular-only Calculated Data ===
    private int guildExpEarned;
    private Long guildId;

    // === Common Feed Data ===
    private Long createdFeedId;

    // === Common Result Data ===
    private Integer userLevelBefore;
    private Integer userLevelAfter;

    // === Regular-only Result Data ===
    private Long userExperienceHistoryId;
    private Long guildExperienceHistoryId;
    private boolean fullCompletionBonusGranted;
    private int fullCompletionBonusExp;

    // === Pinned-only Result Data ===
    private Long nextInstanceId;

    // === Regular mission constructor ===
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

    // === Pinned mission factory ===
    public static MissionCompletionContext forPinned(Long instanceId, String userId, String note, boolean shareToFeed) {
        MissionCompletionContext ctx = new MissionCompletionContext(userId);
        ctx.instanceId = instanceId;
        ctx.note = note;
        ctx.shareToFeed = shareToFeed;
        ctx.pinned = true;
        return ctx;
    }

    /**
     * 길드 미션 여부 확인 (pinned 미션은 항상 false)
     */
    public boolean isGuildMission() {
        return !pinned && mission != null && mission.isGuildMission() && mission.getGuildId() != null;
    }

    /**
     * 보상 데이터 키 상수 (regular + pinned 통합)
     */
    public static class CompensationKeys {
        // Common
        public static final String USER_EXP_BEFORE = "userExpBefore";
        public static final String USER_LEVEL_BEFORE = "userLevelBefore";
        public static final String USER_STATS_BEFORE = "userStatsBefore";
        // Regular only
        public static final String GUILD_EXP_BEFORE = "guildExpBefore";
        public static final String GUILD_LEVEL_BEFORE = "guildLevelBefore";
        public static final String EXECUTION_STATUS_BEFORE = "executionStatusBefore";
        public static final String PARTICIPANT_PROGRESS_BEFORE = "participantProgressBefore";
        public static final String PARTICIPANT_STATUS_BEFORE = "participantStatusBefore";
        // Pinned only
        public static final String INSTANCE_STATUS_BEFORE = "instanceStatusBefore";
    }
}
