package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import io.pinkspider.global.event.ItemEquippedEvent;
import io.pinkspider.global.event.ItemGrantedByAdminEvent;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemGrantAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemGrantAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemGrantAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemGrant;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemGrantRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.UserItemRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-472: 관리자 아이템 수동 지급 (이벤트 보상·CS 보상·테스터 지급).
 *
 * <p>지급은 상점 구매와 같은 인벤토리 경로({@link UserItemService#grantItem})를 재사용하되 다이아 차감이
 * 없고, 별도 이력(item_grant)을 남긴다. 이미 보유 중이면 멱등 no-op (이력 미기록, already_owned 응답).
 * 회수는 이력에 revoked 마킹 + 인벤토리 삭제(장착 중이면 해제 이벤트로 홈 캐시 무효화).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class ItemGrantAdminService {

    private final ItemGrantRepository itemGrantRepository;
    private final ShopItemRepository shopItemRepository;
    private final UserItemRepository userItemRepository;
    private final UserItemService userItemService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(transactionManager = "gamificationTransactionManager")
    public ItemGrantAdminResponse grantItem(ItemGrantAdminRequest request, Long adminId) {
        String userId = request.getUserId();
        Long shopItemId = request.getShopItemId();

        // 칭호 부여와 달리 유저 존재를 검증한다 — UUID 오타로 고아 인벤토리 행이 생기는 것 방지
        Users user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException("120903", "error.itemgrant.user_not_found"));

        ShopItem shopItem = shopItemRepository.findById(shopItemId)
            .orElseThrow(() -> new CustomException("120602", "error.useritem.item_not_found"));

        // 멱등: 이미 보유(구매·시즌 보상 포함) 시 no-op — 이력도 남기지 않는다
        ShopItem granted = userItemService.grantItem(userId, shopItemId);
        if (granted == null) {
            log.info("관리자 아이템 부여 스킵(이미 보유): userId={}, shopItemId={}, adminId={}",
                userId, shopItemId, adminId);
            return ItemGrantAdminResponse.alreadyOwned(userId, user.getNickname(), shopItemId);
        }

        ItemGrant saved = itemGrantRepository.save(
            ItemGrant.create(userId, shopItem, request.getReason(), adminId));
        log.info("관리자 아이템 부여: userId={}, shopItemId={}, adminId={}", userId, shopItemId, adminId);

        eventPublisher.publishEvent(new ItemGrantedByAdminEvent(
            userId, shopItemId,
            shopItem.getName(), shopItem.getNameEn(), shopItem.getNameAr(), shopItem.getNameJa()));

        return ItemGrantAdminResponse.from(saved, user.getNickname());
    }

    /**
     * 회수 — 이력은 revoked 마킹으로 보존하고 인벤토리에서 삭제한다.
     * 장착 중이면 해제 상태로 삭제되므로 캐시 무효화 이벤트(LUT-427)를 발행한다.
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void revokeItem(Long itemGrantId, Long adminId) {
        ItemGrant grant = itemGrantRepository.findById(itemGrantId)
            .orElseThrow(() -> new CustomException("120904", "error.itemgrant.not_found"));

        if (grant.isRevoked()) {
            throw new CustomException("120905", "error.itemgrant.already_revoked");
        }

        userItemRepository.findByUserIdAndShopItemId(grant.getUserId(), grant.getShopItem().getId())
            .ifPresent(userItem -> {
                boolean wasEquipped = Boolean.TRUE.equals(userItem.getIsEquipped());
                userItemRepository.delete(userItem);
                if (wasEquipped) {
                    eventPublisher.publishEvent(new ItemEquippedEvent(
                        grant.getUserId(), grant.getShopItem().getId(), false));
                }
            });

        grant.revoke(adminId);
        log.info("관리자 아이템 회수: itemGrantId={}, userId={}, shopItemId={}, adminId={}",
            itemGrantId, grant.getUserId(), grant.getShopItem().getId(), adminId);
    }

    public ItemGrantAdminPageResponse getGrantHistory(String keyword, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "grantedAt"));
        Page<ItemGrant> grantPage = itemGrantRepository.findGrantHistory(keyword, pageable);

        List<String> userIds = grantPage.getContent().stream()
            .map(ItemGrant::getUserId)
            .distinct()
            .toList();
        Map<String, String> nicknameMap = getUserNicknameMap(userIds);

        Page<ItemGrantAdminResponse> responsePage = grantPage.map(
            ig -> ItemGrantAdminResponse.from(ig, nicknameMap.get(ig.getUserId())));

        return ItemGrantAdminPageResponse.from(responsePage);
    }

    private Map<String, String> getUserNicknameMap(List<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllByIdIn(userIds).stream()
            .collect(Collectors.toMap(Users::getId, Users::getNickname, (a, b) -> a));
    }
}
