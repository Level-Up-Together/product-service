package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.DiamondService;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.SubscriptionStipend;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.SubscriptionStipendRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionStipendTxService 테스트 (LUT-453)")
class SubscriptionStipendTxServiceTest {

    @Mock
    private SubscriptionStipendRepository stipendRepository;

    @Mock
    private DiamondService diamondService;

    @InjectMocks
    private SubscriptionStipendTxService stipendTxService;

    private static final LocalDate STIPEND_DATE = LocalDate.of(2026, 9, 4);

    private UserSubscription subscription() {
        UserSubscription subscription = UserSubscription.builder()
            .userId("user-1")
            .platform("ios")
            .productId("membership_1m")
            .plan(SubscriptionPlan.MONTHLY)
            .startedAt(LocalDateTime.now().minusMonths(1))
            .expiresAt(LocalDateTime.now().plusDays(10))
            .autoRenew(true)
            .trialUsed(false)
            .build();
        TestReflectionUtils.setField(subscription, "id", 77L);
        return subscription;
    }

    @Test
    @DisplayName("지급 기록 insert 후 블루 다이아를 지급한다 — 원장 source=SUBSCRIPTION")
    void grantsStipendWithLedger() {
        when(stipendRepository.saveAndFlush(any(SubscriptionStipend.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        boolean granted = stipendTxService.grantForSubscription(subscription(), STIPEND_DATE, 1);

        assertThat(granted).isTrue();
        ArgumentCaptor<SubscriptionStipend> captor = ArgumentCaptor.forClass(SubscriptionStipend.class);
        verify(stipendRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSubscriptionId()).isEqualTo(77L);
        assertThat(captor.getValue().getStipendDate()).isEqualTo(STIPEND_DATE);
        assertThat(captor.getValue().getAmount()).isEqualTo(1);
        verify(diamondService).awardSubscriptionStipend("user-1", 77L, 1);
    }

    @Test
    @DisplayName("멱등 — (구독 ID, 지급일) 기지급이면 다이아를 지급하지 않는다")
    void duplicateDateSkipsGrant() {
        when(stipendRepository.saveAndFlush(any(SubscriptionStipend.class)))
            .thenThrow(new DataIntegrityViolationException("uk_subscription_stipend_daily"));

        boolean granted = stipendTxService.grantForSubscription(subscription(), STIPEND_DATE, 1);

        assertThat(granted).isFalse();
        verify(diamondService, never()).awardSubscriptionStipend(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
