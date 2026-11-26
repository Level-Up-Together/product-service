package io.pinkspider.leveluptogethermvp.missionservice.domain.policy;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;

/**
 * LUT-849: 길드 미션 '다시하기'(나의 미션에 다시 넣기) 판정 — 길드 미션 목록의 {@code can_rejoin} 과 다시하기 API 가 같은 규칙을 쓴다.
 *
 * <p>다시 넣을 수 있는 것: 열린(OPEN)·진행중(IN_PROGRESS)·삭제되지 않은 길드 미션 중 내 참여가 없거나 철회(WITHDRAWN)·실패(FAILED)인 것.
 * 일반 미션은 한 번 완료하면 참여가 COMPLETED 로 끝나므로(1회성) 다시 넣지 못한다 — 다시 넣으면 같은 미션으로 EXP 를 또 받는다. 고정 미션은 완료해도
 * IN_PROGRESS 로 남아 나의 미션에 그대로 있다.
 */
public final class GuildMissionRejoinPolicy {

    private GuildMissionRejoinPolicy() {}

    /** 다시 넣을 수 있는 미션인가 — 열린·진행중이고 삭제되지 않은 길드 미션 */
    public static boolean isRejoinableMission(Mission mission) {
        return mission != null
                && mission.isGuildMission()
                && !Boolean.TRUE.equals(mission.getIsDeleted())
                && (mission.getStatus() == MissionStatus.OPEN
                        || mission.getStatus() == MissionStatus.IN_PROGRESS);
    }

    /** 다시 넣을 수 있는 내 참여 상태인가 — 참여 없음·철회·실패 */
    public static boolean isRejoinableParticipation(MissionParticipant participant) {
        return participant == null
                || participant.getStatus() == ParticipantStatus.WITHDRAWN
                || participant.getStatus() == ParticipantStatus.FAILED;
    }

    /** 내 나의 미션에 있는 참여인가 — 나의 미션 목록과 같은 활성 상태(PENDING·ACCEPTED·IN_PROGRESS) */
    public static boolean isInMyMissions(MissionParticipant participant) {
        return participant != null
                && (participant.getStatus() == ParticipantStatus.PENDING
                        || participant.getStatus() == ParticipantStatus.ACCEPTED
                        || participant.getStatus() == ParticipantStatus.IN_PROGRESS);
    }
}
