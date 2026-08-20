package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundleAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundleAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundleAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity.DiamondBundle;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure.DiamondBundlePurchaseRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure.DiamondBundleRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.ShopItemImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 핑크다이아 묶음상품 어드민 서비스 (LUT-356)
 * 이미지는 상점 아이템과 같은 저장 전략(ShopItemImageStorageService)을 공유한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "gamificationTransactionManager")
public class DiamondBundleAdminService {

    private final DiamondBundleRepository diamondBundleRepository;
    private final DiamondBundlePurchaseRepository diamondBundlePurchaseRepository;
    private final ShopItemImageStorageService imageStorageService;

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public DiamondBundleAdminPageResponse searchBundles(
            String keyword, Boolean isActive, Pageable pageable) {
        Page<DiamondBundleAdminResponse> page = diamondBundleRepository
            .search(keyword, isActive, pageable)
            .map(DiamondBundleAdminResponse::from);
        return DiamondBundleAdminPageResponse.from(page);
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public DiamondBundleAdminResponse getBundle(Long id) {
        return DiamondBundleAdminResponse.from(findById(id));
    }

    public DiamondBundleAdminResponse createBundle(DiamondBundleAdminRequest request) {
        if (diamondBundleRepository.existsByName(request.getName())) {
            throw new CustomException("400", "error.diamond_bundle.duplicate_name");
        }

        DiamondBundle bundle = DiamondBundle.builder()
            .name(request.getName())
            .nameEn(request.getNameEn())
            .nameAr(request.getNameAr())
            .nameJa(request.getNameJa())
            .description(request.getDescription())
            .descriptionEn(request.getDescriptionEn())
            .descriptionAr(request.getDescriptionAr())
            .descriptionJa(request.getDescriptionJa())
            .diamondCount(request.getDiamondCount())
            .storeProductId(request.getStoreProductId())
            .imageUrl(request.getImageUrl())
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .build();

        DiamondBundle saved = diamondBundleRepository.save(bundle);
        log.info("다이아 묶음상품 생성: id={}, name={}, count={}",
            saved.getId(), saved.getName(), saved.getDiamondCount());
        return DiamondBundleAdminResponse.from(saved);
    }

    public DiamondBundleAdminResponse updateBundle(Long id, DiamondBundleAdminRequest request) {
        DiamondBundle bundle = findById(id);

        if (!bundle.getName().equals(request.getName())
            && diamondBundleRepository.existsByName(request.getName())) {
            throw new CustomException("400", "error.diamond_bundle.duplicate_name");
        }

        // 이미지가 교체되면 기존 파일 삭제
        String previousImageUrl = bundle.getImageUrl();
        if (previousImageUrl != null && !previousImageUrl.equals(request.getImageUrl())) {
            imageStorageService.delete(previousImageUrl);
        }

        bundle.setName(request.getName());
        bundle.setNameEn(request.getNameEn());
        bundle.setNameAr(request.getNameAr());
        bundle.setNameJa(request.getNameJa());
        bundle.setDescription(request.getDescription());
        bundle.setDescriptionEn(request.getDescriptionEn());
        bundle.setDescriptionAr(request.getDescriptionAr());
        bundle.setDescriptionJa(request.getDescriptionJa());
        bundle.setDiamondCount(request.getDiamondCount());
        bundle.setStoreProductId(request.getStoreProductId());
        bundle.setImageUrl(request.getImageUrl());
        if (request.getIsActive() != null) {
            bundle.setIsActive(request.getIsActive());
        }

        DiamondBundle saved = diamondBundleRepository.save(bundle);
        log.info("다이아 묶음상품 수정: id={}, name={}", id, saved.getName());
        return DiamondBundleAdminResponse.from(saved);
    }

    public DiamondBundleAdminResponse toggleActiveStatus(Long id) {
        DiamondBundle bundle = findById(id);
        bundle.setIsActive(!bundle.getIsActive());
        DiamondBundle saved = diamondBundleRepository.save(bundle);
        log.info("다이아 묶음상품 활성 상태 변경: id={}, isActive={}", id, saved.getIsActive());
        return DiamondBundleAdminResponse.from(saved);
    }

    public void deleteBundle(Long id) {
        DiamondBundle bundle = findById(id);
        // LUT-404: 결제 기록은 재무/CS 증적 — 기록이 있는 번들은 삭제 대신 활성 토글로 판매 중단
        if (diamondBundlePurchaseRepository.existsByBundleId(id)) {
            throw new CustomException("400", "error.diamond_bundle.has_purchases");
        }
        String imageUrl = bundle.getImageUrl();
        diamondBundleRepository.deleteById(id);
        imageStorageService.delete(imageUrl);
        log.info("다이아 묶음상품 삭제: id={}", id);
    }

    /** 이미지 업로드 — 저장 후 URL 반환. 생성/수정 요청의 image_url로 사용한다. */
    public String uploadImage(MultipartFile file) {
        return imageStorageService.store(file);
    }

    private DiamondBundle findById(Long id) {
        return diamondBundleRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.diamond_bundle.not_found"));
    }
}
