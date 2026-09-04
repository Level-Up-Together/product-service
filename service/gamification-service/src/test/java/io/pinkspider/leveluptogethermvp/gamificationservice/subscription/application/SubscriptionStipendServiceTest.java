package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.UserSubscriptionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionStipendService 테스트 (LUT-453)")
class SubscriptionStipendServiceTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private SubscriptionStipendTxService stipendTxService;

    @InjectMocks
    private SubscriptionStipendService stipendService;

    private UserSubscription subscription(long id, String userId) {
        UserSubscription subscription = UserSubscription.builder()
            .userId(userId)
            .platform("ios")
            .productId("membership_1m")
            .plan(SubscriptionPlan.MONTHLY)
            .startedAt(LocalDateTime.now().minusMonths(1))
            .expiresAt(LocalDateTime.now().plusDays(10))
            .autoRenew(true)
            .trialUsed(false)
            .build();
        TestReflectionUtils.setField(subscription, "id", id);
        return subscription;
    }

    @Test
    @DisplayName("권한 보유 구독 전체에 UTC 오늘 날짜로 1개씩 지급한다")
    void grantsToAllEntitled() {
        when(userSubscriptionRepository.findAllEntitled(any()))
            .thenReturn(List.of(subscription(1L, "user-1"), subscription(2L, "user-2")));
        when(stipendTxService.grantForSubscription(any(), any(), anyInt())).thenReturn(true);

        int granted = stipendService.grantDailyStipends();

        assertThat(granted).isEqualTo(2);
        verify(stipendTxService, times(2))
            .grantForSubscription(any(), eq(LocalDate.now(java.time.ZoneOffset.UTC)), eq(1));
    }

    @Test
    @DisplayName("기지급(멱등 스킵)은 지급 건수에 세지 않는다")
    void alreadyGrantedNotCounted() {
        when(userSubscriptionRepository.findAllEntitled(any()))
            .thenReturn(List.of(subscription(1L, "user-1"), subscription(2L, "user-2")));
        when(stipendTxService.grantForSubscription(any(), any(), anyInt()))
            .thenReturn(true)
            .thenReturn(false);

        assertThat(stipendService.grantDailyStipends()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 구독의 지급 실패가 나머지 지급을 멈추지 않는다")
    void oneFailureDoesNotStopBatch() {
        when(userSubscriptionRepository.findAllEntitled(any()))
            .thenReturn(List.of(subscription(1L, "user-1"), subscription(2L, "user-2")));
        when(stipendTxService.grantForSubscription(any(), any(), anyInt()))
            .thenThrow(new RuntimeException("db down"))
            .thenReturn(true);

        int granted = stipendService.grantDailyStipends();

        assertThat(granted).isEqualTo(1);
        verify(stipendTxService, times(2)).grantForSubscription(any(), any(), anyInt());
    }

    @Test
    @DisplayName("지급 대상이 없으면 아무것도 하지 않는다")
    void noEntitledNoop() {
        when(userSubscriptionRepository.findAllEntitled(any())).thenReturn(List.of());

        assertThat(stipendService.grantDailyStipends()).isZero();
        verify(stipendTxService, never()).grantForSubscription(any(), any(), anyInt());
    }
}
