package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.DiamondService;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.SubscriptionStipend;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.SubscriptionStipendRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-453: 구독 1건의 일일 스티펜드 지급 — 지급 기록 + 다이아 원장을 한 트랜잭션으로.
 *
 * <p>멱등은 (subscription_id, stipend_date) 유니크가 보장한다 — 지급 기록 insert 를
 * saveAndFlush 로 먼저 확정한 뒤 다이아를 지급하므로, 재실행·동시 실행에서는 유니크 위반으로
 * 트랜잭션 전체가 롤백돼 이중 지급이 불가능하다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionStipendTxService {

    private final SubscriptionStipendRepository stipendRepository;
    private final DiamondService diamondService;

    /**
     * @return 실제 지급 여부 (이미 지급된 날이면 false)
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public boolean grantForSubscription(
            UserSubscription subscription, LocalDate stipendDate, int amount) {
        try {
            stipendRepository.saveAndFlush(
                    SubscriptionStipend.builder()
                            .subscriptionId(subscription.getId())
                            .userId(subscription.getUserId())
                            .stipendDate(stipendDate)
                            .amount(amount)
                            .build());
        } catch (DataIntegrityViolationException e) {
            // (구독 ID, 지급일) 기지급 — 멱등 스킵
            log.debug(
                    "스티펜드 기지급 스킵: subscriptionId={}, date={}",
                    subscription.getId(),
                    stipendDate);
            return false;
        }

        diamondService.awardSubscriptionStipend(
                subscription.getUserId(), subscription.getId(), amount);
        return true;
    }
}
