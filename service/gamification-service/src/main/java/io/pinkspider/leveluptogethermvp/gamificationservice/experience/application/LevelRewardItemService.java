package io.pinkspider.leveluptogethermvp.gamificationservice.experience.application;

import io.pinkspider.global.event.LevelRewardItemGrantedEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.UserItemService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * LUT-607: 회원가입 완료 직후 성장의 새싹 지급 (LUT-593 의 "레벨 5 도달 시" 를 대체).
 *
 * <p>목적은 날개 외에 기타 아이템이 있음을 알리고, 장착하면 아이템 푸시(LUT-516)를 받는 경험을 초반에 겪게 하는 것 — 그래서 **미착용 상태**로 인벤토리에만
 * 넣는다. 신규 가입 환영 모달의 확인 버튼이 인벤토리로 이동하므로 가입 직후 인벤토리에 보여야 한다.
 *
 * <ul>
 *   <li>트리거: {@code UserSignedUpEventListener}(가입 완료 트랜잭션 커밋 후, 비동기) — 기본 칭호·기본 아이템과 같은 지점. 지급 실패는
 *       가입을 막지 않는다. 레벨업 트리거는 LUT-607 에서 제거됐다.
 *   <li>멱등: {@link UserItemService#grantItem} 이 보유 시 null 을 돌려주므로 이미 가진 유저에겐 지급·알림 모두 없다.
 *   <li>아이템 id 는 환경별 설정({@code app.level-reward.sprout-item-id}) — 클래스·설정·이벤트 이름의 "LevelReward" 는
 *       LUT-593 때 이름이다 (플랫폼 이벤트 호환을 위해 유지).
 *   <li>기존 미보유 유저 보정은 백필 SQL(1.1.4 V002, 레벨 무관·탈퇴 제외·알림 없음) 몫.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LevelRewardItemService {

    /** 이벤트의 reachedLevel — 가입 직후라 1 (알림 문구는 쓰지 않는다) */
    static final int SIGNUP_LEVEL = 1;

    private final LevelRewardProperties properties;
    private final UserItemService userItemService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 가입 완료 뒤 호출한다(커밋 후 리스너). 설정이 비어 있으면 아무것도 하지 않는다.
     *
     * @return 새로 지급했으면 true, 보유 중·설정 없음·실패면 false
     */
    public boolean grantOnSignup(String userId) {
        Long itemId = properties.getSproutItemId();
        if (itemId == null) {
            return false;
        }
        try {
            ShopItem granted = userItemService.grantItem(userId, itemId);
            if (granted == null) {
                log.info("가입 보상 아이템 스킵(이미 보유): userId={}, shopItemId={}", userId, itemId);
                return false;
            }
            log.info("가입 보상 아이템 지급: userId={}, shopItemId={}", userId, itemId);
            eventPublisher.publishEvent(
                    new LevelRewardItemGrantedEvent(
                            userId,
                            granted.getId(),
                            granted.getName(),
                            granted.getNameEn(),
                            granted.getNameAr(),
                            granted.getNameJa(),
                            SIGNUP_LEVEL));
            return true;
        } catch (RuntimeException e) {
            // 설정된 아이템이 없는 등 지급 실패 — 가입은 이미 끝났고 로그만 남긴다 (백필 SQL 로 보정)
            log.error("가입 보상 아이템 지급 실패: userId={}, shopItemId={}", userId, itemId, e);
            return false;
        }
    }
}
