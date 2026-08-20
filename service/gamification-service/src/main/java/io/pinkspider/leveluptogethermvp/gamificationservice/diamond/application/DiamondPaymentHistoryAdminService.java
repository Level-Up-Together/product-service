package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondPaymentHistoryPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondPaymentHistoryResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondPaymentHistoryRow;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.enums.DiamondPurchaseStatus;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure.DiamondBundlePurchaseRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-401: 어드민 다이아 결제이력 조회.
 *
 * <p>diamond_bundle_purchase 를 diamond_bundle 과 조인해 상품명을 포함한 결제 기록을 내려주고,
 * 구매자 닉네임은 {@link UserQueryFacade} 벌크 조회로 채운다 (ShopPurchaseHistoryAdminService
 * 패턴과 동일). 닉네임 검색어는 매칭된 userId 목록으로 변환해 필터링한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class DiamondPaymentHistoryAdminService {

    private final DiamondBundlePurchaseRepository purchaseRepository;
    private final UserQueryFacade userQueryFacade;

    public DiamondPaymentHistoryPageResponse getPaymentHistory(
            LocalDateTime startAt, LocalDateTime endAt, String nickname,
            String platform, Long bundleId, DiamondPurchaseStatus status,
            int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String trimmedNickname = (nickname == null || nickname.isBlank()) ? null : nickname.trim();

        Page<DiamondPaymentHistoryRow> rows;
        if (trimmedNickname == null) {
            rows = purchaseRepository.search(startAt, endAt, platform, bundleId, status, pageable);
        } else {
            List<String> matchedUserIds = userQueryFacade.findUserIdsByNicknameContaining(trimmedNickname);
            // IN 빈 리스트는 JPQL 에서 무효라, 닉네임 매칭이 없으면 결과 없음으로 즉시 반환
            if (matchedUserIds.isEmpty()) {
                return DiamondPaymentHistoryPageResponse.from(
                    Page.empty(pageable), List.of());
            }
            rows = purchaseRepository.searchWithUsers(
                startAt, endAt, platform, bundleId, status, matchedUserIds, pageable);
        }

        List<String> purchaserIds = rows.getContent().stream()
            .map(DiamondPaymentHistoryRow::userId)
            .distinct()
            .toList();
        Map<String, UserProfileInfo> profiles = purchaserIds.isEmpty()
            ? Map.of()
            : userQueryFacade.getUserProfiles(purchaserIds);

        List<DiamondPaymentHistoryResponse> content = rows.getContent().stream()
            .map(row -> {
                UserProfileInfo profile = profiles.get(row.userId());
                return DiamondPaymentHistoryResponse.from(row, profile != null ? profile.nickname() : null);
            })
            .toList();

        return DiamondPaymentHistoryPageResponse.from(rows, content);
    }
}
