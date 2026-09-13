package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionPaymentHistoryPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionPaymentHistoryResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.SubscriptionPaymentHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.SubscriptionPaymentHistoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** LUT-486: 어드민 유저 상세 '결제 이력' 탭 — 구독 결제 이력 조회 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class SubscriptionPaymentHistoryAdminService {

    private final SubscriptionPaymentHistoryRepository repository;

    public SubscriptionPaymentHistoryPageResponse getPaymentHistory(
            String userId, int page, int size) {
        Page<SubscriptionPaymentHistory> rows =
                repository.findByUserIdOrderByIdDesc(userId, PageRequest.of(page, size));
        List<SubscriptionPaymentHistoryResponse> content =
                rows.getContent().stream().map(SubscriptionPaymentHistoryResponse::from).toList();
        return SubscriptionPaymentHistoryPageResponse.from(rows, content);
    }
}
