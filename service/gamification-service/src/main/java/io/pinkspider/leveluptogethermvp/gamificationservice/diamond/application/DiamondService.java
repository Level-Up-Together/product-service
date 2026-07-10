package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

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
            int balanceAfter = diamond.apply(1);
            diamondHistoryRepository.save(DiamondHistory.builder()
                .userId(userId)
                .type(DiamondType.LEVEL_UP)
                .sourceId((long) level)
                .amount(1)
                .balanceAfter(balanceAfter)
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
        int balanceAfter = diamond.apply(amount);
        String description = fromLevel == toLevel
            ? "Lv." + toLevel + " 레벨업 보상"
            : "Lv." + fromLevel + "~Lv." + toLevel + " 레벨업 보상";

        diamondHistoryRepository.save(DiamondHistory.builder()
            .userId(userId)
            .type(DiamondType.LEVEL_UP)
            .sourceId((long) toLevel)
            .amount(amount)
            .balanceAfter(balanceAfter)
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
        int balanceAfter = diamond.apply(1);
        diamondHistoryRepository.save(DiamondHistory.builder()
            .userId(userId)
            .type(DiamondType.MISSION_BOOK)
            .sourceId(templateId)
            .amount(1)
            .balanceAfter(balanceAfter)
            .description(missionTitle + " 목표달성")
            .build());

        log.info("미션북 다이아 지급: userId={}, templateId={}, balance={}",
            userId, templateId, diamond.getBalance());
        return true;
    }

    /**
     * 다이아 차감 (상점 구매용 — QA-220 시점에는 상점 미구현, API 만 준비).
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public int spendDiamonds(String userId, int amount, Long itemId, String itemName) {
        if (amount <= 0) {
            throw new IllegalArgumentException("차감량은 1 이상이어야 합니다: " + amount);
        }
        UserDiamond diamond = getOrCreate(userId);
        int balanceAfter = diamond.apply(-amount);
        diamondHistoryRepository.save(DiamondHistory.builder()
            .userId(userId)
            .type(DiamondType.SHOP)
            .sourceId(itemId)
            .amount(-amount)
            .balanceAfter(balanceAfter)
            .description(itemName + " 구매")
            .build());

        log.info("다이아 차감: userId={}, amount={}, balance={}", userId, amount, balanceAfter);
        return balanceAfter;
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
