package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionPaymentHistoryPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.SubscriptionPaymentHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.SubscriptionPaymentHistoryRepository;
import io.pinkspider.global.test.TestReflectionUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionPaymentHistoryAdminService 테스트 (LUT-486)")
class SubscriptionPaymentHistoryAdminServiceTest {

    @Mock
    private SubscriptionPaymentHistoryRepository repository;

    @InjectMocks
    private SubscriptionPaymentHistoryAdminService service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0, 0);

    private SubscriptionPaymentHistory history(Long id, SubscriptionPaymentEventType eventType) {
        SubscriptionPaymentHistory row = SubscriptionPaymentHistory.builder()
            .userId("user-1")
            .platform("ios")
            .productId("membership_1m")
            .plan(SubscriptionPlan.MONTHLY)
            .eventType(eventType)
            .trial(false)
            .priceAmount(new BigDecimal("4900.00"))
            .priceCurrency("KRW")
            .transactionId("tx-" + id)
            .expiresAt(NOW.plusMonths(1))
            .occurredAt(NOW)
            .build();
        TestReflectionUtils.setField(row, "id", id);
        return row;
    }

    @Test
    @DisplayName("유저별 구독 결제 이력을 페이지로 반환한다")
    void getPaymentHistory() {
        when(repository.findByUserIdOrderByIdDesc(eq("user-1"), eq(PageRequest.of(0, 20))))
            .thenReturn(new PageImpl<>(
                List.of(history(2L, SubscriptionPaymentEventType.RENEWAL),
                    history(1L, SubscriptionPaymentEventType.PURCHASE)),
                PageRequest.of(0, 20), 2));

        SubscriptionPaymentHistoryPageResponse result =
            service.getPaymentHistory("user-1", 0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).id()).isEqualTo(2L);
        assertThat(result.content().get(0).eventType())
            .isEqualTo(SubscriptionPaymentEventType.RENEWAL);
        assertThat(result.content().get(0).priceAmount())
            .isEqualByComparingTo(new BigDecimal("4900.00"));
        assertThat(result.content().get(1).eventType())
            .isEqualTo(SubscriptionPaymentEventType.PURCHASE);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.page()).isZero();
    }

    @Test
    @DisplayName("이력이 없으면 빈 페이지를 반환한다")
    void getPaymentHistory_empty() {
        when(repository.findByUserIdOrderByIdDesc(eq("user-2"), eq(PageRequest.of(0, 20))))
            .thenReturn(Page.empty(PageRequest.of(0, 20)));

        SubscriptionPaymentHistoryPageResponse result =
            service.getPaymentHistory("user-2", 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }
}
