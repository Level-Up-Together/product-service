package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import io.pinkspider.global.event.EquippedItemPushDueEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushMessage;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushSendLog;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushSendLogRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-516: 장착 아이템 푸시 유저 단위 발송 처리. 스케줄러와 분리한 별도 빈이라 {@code @Transactional} 이 실제 적용되고(자가호출 아님), 유저별 짧은
 * 트랜잭션이 커밋되는 순간 AFTER_COMMIT 리스너(notification-service)가 발동한다. 슬롯 UNIQUE 로 다중 인스턴스/재실행에도 정확히 1회.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemPushDispatchService {

    private final ItemPushSendLogRepository sendLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 같은 시각 복수 메시지 중 랜덤 1개 선택 (테스트에서 고정 주입용, package-private setter)
    private Random random = new Random();

    void setRandom(Random random) {
        this.random = random;
    }

    /**
     * 한 유저의 한 슬롯 발송 시도. 이미 발송했으면(슬롯 중복) 아무것도 하지 않는다. 같은 슬롯에 여러 메시지면 랜덤 1개만 고른다.
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void trySendForUser(
            String userId,
            ShopItem item,
            LocalDate localDate,
            String slot,
            List<ItemPushMessage> dueForSlot) {
        // 1) 빠른 중복 체크
        if (sendLogRepository.existsByUserIdAndShopItemIdAndSendDateAndSendTime(
                userId, item.getId(), localDate, slot)) {
            return;
        }

        // 2) 같은 시각 메시지 중 랜덤 1개
        ItemPushMessage picked = dueForSlot.get(random.nextInt(dueForSlot.size()));

        // 3) 슬롯 선점 — 유니크 위반이면 다른 인스턴스가 이미 처리한 것이므로 조용히 종료
        try {
            sendLogRepository.saveAndFlush(
                    ItemPushSendLog.record(picked.getId(), item.getId(), userId, localDate, slot));
        } catch (DataIntegrityViolationException e) {
            return;
        }

        // 4) 유저 단위 이벤트 발행 (이 트랜잭션 커밋 후 notification 리스너가 실제 발송)
        eventPublisher.publishEvent(
                new EquippedItemPushDueEvent(
                        userId,
                        item.getId(),
                        picked.getId(),
                        item.getName(),
                        item.getNameEn(),
                        item.getNameAr(),
                        item.getNameJa(),
                        picked.getMessage(),
                        picked.getMessageEn(),
                        picked.getMessageAr(),
                        picked.getMessageJa(),
                        "/mypage/inventory"));
    }
}
