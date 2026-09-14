package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionPaymentHistoryPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.SubscriptionPaymentHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.SubscriptionPaymentHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionPaymentHistoryAdminService 테스트 (LUT-486/488)")
class SubscriptionPaymentHistoryAdminServiceTest {

    @Mock
    private SubscriptionPaymentHistoryRepository repository;

    @Mock
    private UserQueryFacade userQueryFacade;

    @InjectMocks
    private SubscriptionPaymentHistoryAdminService service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0, 0);

    private SubscriptionPaymentHistory history(
            Long id, String userId, SubscriptionPaymentEventType eventType) {
        SubscriptionPaymentHistory row = SubscriptionPaymentHistory.builder()
            .userId(userId)
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

    private UserProfileInfo profile(String userId, String nickname) {
        return new UserProfileInfo(userId, nickname, null, 1, null, null, null);
    }

    @Test
    @DisplayName("userId 지정 시 해당 유저로 필터링하고 닉네임을 채운다 (LUT-486 유저 상세 탭)")
    void getPaymentHistory_byUserId() {
        when(repository.searchWithUsers(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(List.of("user-1")), any(Pageable.class)))
            .thenReturn(new PageImpl<>(
                List.of(history(2L, "user-1", SubscriptionPaymentEventType.RENEWAL),
                    history(1L, "user-1", SubscriptionPaymentEventType.PURCHASE)),
                PageRequest.of(0, 20), 2));
        when(userQueryFacade.getUserProfiles(List.of("user-1")))
            .thenReturn(Map.of("user-1", profile("user-1", "백루미")));

        SubscriptionPaymentHistoryPageResponse result =
            service.getPaymentHistory(null, null, null, "user-1", null, null, null, 0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).id()).isEqualTo(2L);
        assertThat(result.content().get(0).eventType())
            .isEqualTo(SubscriptionPaymentEventType.RENEWAL);
        assertThat(result.content().get(0).nickname()).isEqualTo("백루미");
        assertThat(result.content().get(1).eventType())
            .isEqualTo(SubscriptionPaymentEventType.PURCHASE);
        assertThat(result.totalElements()).isEqualTo(2);
        verify(userQueryFacade, never()).findUserIdsByNicknameContaining(any());
    }

    @Test
    @DisplayName("필터만으로 전체 목록을 조회한다 (LUT-488 결제이력 통합 페이지)")
    void getPaymentHistory_listWithFilters() {
        when(repository.search(
                isNull(), isNull(), eq("ios"), eq(SubscriptionPlan.MONTHLY), isNull(),
                any(Pageable.class)))
            .thenReturn(new PageImpl<>(
                List.of(history(1L, "user-1", SubscriptionPaymentEventType.PURCHASE)),
                PageRequest.of(0, 20), 1));
        when(userQueryFacade.getUserProfiles(List.of("user-1")))
            .thenReturn(Map.of("user-1", profile("user-1", "백루미")));

        SubscriptionPaymentHistoryPageResponse result =
            service.getPaymentHistory(
                null, null, null, null, "ios", SubscriptionPlan.MONTHLY, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).nickname()).isEqualTo("백루미");
        assertThat(result.content().get(0).priceAmount())
            .isEqualByComparingTo(new BigDecimal("4900.00"));
    }

    @Test
    @DisplayName("닉네임 매칭 유저가 있으면 매칭된 userId로 필터링한다")
    void getPaymentHistory_nicknameMatch() {
        when(userQueryFacade.findUserIdsByNicknameContaining("루미")).thenReturn(List.of("user-1"));
        when(repository.searchWithUsers(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(List.of("user-1")), any(Pageable.class)))
            .thenReturn(new PageImpl<>(
                List.of(history(1L, "user-1", SubscriptionPaymentEventType.PURCHASE)),
                PageRequest.of(0, 20), 1));
        when(userQueryFacade.getUserProfiles(List.of("user-1")))
            .thenReturn(Map.of("user-1", profile("user-1", "백루미")));

        SubscriptionPaymentHistoryPageResponse result =
            service.getPaymentHistory(null, null, "루미", null, null, null, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        verify(repository, never()).search(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("닉네임 매칭이 없으면 빈 결과를 즉시 반환한다 (빈 IN 절 방지)")
    void getPaymentHistory_nicknameNoMatch_returnsEmpty() {
        when(userQueryFacade.findUserIdsByNicknameContaining("없는유저")).thenReturn(List.of());

        SubscriptionPaymentHistoryPageResponse result =
            service.getPaymentHistory(null, null, "없는유저", null, null, null, null, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        verify(repository, never()).search(any(), any(), any(), any(), any(), any());
        verify(repository, never())
            .searchWithUsers(any(), any(), any(), any(), any(), anyList(), any());
    }

    @Test
    @DisplayName("프로필이 없는 결제자(탈퇴 등)는 닉네임 null 로 노출한다")
    void getPaymentHistory_missingProfile_nicknameNull() {
        when(repository.search(isNull(), isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class)))
            .thenReturn(new PageImpl<>(
                List.of(history(1L, "withdrawn-user", SubscriptionPaymentEventType.PURCHASE)),
                PageRequest.of(0, 20), 1));
        when(userQueryFacade.getUserProfiles(List.of("withdrawn-user"))).thenReturn(Map.of());

        SubscriptionPaymentHistoryPageResponse result =
            service.getPaymentHistory(null, null, null, null, null, null, null, 0, 20);

        assertThat(result.content().get(0).nickname()).isNull();
    }

    @Test
    @DisplayName("결과가 없으면 프로필 벌크 조회를 생략한다")
    void getPaymentHistory_emptyResult_skipsProfileLookup() {
        when(repository.search(any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.getPaymentHistory(null, null, null, null, null, null, null, 0, 20);

        verify(userQueryFacade, never()).getUserProfiles(any());
    }
}
