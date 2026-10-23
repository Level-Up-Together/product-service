package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source;

/** LUT-601: 하루치 미션 완료 수와 완료한 유니크 유저 수 (mission_db) */
public record MissionDailyCounts(int completions, int activeUsers) {
    public static final MissionDailyCounts ZERO = new MissionDailyCounts(0, 0);
}
