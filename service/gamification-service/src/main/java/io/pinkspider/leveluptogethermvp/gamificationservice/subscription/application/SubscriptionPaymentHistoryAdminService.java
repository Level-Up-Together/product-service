package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionPaymentHistoryPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionPaymentHistoryResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.SubscriptionPaymentHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.SubscriptionPaymentHistoryRepository;
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
 * LUT-486/488: 어드민 구독 결제 이력 조회 — 유저 상세 탭(userId 지정)과 결제이력 통합
 * 페이지(필터 목록)를 한 API 로 겸한다. 닉네임 검색·벌크 채움은
 * {@code DiamondPaymentHistoryAdminService}(LUT-401) 패턴과 동일.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class SubscriptionPaymentHistoryAdminService {

    private final SubscriptionPaymentHistoryRepository repository;
    private final UserQueryFacade userQueryFacade;

    public SubscriptionPaymentHistoryPageResponse getPaymentHistory(
            LocalDateTime startAt, LocalDateTime endAt, String nickname, String userId,
            String platform, SubscriptionPlan plan, SubscriptionPaymentEventType eventType,
            int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String trimmedNickname = (nickname == null || nickname.isBlank()) ? null : nickname.trim();

        Page<SubscriptionPaymentHistory> rows;
        if (userId != null && !userId.isBlank()) {
            // 유저 상세 '결제 이력' 탭 (LUT-486) — userId 지정 시 닉네임 검색보다 우선
            rows = repository.searchWithUsers(
                startAt, endAt, platform, plan, eventType, List.of(userId.trim()), pageable);
        } else if (trimmedNickname == null) {
            rows = repository.search(startAt, endAt, platform, plan, eventType, pageable);
        } else {
            List<String> matchedUserIds =
                userQueryFacade.findUserIdsByNicknameContaining(trimmedNickname);
            // IN 빈 리스트는 JPQL 에서 무효라, 닉네임 매칭이 없으면 결과 없음으로 즉시 반환
            if (matchedUserIds.isEmpty()) {
                return SubscriptionPaymentHistoryPageResponse.from(
                    Page.empty(pageable), List.of());
            }
            rows = repository.searchWithUsers(
                startAt, endAt, platform, plan, eventType, matchedUserIds, pageable);
        }

        List<String> payerIds = rows.getContent().stream()
            .map(SubscriptionPaymentHistory::getUserId)
            .distinct()
            .toList();
        Map<String, UserProfileInfo> profiles = payerIds.isEmpty()
            ? Map.of()
            : userQueryFacade.getUserProfiles(payerIds);

        List<SubscriptionPaymentHistoryResponse> content = rows.getContent().stream()
            .map(row -> {
                UserProfileInfo profile = profiles.get(row.getUserId());
                return SubscriptionPaymentHistoryResponse.from(
                    row, profile != null ? profile.nickname() : null);
            })
            .toList();

        return SubscriptionPaymentHistoryPageResponse.from(rows, content);
    }
}
