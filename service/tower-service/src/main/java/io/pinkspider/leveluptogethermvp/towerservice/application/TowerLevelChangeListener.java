package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.global.event.UserLevelUpEvent;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** LUT-631: 탑에 있는 유저(자리 유지·자동 착석 포함)의 레벨이 오르면 같은 방에 LEVEL 을 브로드캐스트한다(머리 위 이름표, LUT-639) */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerLevelChangeListener {

    private final TowerPresenceService presenceService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLevelUp(UserLevelUpEvent event) {
        try {
            presenceService.levelChanged(event.userId(), event.newLevel());
        } catch (Exception e) {
            log.warn("[tower] 레벨 변경 반영 실패: userId={} error={}", event.userId(), e.getMessage());
        }
    }
}
