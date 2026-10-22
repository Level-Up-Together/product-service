package io.pinkspider.leveluptogethermvp.gamificationservice.experience.application;

import io.pinkspider.global.event.LevelRewardItemGrantedEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.UserItemService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * LUT-593: 레벨 5 도달 시 성장의 새싹 지급.
 *
 * <p>목적은 날개 외에 기타 아이템이 있음을 알리고, 장착하면 아이템 푸시(LUT-516)를 받는 경험을 초반에 겪게 하는 것 — 그래서 **미착용 상태**로 인벤토리에만
 * 넣는다.
 *
 * <ul>
 *   <li>트리거: {@code 이전 레벨 < 지급 레벨 <= 새 레벨} (4→6 처럼 건너뛰어도 지급). 이미 5 이상에서의 레벨업(5→6)은 대상이 아니다 — 관리자가
 *       회수한 아이템이 다음 레벨업에 되살아나지 않게 한다. 미지급 유저 보정은 백필 SQL(V007) 몫.
 *   <li>멱등: {@link UserItemService#grantItem} 이 보유 시 null 을 돌려주므로 구매·시즌 보상으로 이미 가진 유저에겐 지급·알림 모두
 *       없다.
 *   <li>지급 실패는 레벨업을 되돌리지 않는다 — 보상은 best-effort 이고, 행 생성은 REQUIRES_NEW({@code UserItemCreator})라 예외를
 *       잡아도 호출자 트랜잭션이 오염되지 않는다.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LevelRewardItemService {

    private final LevelRewardProperties properties;
    private final UserItemService userItemService;
    private final ApplicationEventPublisher eventPublisher;

    /** 레벨업 트랜잭션 안에서 호출한다. 조건이 맞지 않거나 설정이 비어 있으면 아무것도 하지 않는다. */
    public void grantOnLevelUp(String userId, int levelBefore, int levelAfter) {
        Long itemId = properties.getSproutItemId();
        int targetLevel = properties.getSproutLevel();
        if (itemId == null || !(levelBefore < targetLevel && levelAfter >= targetLevel)) {
            return;
        }
        try {
            ShopItem granted = userItemService.grantItem(userId, itemId);
            if (granted == null) {
                log.info("레벨 보상 아이템 스킵(이미 보유): userId={}, shopItemId={}", userId, itemId);
                return;
            }
            log.info(
                    "레벨 보상 아이템 지급: userId={}, shopItemId={}, {} -> {}",
                    userId,
                    itemId,
                    levelBefore,
                    levelAfter);
            eventPublisher.publishEvent(
                    new LevelRewardItemGrantedEvent(
                            userId,
                            granted.getId(),
                            granted.getName(),
                            granted.getNameEn(),
                            granted.getNameAr(),
                            granted.getNameJa(),
                            levelAfter));
        } catch (RuntimeException e) {
            // 설정된 아이템이 없는 등 지급 실패 — 레벨업은 유지하고 로그만 남긴다 (백필 SQL 로 보정)
            log.error(
                    "레벨 보상 아이템 지급 실패(레벨업 유지): userId={}, shopItemId={}, {} -> {}",
                    userId,
                    itemId,
                    levelBefore,
                    levelAfter,
                    e);
        }
    }
}
