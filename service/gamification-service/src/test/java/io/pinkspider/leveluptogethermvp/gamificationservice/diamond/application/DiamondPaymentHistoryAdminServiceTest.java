package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

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
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondPaymentHistoryPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondPaymentHistoryRow;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.enums.DiamondPurchaseStatus;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure.DiamondBundlePurchaseRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiamondPaymentHistoryAdminService 테스트 (LUT-401)")
class DiamondPaymentHistoryAdminServiceTest {

    @Mock
    private DiamondBundlePurchaseRepository purchaseRepository;

    @Mock
    private UserQueryFacade userQueryFacade;

    @InjectMocks
    private DiamondPaymentHistoryAdminService service;

    private DiamondPaymentHistoryRow row(Long id, String userId) {
        return new DiamondPaymentHistoryRow(
            id, userId, 1L, "핑크다이아 100개", "ios", "pink_100", "tx-" + id, 100,
            new BigDecimal("1.99"), "USD", DiamondPurchaseStatus.PAID, null,
            LocalDateTime.of(2026, 8, 7, 10, 30));
    }

    private UserProfileInfo profile(String userId, String nickname) {
        return new UserProfileInfo(userId, nickname, null, 1, null, null, null);
    }

    @Test
    @DisplayName("닉네임 검색어 없으면 필터만으로 조회하고 닉네임을 벌크로 채운다")
    void getPaymentHistory_noNickname() {
        when(purchaseRepository.search(isNull(), isNull(), eq("ios"), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(
                List.of(row(1L, "user-1")), PageRequest.of(0, 20), 1));
        when(userQueryFacade.getUserProfiles(List.of("user-1")))
            .thenReturn(Map.of("user-1", profile("user-1", "백루미")));

        DiamondPaymentHistoryPageResponse result =
            service.getPaymentHistory(null, null, null, "ios", null, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).bundleName()).isEqualTo("핑크다이아 100개");
        assertThat(result.content().get(0).priceAmount()).isEqualByComparingTo(new BigDecimal("1.99"));
        assertThat(result.content().get(0).nickname()).isEqualTo("백루미");
        assertThat(result.page()).isZero();
        verify(userQueryFacade, never()).findUserIdsByNicknameContaining(any());
    }

    @Test
    @DisplayName("닉네임 매칭 유저가 있으면 매칭된 userId로 필터링한다")
    void getPaymentHistory_nicknameMatch() {
        when(userQueryFacade.findUserIdsByNicknameContaining("루미")).thenReturn(List.of("user-1"));
        when(purchaseRepository.searchWithUsers(
                isNull(), isNull(), isNull(), isNull(), isNull(), eq(List.of("user-1")), any(Pageable.class)))
            .thenReturn(new PageImpl<>(
                List.of(row(1L, "user-1")), PageRequest.of(0, 20), 1));
        when(userQueryFacade.getUserProfiles(List.of("user-1")))
            .thenReturn(Map.of("user-1", profile("user-1", "백루미")));

        DiamondPaymentHistoryPageResponse result =
            service.getPaymentHistory(null, null, "루미", null, null, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        verify(purchaseRepository, never()).search(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("닉네임 매칭이 없으면 빈 결과를 즉시 반환한다 (빈 IN 절 방지)")
    void getPaymentHistory_nicknameNoMatch_returnsEmpty() {
        when(userQueryFacade.findUserIdsByNicknameContaining("없는유저")).thenReturn(List.of());

        DiamondPaymentHistoryPageResponse result =
            service.getPaymentHistory(null, null, "없는유저", null, null, null, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        verify(purchaseRepository, never()).search(any(), any(), any(), any(), any(), any());
        verify(purchaseRepository, never()).searchWithUsers(any(), any(), any(), any(), any(), anyList(), any());
    }

    @Test
    @DisplayName("결제 기록이 있어도 유저 프로필이 없으면 닉네임은 null로 노출한다")
    void getPaymentHistory_missingProfile_nicknameNull() {
        when(purchaseRepository.search(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(
                List.of(row(2L, "withdrawn-user")), PageRequest.of(0, 20), 1));
        when(userQueryFacade.getUserProfiles(List.of("withdrawn-user"))).thenReturn(Map.of());

        DiamondPaymentHistoryPageResponse result =
            service.getPaymentHistory(null, null, null, null, null, null, 0, 20);

        assertThat(result.content().get(0).nickname()).isNull();
    }

    @Test
    @DisplayName("결과가 없으면 프로필 벌크 조회를 생략한다")
    void getPaymentHistory_emptyResult_skipsProfileLookup() {
        when(purchaseRepository.search(any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.getPaymentHistory(null, null, null, null, null, null, 0, 20);

        verify(userQueryFacade, never()).getUserProfiles(any());
    }
}
