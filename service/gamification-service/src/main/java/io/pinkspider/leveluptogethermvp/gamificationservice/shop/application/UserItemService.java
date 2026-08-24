package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.UserItemResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.UserItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.UserItemRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-296: 아이템 인벤토리 — 유저 보유 아이템 조회/지급/장착.
 *
 * <p>장착은 아이템 타입당 최대 1개 — 같은 타입의 기존 장착 아이템을 해제한 뒤 장착한다.
 * 기본 아이템(ID:2, 레벨업 사용 설명서)은 가입 시 지급되며, 마이그레이션 이전 기존 유저는
 * 인벤토리 첫 조회 시 lazy 지급으로 보완한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class UserItemService {

    /** 기본 지급 아이템 ID (dev/prod 동일 — V005 마이그레이션과 짝) */
    static final Long DEFAULT_ITEM_ID = 2L;

    private final UserItemRepository userItemRepository;
    private final ShopItemRepository shopItemRepository;

    /** 내 인벤토리 조회 — 기본 아이템 미보유 시 lazy 지급 후 반환 */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public List<UserItemResponse> getMyItems(String userId) {
        grantDefaultItems(userId);
        return userItemRepository.findByUserIdWithItem(userId).stream()
            .map(UserItemResponse::from)
            .collect(Collectors.toList());
    }

    /**
     * 아이템 장착 — 충돌 타입(같은 타입 + BASIC↔FULL, LUT-308)의 기존 장착을 해제하고 장착한다.
     * 이미 장착중이면 그대로 반환 (프론트는 버튼 disabled 처리).
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserItemResponse equipItem(String userId, Long shopItemId) {
        UserItem userItem = userItemRepository.findByUserIdAndShopItemId(userId, shopItemId)
            .orElseThrow(() -> new CustomException("120601", "error.useritem.not_owned"));

        if (Boolean.TRUE.equals(userItem.getIsEquipped())) {
            return UserItemResponse.from(userItem);
        }

        // 충돌 타입의 기존 장착 아이템 해제 (LUT-308: BASIC/FULL 상호 배타)
        userItemRepository
            .findEquippedByUserIdAndItemTypeIn(
                userId, userItem.getShopItem().getItemType().equipConflictTypes())
            .forEach(UserItem::unequip);

        userItem.equip();
        log.info("아이템 장착: userId={}, shopItemId={}, type={}",
            userId, shopItemId, userItem.getShopItem().getItemType());
        return UserItemResponse.from(userItem);
    }

    /**
     * 아이템 장착해제 (LUT-299) — 미보유면 예외, 이미 미장착이면 그대로 반환 (멱등).
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserItemResponse unequipItem(String userId, Long shopItemId) {
        UserItem userItem = userItemRepository.findByUserIdAndShopItemId(userId, shopItemId)
            .orElseThrow(() -> new CustomException("120601", "error.useritem.not_owned"));

        if (Boolean.FALSE.equals(userItem.getIsEquipped())) {
            return UserItemResponse.from(userItem);
        }

        userItem.unequip();
        log.info("아이템 장착해제: userId={}, shopItemId={}", userId, shopItemId);
        return UserItemResponse.from(userItem);
    }

    /**
     * 아이템 지급 (멱등 — 이미 보유 시 no-op). 동시 지급 race는
     * uk_user_item 제약 + DataIntegrityViolationException 흡수로 방어한다.
     *
     * @return 신규 지급된 경우 해당 ShopItem, 이미 보유·동시 지급 race 로 no-op 이면 null —
     *     호출자가 획득 알림 발행 여부를 판단하는 근거 (LUT-410)
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public ShopItem grantItem(String userId, Long shopItemId) {
        if (userItemRepository.existsByUserIdAndShopItemId(userId, shopItemId)) {
            return null;
        }
        ShopItem shopItem = shopItemRepository.findById(shopItemId)
            .orElseThrow(() -> new CustomException("120602", "error.useritem.item_not_found"));

        try {
            userItemRepository.saveAndFlush(UserItem.builder()
                .userId(userId)
                .shopItem(shopItem)
                .acquiredAt(LocalDateTime.now())
                .build());
            log.info("아이템 지급: userId={}, shopItemId={}", userId, shopItemId);
            return shopItem;
        } catch (DataIntegrityViolationException e) {
            log.debug("아이템 중복 지급 스킵 (동시 요청): userId={}, shopItemId={}", userId, shopItemId);
            return null;
        }
    }

    /** 장착중 아이템 목록 (파사드 → 프로필 응답용) */
    public List<UserItem> getEquippedItemEntities(String userId) {
        return userItemRepository.findEquippedByUserId(userId);
    }

    /** 가입 시/인벤토리 조회 시 기본 아이템(ID:2) 지급. 아이템 미등록 등 실패는 로그만 남긴다. */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void grantDefaultItems(String userId) {
        try {
            grantItem(userId, DEFAULT_ITEM_ID);
        } catch (Exception e) {
            log.error("기본 아이템 지급 실패: userId={}, itemId={}, error={}",
                userId, DEFAULT_ITEM_ID, e.getMessage());
        }
    }
}
