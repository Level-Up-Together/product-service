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
 * LUT-516/528: 장착 아이템 푸시 유저 단위 발송 처리. 스케줄러와 분리한 별도 빈이라 {@code @Transactional} 이 실제 적용되고(자가호출 아님),
 * 유저별 짧은 트랜잭션이 커밋되는 순간 AFTER_COMMIT 리스너(notification-service)가 발동한다. (user_id, send_date) UNIQUE 로 다중
 * 인스턴스/재실행에도 유저·로컬날짜당 정확히 1회.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemPushDispatchService {

    private final ItemPushSendLogRepository sendLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 대사 풀에서 랜덤 1개 선택 (테스트에서 고정 주입용, package-private setter)
    private Random random = new Random();

    void setRandom(Random random) {
        this.random = random;
    }

    /**
     * 한 유저의 하루 발송 시도. 이미 오늘 발송했으면 아무것도 하지 않는다. 아이템의 대사 풀에서 랜덤 1개를 고른다. (LUT-528: 상태별 대사 선택은 발송 로직
     * 티켓에서 도입 — 현재는 랜덤 1개.)
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void trySendForUser(
            String userId,
            ShopItem item,
            LocalDate localDate,
            String slot,
            List<ItemPushMessage> messages) {
        // 1) 빠른 중복 체크 — 유저·로컬날짜당 1건
        if (sendLogRepository.existsByUserIdAndSendDate(userId, localDate)) {
            return;
        }

        // 2) 대사 풀에서 랜덤 1개
        ItemPushMessage picked = messages.get(random.nextInt(messages.size()));

        // 3) 발송 선점 — 유니크 위반이면 다른 인스턴스가 이미 처리한 것이므로 조용히 종료
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
