package io.pinkspider.leveluptogethermvp.towerservice.domain.enums;

/**
 * LUT-630: 탑 인스턴스 점유자의 종류.
 *
 * <ul>
 *   <li>{@code DIRECT} — 직접 입장한 로그인 유저. 미션 중이면 연결이 끊겨도 자리를 유지한다({@code mission_active}).
 *   <li>{@code VISITOR} — 비로그인 방문객. 자리 유지 없음(세션·권한은 LUT-705).
 *   <li>{@code AUTO_SEATED} — 탑에 들어오지 않고 미션을 시작해 서버가 앉힌 유저. 방 정원·배정 계산에서 빠지고 층 인원 집계에는 들어간다.
 * </ul>
 */
public enum TowerOccupantKind {
    DIRECT,
    VISITOR,
    AUTO_SEATED;

    /** 방 정원(인스턴스 상한)에 세는 종류 — 자동 착석자는 세지 않는다 */
    public boolean countsTowardCapacity() {
        return this != AUTO_SEATED;
    }
}
