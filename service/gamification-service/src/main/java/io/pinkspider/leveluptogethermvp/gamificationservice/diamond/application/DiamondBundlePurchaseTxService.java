package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundlePurchaseRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity.DiamondBundle;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity.DiamondBundlePurchase;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure.DiamondBundlePurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-354: 구매 기록 + 핑크다이아 지급을 한 트랜잭션으로 묶는다.
 * (검증 HTTP 를 트랜잭션에 포함시키지 않기 위해 오케스트레이터와 분리 —
 * 같은 클래스 내부 호출은 프록시를 타지 않아 별도 빈이 필요하다)
 */
@Service
@RequiredArgsConstructor
public class DiamondBundlePurchaseTxService {

    private final DiamondBundlePurchaseRepository purchaseRepository;
    private final DiamondService diamondService;

    /**
     * @return 지급 후 총잔액 (블루+핑크)
     * @throws org.springframework.dao.DataIntegrityViolationException 동일 트랜잭션 동시 기록 시
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public int recordAndGrant(
            String userId, DiamondBundle bundle,
            DiamondBundlePurchaseRequest request, String transactionId) {
        // saveAndFlush 로 유니크 위반을 이 자리에서 감지 — 지급 전에 멱등이 판정된다
        purchaseRepository.saveAndFlush(DiamondBundlePurchase.builder()
            .userId(userId)
            .bundleId(bundle.getId())
            .platform(request.getPlatform())
            .storeProductId(request.getStoreProductId())
            .storeTransactionId(transactionId)
            .diamondCount(bundle.getDiamondCount())
            .build());

        return diamondService.grantPinkDiamonds(
            userId, bundle.getDiamondCount(), bundle.getId(), bundle.getName());
    }
}
