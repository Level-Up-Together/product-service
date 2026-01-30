package io.pinkspider.leveluptogethermvp.missionservice.saga;

import io.pinkspider.global.saga.SagaContext;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 고정 미션 완료 Saga 컨텍스트
 *
 * 고정 미션(DailyMissionInstance) 수행 완료 시 필요한 모든 데이터를 담고 있음
 */
@Getter
@Setter
public class PinnedMissionCompletionContext extends SagaContext {

    public static final String SAGA_TYPE = "PINNED_MISSION_COMPLETION";

    // === Input Data ===
    private Long instanceId;
    private String userId;
    private String note;
    private boolean shareToFeed = false;

    // === Loaded Data ===
    private DailyMissionInstance instance;
    private MissionParticipant participant;
    private Mission mission;

    // === Calculated Data ===
    private int expEarned;
    private Long categoryId;
    private String categoryName;
    private String missionTitle;
    private LocalDate instanceDate;

    // === Result Data ===
    private Integer userLevelBefore;
    private Integer userLevelAfter;
    private Long createdFeedId;
    private Long nextInstanceId;

    public PinnedMissionCompletionContext(String userId) {
        super(SAGA_TYPE, userId);
        this.userId = userId;
    }

    public PinnedMissionCompletionContext(Long instanceId, String userId, String note, boolean shareToFeed) {
        super(SAGA_TYPE, userId);
        this.instanceId = instanceId;
        this.userId = userId;
        this.note = note;
        this.shareToFeed = shareToFeed;
    }

    /**
     * 보상 데이터 키 상수
     */
    public static class CompensationKeys {
        public static final String USER_EXP_BEFORE = "userExpBefore";
        public static final String USER_LEVEL_BEFORE = "userLevelBefore";
        public static final String INSTANCE_STATUS_BEFORE = "instanceStatusBefore";
        public static final String USER_STATS_BEFORE = "userStatsBefore";
    }
}
