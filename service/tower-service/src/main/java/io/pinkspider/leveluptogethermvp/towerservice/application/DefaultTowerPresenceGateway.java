package io.pinkspider.leveluptogethermvp.towerservice.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * LUT-632: LUT-631(Redis presence) 전까지의 임시 구현 — 모두 연결된 것으로 본다. 그래서 직접 입장한 유저는 미션이 끝나도 휴식 중으로 남고(퇴장은
 * {@code leave}/{@code vacate} 또는 631 의 끊김 감지), 자동 착석자는 연결과 무관하게 퇴장한다.
 */
@Component
@ConditionalOnMissingBean(
        value = TowerPresenceGateway.class,
        ignored = DefaultTowerPresenceGateway.class)
public class DefaultTowerPresenceGateway implements TowerPresenceGateway {

    @Override
    public boolean isConnected(String userId) {
        return true;
    }
}
