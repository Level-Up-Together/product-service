package io.pinkspider.leveluptogethermvp.missionservice.api.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * LUT-849: 길드 미션 '다시하기'(나의 미션에 다시 넣기) 실패 — 플랫폼 {@code CustomException} 은 항상 500 이라, 입력 오류(400)·권한
 * 없음(403)을 상태로 실어 보낸다. {@link MissionIdempotencyExceptionHandler} 가 같은 방식으로 응답한다.
 */
@Getter
public class GuildMissionRejoinException extends RuntimeException {

    /** 050601 — 길드 미션이 아님 (400) */
    public static final String CODE_NOT_GUILD_MISSION = "050601";

    /** 050602 — 없거나 삭제됐거나, 아직 열리지 않았거나(DRAFT) 끝난(COMPLETED·CANCELLED) 길드 미션 (400) */
    public static final String CODE_UNAVAILABLE = "050602";

    /** 050603 — 그 길드의 활동 멤버가 아님 (403) */
    public static final String CODE_NOT_GUILD_MEMBER = "050603";

    /** 050604 — 이미 완료한 일반 길드 미션(1회성)이라 다시 넣을 수 없음 (400) */
    public static final String CODE_ALREADY_COMPLETED = "050604";

    private final String code;
    private final HttpStatus status;

    private GuildMissionRejoinException(String code, String messageKey, HttpStatus status) {
        super(messageKey);
        this.code = code;
        this.status = status;
    }

    public static GuildMissionRejoinException notGuildMission() {
        return new GuildMissionRejoinException(
                CODE_NOT_GUILD_MISSION,
                "error.mission.rejoin.not_guild_mission",
                HttpStatus.BAD_REQUEST);
    }

    public static GuildMissionRejoinException unavailable() {
        return new GuildMissionRejoinException(
                CODE_UNAVAILABLE, "error.mission.rejoin.unavailable", HttpStatus.BAD_REQUEST);
    }

    public static GuildMissionRejoinException notGuildMember() {
        return new GuildMissionRejoinException(
                CODE_NOT_GUILD_MEMBER,
                "error.mission.rejoin.not_guild_member",
                HttpStatus.FORBIDDEN);
    }

    public static GuildMissionRejoinException alreadyCompleted() {
        return new GuildMissionRejoinException(
                CODE_ALREADY_COMPLETED,
                "error.mission.rejoin.already_completed",
                HttpStatus.BAD_REQUEST);
    }
}
