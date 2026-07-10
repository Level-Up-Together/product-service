package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 상점 아이템 어드민 서비스 (QA-225)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "gamificationTransactionManager")
public class ShopItemAdminService {

    private final ShopItemRepository shopItemRepository;
    private final ShopItemImageStorageService imageStorageService;

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public ShopItemAdminPageResponse searchShopItems(
            String keyword, ShopItemType itemType, TitleRarity rarity, Boolean isActive, Pageable pageable) {
        Page<ShopItemAdminResponse> page = shopItemRepository
            .search(keyword, itemType, rarity, isActive, pageable)
            .map(ShopItemAdminResponse::from);
        return ShopItemAdminPageResponse.from(page);
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public ShopItemAdminResponse getShopItem(Long id) {
        return ShopItemAdminResponse.from(findById(id));
    }

    public ShopItemAdminResponse createShopItem(ShopItemAdminRequest request) {
        if (shopItemRepository.existsByName(request.getName())) {
            throw new CustomException("400", "error.shop_item.duplicate_name");
        }

        ShopItem item = ShopItem.builder()
            .name(request.getName())
            .nameEn(request.getNameEn())
            .nameAr(request.getNameAr())
            .nameJa(request.getNameJa())
            .itemType(request.getItemType())
            .rarity(request.getRarity())
            .imageUrl(request.getImageUrl())
            .price(request.getPrice())
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .build();

        ShopItem saved = shopItemRepository.save(item);
        log.info("상점 아이템 생성: id={}, name={}, type={}", saved.getId(), saved.getName(), saved.getItemType());
        return ShopItemAdminResponse.from(saved);
    }

    public ShopItemAdminResponse updateShopItem(Long id, ShopItemAdminRequest request) {
        ShopItem item = findById(id);

        if (!item.getName().equals(request.getName())
            && shopItemRepository.existsByName(request.getName())) {
            throw new CustomException("400", "error.shop_item.duplicate_name");
        }

        // 이미지가 교체되면 기존 파일 삭제
        String previousImageUrl = item.getImageUrl();
        if (previousImageUrl != null && !previousImageUrl.equals(request.getImageUrl())) {
            imageStorageService.delete(previousImageUrl);
        }

        item.setName(request.getName());
        item.setNameEn(request.getNameEn());
        item.setNameAr(request.getNameAr());
        item.setNameJa(request.getNameJa());
        item.setItemType(request.getItemType());
        item.setRarity(request.getRarity());
        item.setImageUrl(request.getImageUrl());
        item.setPrice(request.getPrice());
        if (request.getIsActive() != null) {
            item.setIsActive(request.getIsActive());
        }

        ShopItem saved = shopItemRepository.save(item);
        log.info("상점 아이템 수정: id={}, name={}", id, saved.getName());
        return ShopItemAdminResponse.from(saved);
    }

    public ShopItemAdminResponse toggleActiveStatus(Long id) {
        ShopItem item = findById(id);
        item.setIsActive(!item.getIsActive());
        ShopItem saved = shopItemRepository.save(item);
        log.info("상점 아이템 활성 상태 변경: id={}, isActive={}", id, saved.getIsActive());
        return ShopItemAdminResponse.from(saved);
    }

    public void deleteShopItem(Long id) {
        ShopItem item = findById(id);
        String imageUrl = item.getImageUrl();
        shopItemRepository.deleteById(id);
        imageStorageService.delete(imageUrl);
        log.info("상점 아이템 삭제: id={}", id);
    }

    /**
     * 이미지 업로드 — 저장 후 URL 반환. 생성/수정 요청의 image_url로 사용한다.
     */
    public String uploadImage(MultipartFile file) {
        return imageStorageService.store(file);
    }

    private ShopItem findById(Long id) {
        return shopItemRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.shop_item.not_found"));
    }
}
