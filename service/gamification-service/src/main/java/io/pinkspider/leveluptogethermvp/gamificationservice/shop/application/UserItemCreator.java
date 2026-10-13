package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.UserItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.UserItemRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-539: user_item 지급 행 생성을 호출자와 **분리된 트랜잭션**에서 수행한다.
 *
 * <p>{@code UserItemService.grantItem} 의 "동시 지급 race 는 uk_user_item +
 * DataIntegrityViolationException 흡수로 방어" 의도는 같은 트랜잭션에서 flush 가 실패하는 순간 세션이 오염돼 무력화됐다 (중복을 흡수해도 커밋
 * 시 UnexpectedRollbackException). insert 를 REQUIRES_NEW 로 분리해 호출자 컨텍스트를 보호한다. ShopItem 은 id 로만 받아
 * 내부 트랜잭션에서 참조를 얻는다 — 호출자가 가진 인스턴스는 다른 영속성 컨텍스트의 것일 수 있다.
 *
 * <p>중복 예외는 삼키지 않고 호출자에게 던진다. 구조는 {@link
 * io.pinkspider.leveluptogethermvp.gamificationservice.stats.application.UserStatsCreator} 와 동일.
 */
@Service
@RequiredArgsConstructor
public class UserItemCreator {

    private final UserItemRepository userItemRepository;
    private final ShopItemRepository shopItemRepository;

    /**
     * 보유 아이템 행을 별도 트랜잭션에서 생성한다.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException 다른 요청이 먼저 지급한 경우
     *     (uk_user_item)
     */
    @Transactional(
            transactionManager = "gamificationTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public void create(String userId, Long shopItemId) {
        ShopItem shopItem = shopItemRepository.getReferenceById(shopItemId);
        userItemRepository.saveAndFlush(
                UserItem.builder()
                        .userId(userId)
                        .shopItem(shopItem)
                        .acquiredAt(LocalDateTime.now())
                        .build());
    }
}
