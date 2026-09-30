package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemPushSettingRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemPushSettingResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushSetting;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushSettingRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-528: 장착 아이템 푸시 발송 설정 관리 (어드민 내부 API 백엔드). 발송 시각을 아이템 단위로 1개 둔다. HEAD 타입 아이템에만 등록/수정을 허용한다.
 * 설정이 없으면 해당 아이템은 푸시가 나가지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "gamificationTransactionManager")
public class ItemPushSettingAdminService {

    private final ShopItemRepository shopItemRepository;
    private final ItemPushSettingRepository itemPushSettingRepository;

    /** 발송 설정 조회 — 없으면 null(미발송). */
    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public ItemPushSettingResponse get(Long itemId) {
        requireHead(itemId);
        return itemPushSettingRepository
                .findById(itemId)
                .map(ItemPushSettingResponse::from)
                .orElse(null);
    }

    /** 발송 설정 등록/수정 (아이템당 1행 upsert). */
    public ItemPushSettingResponse upsert(
            Long itemId, ItemPushSettingRequest request, Long adminId) {
        requireHead(itemId);
        ItemPushSetting setting =
                itemPushSettingRepository
                        .findById(itemId)
                        .map(
                                existing -> {
                                    existing.updateSendTime(request.getSendTime());
                                    return existing;
                                })
                        .orElseGet(
                                () -> ItemPushSetting.of(itemId, request.getSendTime(), adminId));
        return ItemPushSettingResponse.from(itemPushSettingRepository.save(setting));
    }

    /** 발송 해제 — 설정 행 삭제. 없으면 no-op. */
    public void delete(Long itemId) {
        requireHead(itemId);
        itemPushSettingRepository.deleteById(itemId);
    }

    /** 아이템 존재 + HEAD 타입 검증 */
    private ShopItem requireHead(Long itemId) {
        ShopItem item =
                shopItemRepository
                        .findById(itemId)
                        .orElseThrow(() -> new CustomException("404", "error.shop_item.not_found"));
        if (item.getItemType() != ShopItemType.HEAD) {
            throw new CustomException("400", "error.item_push.not_head_item");
        }
        return item;
    }
}
