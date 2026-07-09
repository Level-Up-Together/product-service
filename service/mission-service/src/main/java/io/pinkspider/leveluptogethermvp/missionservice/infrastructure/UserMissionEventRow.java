package io.pinkspider.leveluptogethermvp.missionservice.infrastructure;

import java.time.LocalDateTime;

/**
 * QA-205: 어드민 유저 미션 수행 기록 네이티브 UNION 쿼리 프로젝션.
 * mission_execution(일반 미션) + daily_mission_instance(고정 미션) 를 한 행 = 한 수행 건으로 합친다.
 */
public interface UserMissionEventRow {

    Long getParticipantId();

    Long getMissionId();

    String getMissionTitle();

    /** Mission.type 원본 문자열 (PERSONAL/GUILD) */
    String getMissionType();

    /** Mission.source 원본 문자열 (USER/SYSTEM/GUILD) */
    String getMissionSource();

    String getGuildName();

    /** ExecutionStatus 원본 문자열 (IN_PROGRESS/COMPLETED/...) */
    String getStatus();

    Integer getExpEarned();

    LocalDateTime getEventAt();
}
