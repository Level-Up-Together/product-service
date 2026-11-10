package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.global.event.TowerFloorUnlockedEvent;
import io.pinkspider.global.event.UserLevelUpEvent;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * LUT-634: 레벨업으로 새 층이 열리면 {@link TowerFloorUnlockedEvent} 를 낸다(알림은 notification-service).
 *
 * <p>판정은 층 번호가 아니라 **입장 레벨**: 공개 층 중 "이전 레벨 < 입장 레벨 <= 새 레벨". 제한 없는 층(entryLevel null)은 대상이 아니다. 한
 * 번에 여러 층이 열리면(입장 레벨이 같은 층 포함) 가장 높은 층 1건만 낸다. 이 리스너는 gamification 의 커밋 뒤 콜백에서 돌므로 재발행 이벤트의 수신자는
 * {@code fallbackExecution = true} 여야 한다(LUT-653).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerFloorUnlockListener {

    private final TowerFloorRepository towerFloorRepository;
    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLevelUp(UserLevelUpEvent event) {
        try {
            unlockedFloor(event.previousLevel(), event.newLevel())
                    .ifPresent(
                            floor -> {
                                eventPublisher.publishEvent(
                                        new TowerFloorUnlockedEvent(
                                                event.userId(),
                                                floor.getFloorNumber(),
                                                floor.getName(),
                                                floor.getNameEn(),
                                                floor.getNameAr(),
                                                floor.getNameJa(),
                                                event.newLevel()));
                                log.info(
                                        "[tower] 층 해제: userId={} floor={} level {}->{}",
                                        event.userId(),
                                        floor.getFloorNumber(),
                                        event.previousLevel(),
                                        event.newLevel());
                            });
        } catch (Exception e) {
            log.warn("[tower] 층 해제 판정 실패: userId={} error={}", event.userId(), e.getMessage());
        }
    }

    /** 공개 층 중 입장 레벨이 (이전 레벨, 새 레벨] 인 층 — 제한 없는 층 제외, 여러 층이면 가장 높은 층 */
    Optional<TowerFloor> unlockedFloor(int previousLevel, int newLevel) {
        if (newLevel <= previousLevel) return Optional.empty();
        return towerFloorRepository.findAllByIsPublicTrueOrderByFloorNumberAsc().stream()
                .filter(floor -> floor.getEntryLevel() != null)
                .filter(
                        floor ->
                                previousLevel < floor.getEntryLevel()
                                        && floor.getEntryLevel() <= newLevel)
                .max(Comparator.comparingInt(TowerFloor::getFloorNumber));
    }
}
