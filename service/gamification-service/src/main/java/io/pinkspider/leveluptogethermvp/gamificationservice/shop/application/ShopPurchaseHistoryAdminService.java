package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.DiamondType;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.DiamondHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopPurchaseHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopPurchaseHistoryAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopPurchaseHistoryRow;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-328: 어드민 아이템 구매이력 조회.
 *
 * <p>원장(diamond_history, type=SHOP)과 shop_item 을 조인해 구매 시점·가격을 내려주고,
 * 구매자 닉네임은 UserQueryFacade 벌크 조회로 채운다 (MissionParticipantAdminService 패턴).
 * keyword 는 아이템명(한/영) OR 구매자 닉네임에 매칭된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class ShopPurchaseHistoryAdminService {

    private final DiamondHistoryRepository diamondHistoryRepository;
    private final UserQueryFacade userQueryFacade;

    public ShopPurchaseHistoryAdminPageResponse getPurchaseHistory(
            String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String trimmed = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        Page<ShopPurchaseHistoryRow> rows;
        if (trimmed == null) {
            rows = diamondHistoryRepository.searchShopPurchases(DiamondType.SHOP, null, pageable);
        } else {
            List<String> matchedUserIds =
                userQueryFacade.findUserIdsByNicknameContaining(trimmed);
            // IN 빈 리스트는 JPQL 에서 무효라, 닉네임 매칭이 없으면 아이템명 검색만 수행
            rows = matchedUserIds.isEmpty()
                ? diamondHistoryRepository.searchShopPurchases(
                    DiamondType.SHOP, trimmed, pageable)
                : diamondHistoryRepository.searchShopPurchasesWithUsers(
                    DiamondType.SHOP, trimmed, matchedUserIds, pageable);
        }

        List<String> purchaserIds = rows.getContent().stream()
            .map(ShopPurchaseHistoryRow::userId)
            .distinct()
            .toList();
        Map<String, UserProfileInfo> profiles = purchaserIds.isEmpty()
            ? Map.of()
            : userQueryFacade.getUserProfiles(purchaserIds);

        List<ShopPurchaseHistoryAdminResponse> content = rows.getContent().stream()
            .map(row -> {
                UserProfileInfo profile = profiles.get(row.userId());
                return ShopPurchaseHistoryAdminResponse.from(
                    row, profile != null ? profile.nickname() : null);
            })
            .toList();

        return ShopPurchaseHistoryAdminPageResponse.from(rows, content);
    }
}
