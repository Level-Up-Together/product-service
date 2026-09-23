package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemPushMessageRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemPushMessageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushMessage;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushMessageRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-516: 장착 아이템 개별 푸시 메시지 관리 (어드민 내부 API 백엔드). HEAD 타입 아이템에만 등록/수정을 허용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "gamificationTransactionManager")
public class ItemPushMessageAdminService {

    private final ShopItemRepository shopItemRepository;
    private final ItemPushMessageRepository itemPushMessageRepository;

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<ItemPushMessageResponse> list(Long itemId) {
        requireHead(itemId);
        return itemPushMessageRepository.findByShopItemIdOrderByIdAsc(itemId).stream()
                .map(ItemPushMessageResponse::from)
                .toList();
    }

    public ItemPushMessageResponse create(Long itemId, ItemPushMessageRequest request, Long adminId) {
        ShopItem item = requireHead(itemId);
        ItemPushMessage saved =
                itemPushMessageRepository.save(
                        ItemPushMessage.create(
                                item,
                                request.getMessage(),
                                request.getMessageEn(),
                                request.getMessageAr(),
                                request.getMessageJa(),
                                request.getSendTime(),
                                request.getEnabled(),
                                adminId));
        return ItemPushMessageResponse.from(saved);
    }

    public ItemPushMessageResponse update(Long itemId, Long messageId, ItemPushMessageRequest request) {
        ItemPushMessage message = findMessage(itemId, messageId);
        message.update(
                request.getMessage(),
                request.getMessageEn(),
                request.getMessageAr(),
                request.getMessageJa(),
                request.getSendTime());
        if (request.getEnabled() != null) {
            message.changeEnabled(request.getEnabled());
        }
        return ItemPushMessageResponse.from(message);
    }

    public ItemPushMessageResponse toggle(Long itemId, Long messageId) {
        ItemPushMessage message = findMessage(itemId, messageId);
        message.toggle();
        return ItemPushMessageResponse.from(message);
    }

    public void delete(Long itemId, Long messageId) {
        ItemPushMessage message = findMessage(itemId, messageId);
        itemPushMessageRepository.delete(message);
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

    /** 메시지 조회 + 경로 아이템 소속 검증 */
    private ItemPushMessage findMessage(Long itemId, Long messageId) {
        requireHead(itemId);
        ItemPushMessage message =
                itemPushMessageRepository
                        .findById(messageId)
                        .orElseThrow(() -> new CustomException("404", "error.item_push.not_found"));
        if (!message.getShopItem().getId().equals(itemId)) {
            throw new CustomException("404", "error.item_push.not_found");
        }
        return message;
    }
}
