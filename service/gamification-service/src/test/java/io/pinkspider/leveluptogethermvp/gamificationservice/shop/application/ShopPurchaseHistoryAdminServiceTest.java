package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.DiamondType;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.DiamondHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopPurchaseHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopPurchaseHistoryRow;
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
class ShopPurchaseHistoryAdminServiceTest {

    @Mock
    private DiamondHistoryRepository diamondHistoryRepository;

    @Mock
    private UserQueryFacade userQueryFacade;

    @InjectMocks
    private ShopPurchaseHistoryAdminService shopPurchaseHistoryAdminService;

    private ShopPurchaseHistoryRow row(Long historyId, String userId, int amount, Long itemId,
            String itemName, TitleRarity rarity) {
        return new ShopPurchaseHistoryRow(historyId, userId, amount,
            LocalDateTime.of(2026, 8, 7, 10, 30), itemId, itemName, rarity);
    }

    private UserProfileInfo profile(String userId, String nickname) {
        return new UserProfileInfo(userId, nickname, null, 1, null, null, null);
    }

    @Test
    @DisplayName("검색어 없으면 전체 구매이력을 최신순으로 조회하고 닉네임을 벌크로 채운다")
    void getPurchaseHistory_noKeyword() {
        when(diamondHistoryRepository.searchShopPurchases(
            eq(DiamondType.SHOP), eq(null), any(Pageable.class)))
            .thenReturn(new PageImpl<>(
                List.of(row(10L, "user-1", -300, 3L, "메딕의 날개", TitleRarity.RARE)),
                PageRequest.of(0, 20), 1));
        when(userQueryFacade.getUserProfiles(List.of("user-1")))
            .thenReturn(Map.of("user-1", profile("user-1", "백루미")));

        ShopPurchaseHistoryAdminPageResponse result =
            shopPurchaseHistoryAdminService.getPurchaseHistory(null, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).itemName()).isEqualTo("메딕의 날개");
        assertThat(result.content().get(0).price()).isEqualTo(300);
        assertThat(result.content().get(0).nickname()).isEqualTo("백루미");
        assertThat(result.content().get(0).rarityName()).isEqualTo("희귀");
        verify(userQueryFacade, never()).findUserIdsByNicknameContaining(anyString());
    }

    @Test
    @DisplayName("닉네임 매칭 유저가 있으면 아이템명 OR 구매자 검색 쿼리를 사용한다")
    void getPurchaseHistory_keywordWithNicknameMatch() {
        when(userQueryFacade.findUserIdsByNicknameContaining("루미"))
            .thenReturn(List.of("user-1"));
        when(diamondHistoryRepository.searchShopPurchasesWithUsers(
            eq(DiamondType.SHOP), eq("루미"), eq(List.of("user-1")), any(Pageable.class)))
            .thenReturn(new PageImpl<>(
                List.of(row(10L, "user-1", -300, 3L, "메딕의 날개", TitleRarity.RARE)),
                PageRequest.of(0, 20), 1));
        when(userQueryFacade.getUserProfiles(List.of("user-1")))
            .thenReturn(Map.of("user-1", profile("user-1", "백루미")));

        ShopPurchaseHistoryAdminPageResponse result =
            shopPurchaseHistoryAdminService.getPurchaseHistory("루미", 0, 20);

        assertThat(result.content()).hasSize(1);
        verify(diamondHistoryRepository, never()).searchShopPurchases(
            any(), anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("닉네임 매칭이 없으면 아이템명 검색만 수행한다 (빈 IN 절 방지)")
    void getPurchaseHistory_keywordWithoutNicknameMatch() {
        when(userQueryFacade.findUserIdsByNicknameContaining("날개")).thenReturn(List.of());
        when(diamondHistoryRepository.searchShopPurchases(
            eq(DiamondType.SHOP), eq("날개"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(
                List.of(row(11L, "user-2", 0, 2L, "무료 날개", TitleRarity.COMMON)),
                PageRequest.of(0, 20), 1));
        when(userQueryFacade.getUserProfiles(List.of("user-2")))
            .thenReturn(Map.of());

        ShopPurchaseHistoryAdminPageResponse result =
            shopPurchaseHistoryAdminService.getPurchaseHistory("날개", 0, 20);

        // 0원 구매(LUT-328)도 price 0 으로 노출, 탈퇴 등 프로필 부재 시 닉네임 null
        assertThat(result.content().get(0).price()).isZero();
        assertThat(result.content().get(0).nickname()).isNull();
        verify(diamondHistoryRepository, never()).searchShopPurchasesWithUsers(
            any(), anyString(), anyList(), any(Pageable.class));
    }
}
