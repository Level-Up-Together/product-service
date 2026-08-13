package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundlePurchaseRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundlePurchaseResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.UserDiamondBalanceResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity.DiamondBundle;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity.DiamondBundlePurchase;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure.DiamondBundlePurchaseRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure.DiamondBundleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * LUT-354: 핑크다이아 묶음상품 IAP 구매 — 영수증 검증 + 멱등 지급.
 *
 * <p>외부 영수증 검증(HTTP)은 트랜잭션 밖에서 수행하고, 구매 기록 + 지급만
 * {@link DiamondBundlePurchaseTxService} 한 트랜잭션으로 묶는다.
 * 멱등성은 store_transaction_id 유니크 제약이 보장 — 재요청은 기존 기록을 돌려준다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiamondBundlePurchaseService {

    private final DiamondBundleRepository diamondBundleRepository;
    private final DiamondBundlePurchaseRepository purchaseRepository;
    private final IapVerificationService iapVerificationService;
    private final DiamondBundlePurchaseTxService purchaseTxService;
    private final DiamondService diamondService;

    public DiamondBundlePurchaseResponse purchase(
            String userId, Long bundleId, DiamondBundlePurchaseRequest request) {
        DiamondBundle bundle = diamondBundleRepository.findById(bundleId)
            .filter(DiamondBundle::getIsActive)
            .orElseThrow(() -> new CustomException("120704", "error.iap.bundle_not_available"));

        // 스토어 상품 매핑 검증 — 등록된 상품 ID와 다른 영수증으로 지급받는 것을 차단
        if (bundle.getStoreProductId() == null
            || !bundle.getStoreProductId().equals(request.getStoreProductId())) {
            throw new CustomException("120703", "error.iap.product_mismatch");
        }

        // 영수증 검증 (외부 HTTP — 트랜잭션 밖)
        String transactionId = iapVerificationService.verify(request);

        // 멱등 선체크 — 이미 지급된 트랜잭션이면 지급 없이 현재 잔액 반환
        var existing = purchaseRepository.findByStoreTransactionId(transactionId);
        if (existing.isPresent()) {
            return alreadyProcessed(userId, existing.get());
        }

        try {
            int balanceAfter = purchaseTxService.recordAndGrant(userId, bundle, request, transactionId);
            UserDiamondBalanceResponse balances = diamondService.getBalances(userId);
            log.info("핑크다이아 묶음 구매 완료: userId={}, bundleId={}, count={}, tx={}",
                userId, bundleId, bundle.getDiamondCount(), transactionId);
            return new DiamondBundlePurchaseResponse(
                bundle.getId(), bundle.getDiamondCount(),
                balanceAfter, balances.getBlueBalance(), balances.getPinkBalance(), false);
        } catch (DataIntegrityViolationException e) {
            // 동시 재요청 race — 유니크 제약이 이중 지급을 막았다. 기존 기록으로 응답.
            DiamondBundlePurchase processed = purchaseRepository
                .findByStoreTransactionId(transactionId)
                .orElseThrow(() -> new CustomException("120702", "error.iap.verification_failed"));
            return alreadyProcessed(userId, processed);
        }
    }

    private DiamondBundlePurchaseResponse alreadyProcessed(
            String userId, DiamondBundlePurchase purchase) {
        UserDiamondBalanceResponse balances = diamondService.getBalances(userId);
        log.info("핑크다이아 묶음 구매 멱등 재요청: userId={}, tx={}", userId, purchase.getStoreTransactionId());
        return new DiamondBundlePurchaseResponse(
            purchase.getBundleId(), purchase.getDiamondCount(),
            balances.getBalance(), balances.getBlueBalance(), balances.getPinkBalance(), true);
    }
}
