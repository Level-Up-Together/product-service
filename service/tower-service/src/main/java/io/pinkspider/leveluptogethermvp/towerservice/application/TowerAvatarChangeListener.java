package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.global.event.ItemEquippedEvent;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * LUT-737: 장착 아이템을 바꾸면(장착·해제·어드민 회수) 탑에 있는 그 유저의 아바타를 다시 채워 같은 방에 알린다. 장착이 커밋된 뒤라 새 장착 상태를 읽는다.
 * 실패해도 장착 자체에는 영향이 없다(다음 입장 때 다시 채운다).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerAvatarChangeListener {

    private final TowerPresenceService presenceService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onItemEquipped(ItemEquippedEvent event) {
        try {
            presenceService.avatarChanged(event.userId());
        } catch (Exception e) {
            log.warn("[tower] 장착 아이템 변경 반영 실패: userId={} error={}", event.userId(), e.getMessage());
        }
    }
}
