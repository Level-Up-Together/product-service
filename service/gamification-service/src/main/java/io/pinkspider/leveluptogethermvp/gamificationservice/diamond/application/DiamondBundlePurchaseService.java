package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundlePurchaseRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundlePurchaseResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.IapVerificationResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.UserDiamondBalanceResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity.DiamondBundle;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity.DiamondBundlePurchase;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure.DiamondBundlePurchaseRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure.DiamondBundleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * LUT-354: 핑크다이아 묶음상품 IAP 구매 — 영수증 검증 + 멱등 지급.
 *
 * <p>외부 영수증 검증(HTTP)은 트랜잭션 밖에서 수행하고, 구매 기록 + 지급만
 * {@link DiamondBundlePurchaseTxService} 한 트랜잭션으로 묶는다.
 * 멱등성은 store_transaction_id 유니크 제약이 보장 — 재요청은 기존 기록을 돌려준다.
 *
 * <p>LUT-504: 같은 유저의 지급이 동시에 들어오면(앱 기동 시 고아 트랜잭션 여러 건 재전달)
 * {@code UserDiamond} 낙관적 락(@Version)이 충돌한다. 트랜잭션 전체가 롤백돼 구매 기록도 남지 않으므로
 * 짧게 재시도하면 두 건 모두 지급된다 — 500 으로 끝내면 클라이언트가 finish 를 못 해 큐에 남는다.
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

    /** 낙관적 락 충돌 재시도 횟수 — 동시 재전달은 보통 2~3건이라 이 안에서 해소된다 */
    static final int OPTIMISTIC_LOCK_MAX_ATTEMPTS = 3;

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
        IapVerificationResult verification = iapVerificationService.verify(request);
        String transactionId = verification.transactionId();

        // 멱등 선체크 — 이미 지급된 트랜잭션이면 지급 없이 현재 잔액 반환
        var existing = purchaseRepository.findByStoreTransactionId(transactionId);
        if (existing.isPresent()) {
            return alreadyProcessed(userId, existing.get());
        }

        try {
            int balanceAfter = recordAndGrantWithRetry(userId, bundle, request, verification);
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

    /**
     * LUT-504: 낙관적 락 충돌은 재시도한다. 유니크 위반(멱등)은 그대로 던져 호출부가 기존 기록으로 응답하게 둔다.
     * 마지막 시도까지 충돌하면 예외를 그대로 올린다 — 클라이언트는 pending 을 유지해 다음 기회에 재전달한다.
     */
    private int recordAndGrantWithRetry(
            String userId, DiamondBundle bundle,
            DiamondBundlePurchaseRequest request, IapVerificationResult verification) {
        for (int attempt = 1; ; attempt++) {
            try {
                return purchaseTxService.recordAndGrant(userId, bundle, request, verification);
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt >= OPTIMISTIC_LOCK_MAX_ATTEMPTS) {
                    log.warn("핑크다이아 지급 낙관적 락 충돌 — 재시도 소진: userId={}, tx={}, attempts={}",
                        userId, verification.transactionId(), attempt);
                    throw e;
                }
                log.info("핑크다이아 지급 낙관적 락 충돌 — 재시도: userId={}, tx={}, attempt={}",
                    userId, verification.transactionId(), attempt);
                backoff(attempt);
            }
        }
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(50L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
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
