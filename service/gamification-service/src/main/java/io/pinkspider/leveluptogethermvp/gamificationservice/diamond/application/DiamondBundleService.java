package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundleResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure.DiamondBundleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** LUT-356: 상점 노출용 핑크다이아 묶음상품 조회 서비스 */
@Service
@RequiredArgsConstructor
public class DiamondBundleService {

    private final DiamondBundleRepository diamondBundleRepository;

    /** 판매중(활성) 묶음상품 목록 — 다이아 개수 오름차순 */
    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<DiamondBundleResponse> getActiveBundles() {
        return diamondBundleRepository.findByIsActiveTrueOrderByDiamondCountAscIdAsc().stream()
            .map(DiamondBundleResponse::from)
            .toList();
    }
}
