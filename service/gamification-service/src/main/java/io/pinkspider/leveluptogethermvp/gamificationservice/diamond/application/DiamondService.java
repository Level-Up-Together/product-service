package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.UserDiamondBalanceResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.UserDiamondHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.UserDiamondHistoryAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.DiamondHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserDiamond;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.DiamondType;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.DiamondHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserDiamondRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * QA-220: 다이아 (샵 재화) 지급/차감/조회.
 *
 * 획득처:
 * 1. 레벨업 1회당 1개 (Lv.1000까지, 총 999개 상한)
 * 2. 미션북 템플릿 최초 목표달성 시 1개 (템플릿당 1회)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class DiamondService {

    private final UserDiamondRepository userDiamondRepository;
    private final DiamondHistoryRepository diamondHistoryRepository;

    /**
     * 레벨업 다이아 지급 — 달성 레벨당 1개.
     *
     * lastRewardedLevel 초과분만 지급하므로 경험치 환수로 레벨이 내려갔다 다시 올라도 중복 지급되지 않는다.
     * (레벨다운 시 환수하지 않는 정책과 짝을 이룬다.)
     *
     * @return 지급된 다이아 수
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public int awardLevelUpDiamonds(String userId, int levelAfter) {
        UserDiamond diamond = getOrCreate(userId);

        int fromLevel = diamond.getLastRewardedLevel() + 1;
        int toLevel = Math.min(levelAfter, UserDiamond.MAX_REWARDED_LEVEL);
        if (fromLevel > toLevel) {
            return 0;
        }

        for (int level = fromLevel; level <= toLevel; level++) {
            diamond.apply(1);
            diamondHistoryRepository.save(DiamondHistory.builder()
                .userId(userId)
                .type(DiamondType.LEVEL_UP)
                .sourceId((long) level)
                .amount(1)
                .balanceAfter(diamond.getTotalBalance())
                .description("Lv." + level + " 레벨업 보상")
                .build());
        }
        diamond.setLastRewardedLevel(toLevel);

        int granted = toLevel - fromLevel + 1;
        log.info("레벨업 다이아 지급: userId={}, Lv.{}~Lv.{}, granted={}, balance={}",
            userId, fromLevel, toLevel, granted, diamond.getBalance());
        return granted;
    }

    /**
     * 레벨업 다이아 일괄 지급 (마이그레이션용) — 여러 레벨 몫을 한 행으로 기록한다.
     *
     * @return 지급된 다이아 수
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public int awardLevelUpDiamondsAggregated(String userId, int levelAfter) {
        UserDiamond diamond = getOrCreate(userId);

        int fromLevel = diamond.getLastRewardedLevel() + 1;
        int toLevel = Math.min(levelAfter, UserDiamond.MAX_REWARDED_LEVEL);
        if (fromLevel > toLevel) {
            return 0;
        }

        int amount = toLevel - fromLevel + 1;
        diamond.apply(amount);
        String description = fromLevel == toLevel
            ? "Lv." + toLevel + " 레벨업 보상"
            : "Lv." + fromLevel + "~Lv." + toLevel + " 레벨업 보상";

        diamondHistoryRepository.save(DiamondHistory.builder()
            .userId(userId)
            .type(DiamondType.LEVEL_UP)
            .sourceId((long) toLevel)
            .amount(amount)
            .balanceAfter(diamond.getTotalBalance())
            .description(description)
            .build());
        diamond.setLastRewardedLevel(toLevel);

        log.info("레벨업 다이아 일괄 지급: userId={}, Lv.{}~Lv.{}, granted={}, balance={}",
            userId, fromLevel, toLevel, amount, diamond.getBalance());
        return amount;
    }

    /**
     * 미션북 최초 목표달성 다이아 지급 — 같은 템플릿에는 1회만 지급.
     *
     * @return 실제 지급 여부 (이미 지급된 템플릿이면 false)
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public boolean awardMissionBookDiamond(String userId, Long templateId, String missionTitle) {
        if (templateId == null) {
            return false;
        }
        if (diamondHistoryRepository.existsByUserIdAndTypeAndSourceId(
            userId, DiamondType.MISSION_BOOK, templateId)) {
            return false;
        }

        UserDiamond diamond = getOrCreate(userId);
        diamond.apply(1);
        diamondHistoryRepository.save(DiamondHistory.builder()
            .userId(userId)
            .type(DiamondType.MISSION_BOOK)
            .sourceId(templateId)
            .amount(1)
            .balanceAfter(diamond.getTotalBalance())
            .description(missionTitle + " 목표달성")
            .build());

        log.info("미션북 다이아 지급: userId={}, templateId={}, balance={}",
            userId, templateId, diamond.getBalance());
        return true;
    }

    /**
     * 다이아 차감 (상점 구매용 — LUT-327 상점에서 사용).
     *
     * <p>LUT-328: 가격 0원 구매도 어드민 구매이력에 남도록 amount 0 을 허용한다
     * (잔액 변동 없이 SHOP 이력만 기록).
     *
     * <p>LUT-354: 블루+핑크 합산에서 차감 — 블루(무상) 우선 소진, 부족분만 핑크(유상).
     * 원장에 핑크 소진량(pink_amount)을 구분 기록한다.
     *
     * @return 차감 후 총잔액 (블루+핑크)
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public int spendDiamonds(String userId, int amount, Long itemId, String itemName) {
        if (amount < 0) {
            throw new IllegalArgumentException("차감량은 0 이상이어야 합니다: " + amount);
        }
        UserDiamond diamond = getOrCreate(userId);
        int pinkSpent = diamond.spendCombined(amount);
        int balanceAfter = diamond.getTotalBalance();
        diamondHistoryRepository.save(DiamondHistory.builder()
            .userId(userId)
            .type(DiamondType.SHOP)
            .sourceId(itemId)
            .amount(-amount)
            .pinkAmount(-pinkSpent)
            .balanceAfter(balanceAfter)
            .description(itemName + " 구매")
            .build());

        log.info("다이아 차감: userId={}, amount={}, pinkSpent={}, balance={}",
            userId, amount, pinkSpent, balanceAfter);
        return balanceAfter;
    }

    /**
     * LUT-354: 핑크다이아 지급 (IAP 묶음상품 구매 검증 완료 후 호출).
     *
     * @return 지급 후 총잔액 (블루+핑크)
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public int grantPinkDiamonds(String userId, int amount, Long bundleId, String bundleName) {
        UserDiamond diamond = getOrCreate(userId);
        int balanceAfter = diamond.addPink(amount);
        diamondHistoryRepository.save(DiamondHistory.builder()
            .userId(userId)
            .type(DiamondType.PINK_PURCHASE)
            .sourceId(bundleId)
            .amount(amount)
            .pinkAmount(amount)
            .balanceAfter(balanceAfter)
            .description(bundleName + " 구매")
            .build());

        log.info("핑크다이아 지급: userId={}, amount={}, bundleId={}, balance={}",
            userId, amount, bundleId, balanceAfter);
        return balanceAfter;
    }

    /**
     * LUT-453: 구독 일일 스티펜드 지급 — 블루(무상) 다이아. 이월 허용·소멸 없음.
     *
     * <p>원장에 type=SUBSCRIPTION + sourceId=구독 ID 로 남긴다 — 구독분 발행/소진 집계와 추후 별도
     * 재화 승격의 근거. 일자 멱등은 호출부(subscription_stipend 유니크)가 보장한다.
     *
     * @return 지급 후 총잔액 (블루+핑크)
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public int awardSubscriptionStipend(String userId, Long subscriptionId, int amount) {
        UserDiamond diamond = getOrCreate(userId);
        diamond.apply(amount);
        int balanceAfter = diamond.getTotalBalance();
        diamondHistoryRepository.save(DiamondHistory.builder()
            .userId(userId)
            .type(DiamondType.SUBSCRIPTION)
            .sourceId(subscriptionId)
            .amount(amount)
            .balanceAfter(balanceAfter)
            .description("구독 일일 스티펜드")
            .build());

        log.info("구독 스티펜드 지급: userId={}, subscriptionId={}, amount={}, balance={}",
            userId, subscriptionId, amount, balanceAfter);
        return balanceAfter;
    }

    /** 현재 보유 다이아 잔액 조회. 지급 이력이 없으면 0. (LUT-248: 마이페이지 표기용) */
    public int getBalance(String userId) {
        return userDiamondRepository.findByUserId(userId).map(UserDiamond::getBalance).orElse(0);
    }

    /** LUT-356: 블루/핑크 분리 잔액 조회. 응답의 balance는 합계(하위호환). */
    public UserDiamondBalanceResponse getBalances(String userId) {
        return userDiamondRepository.findByUserId(userId)
            .map(d -> UserDiamondBalanceResponse.of(d.getBalance(), d.getPinkBalance()))
            .orElseGet(() -> UserDiamondBalanceResponse.of(0, 0));
    }

    /** 어드민 다이아 탭용 이력 조회 (현재 잔액 포함) */
    public UserDiamondHistoryAdminPageResponse getUserDiamondHistory(String userId, Pageable pageable) {
        Page<DiamondHistory> page = diamondHistoryRepository.findByUserIdOrderByIdDesc(userId, pageable);
        List<UserDiamondHistoryAdminResponse> content = page.getContent().stream()
            .map(UserDiamondHistoryAdminResponse::from)
            .toList();
        int balance = userDiamondRepository.findByUserId(userId)
            .map(UserDiamond::getBalance)
            .orElse(0);
        return UserDiamondHistoryAdminPageResponse.from(page, content, balance);
    }

    private UserDiamond getOrCreate(String userId) {
        return userDiamondRepository.findByUserId(userId)
            .orElseGet(() -> userDiamondRepository.save(UserDiamond.create(userId)));
    }
}
