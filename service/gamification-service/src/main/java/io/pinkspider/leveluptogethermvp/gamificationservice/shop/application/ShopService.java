package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.DiamondService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemPurchaseResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.UserItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.UserItemRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-327: 상점 — 판매 아이템 조회/구매.
 *
 * <p>구매는 검증 → 다이아 차감 → 인벤토리 지급이 한 트랜잭션이라 지급 실패 시 차감도 롤백된다.
 * 동시 구매 race는 다이아 쪽은 UserDiamond 낙관적 락(@Version), 지급 쪽은 uk_user_item 제약이 방어한다.
 * UserItemService.grantItem(중복 insert 흡수)과 달리 중복을 실패로 처리해 이중 차감을 막는다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class ShopService {

    private final ShopItemRepository shopItemRepository;
    private final UserItemRepository userItemRepository;
    private final DiamondService diamondService;

    /** 판매중 아이템 전체 — 희귀도(일반→신화) → 가격 → ID 오름차순, 보유 여부 포함 */
    public List<ShopItemResponse> getShopItems(String userId) {
        Set<Long> ownedItemIds =
            Set.copyOf(userItemRepository.findShopItemIdsByUserId(userId));
        return shopItemRepository.findByIsActiveTrue().stream()
            .sorted(Comparator.comparingInt((ShopItem item) -> item.getRarity().ordinal())
                .thenComparing(ShopItem::getPrice)
                .thenComparing(ShopItem::getId))
            .map(item -> ShopItemResponse.from(item, ownedItemIds.contains(item.getId())))
            .collect(Collectors.toList());
    }

    /** 아이템 구매 — 다이아 차감 + 인벤토리 지급 */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public ShopItemPurchaseResponse purchaseItem(String userId, Long shopItemId) {
        ShopItem shopItem = shopItemRepository.findById(shopItemId)
            .filter(ShopItem::getIsActive)
            .orElseThrow(() -> new CustomException("120603", "error.shop.item_not_available"));

        if (userItemRepository.existsByUserIdAndShopItemId(userId, shopItemId)) {
            throw new CustomException("120604", "error.shop.already_owned");
        }

        int balance;
        try {
            // LUT-328: 가격 0원 구매도 어드민 구매이력(diamond_history SHOP)에 남도록 항상 기록
            balance = diamondService.spendDiamonds(
                userId, shopItem.getPrice(), shopItem.getId(), shopItem.getName());
        } catch (IllegalStateException e) {
            throw new CustomException("120605", "error.shop.insufficient_diamond");
        }

        try {
            userItemRepository.saveAndFlush(UserItem.builder()
                .userId(userId)
                .shopItem(shopItem)
                .acquiredAt(LocalDateTime.now())
                .build());
        } catch (DataIntegrityViolationException e) {
            // 동시 구매 race — 예외 전파로 트랜잭션이 롤백되어 차감분도 복구된다
            throw new CustomException("120604", "error.shop.already_owned");
        }

        log.info("아이템 구매: userId={}, shopItemId={}, price={}, balance={}",
            userId, shopItemId, shopItem.getPrice(), balance);
        return ShopItemPurchaseResponse.of(shopItemId, shopItem.getPrice(), balance);
    }
}
