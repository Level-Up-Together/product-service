package io.pinkspider.leveluptogethermvp.towerservice.application;

/**
 * LUT-632: 유저의 탑 실시간 연결 여부. 미션 종료 때 "연결된 유저는 휴식 중으로, 끊긴 유저는 퇴장"을 가르는 데 쓴다.
 *
 * <p>구현은 LUT-631(Redis presence) 이 제공한다. 그 전까지는 {@link DefaultTowerPresenceGateway} 가 모두 연결된 것으로
 * 본다.
 */
public interface TowerPresenceGateway {

    boolean isConnected(String userId);
}
