package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import java.time.LocalDateTime;

/**
 * LUT-239: 어드민 길드 미션 수행 기록 네이티브 UNION 쿼리 프로젝션.
 * 길드 소속 미션의 수행 건을 mission_execution(일반) + daily_mission_instance(고정)에서 합친다.
 */
public interface GuildMissionEventRow {

    Long getParticipantId();

    Long getMissionId();

    String getMissionTitle();

    /** 수행 기록 출처 — mission_execution 이면 false(일반), daily_mission_instance 이면 true(고정) */
    Boolean getIsPinned();

    /** 수행자 user ID */
    String getUserId();

    /** ExecutionStatus 원본 문자열 (IN_PROGRESS/COMPLETED/...) */
    String getStatus();

    Integer getExpEarned();

    LocalDateTime getEventAt();
}
