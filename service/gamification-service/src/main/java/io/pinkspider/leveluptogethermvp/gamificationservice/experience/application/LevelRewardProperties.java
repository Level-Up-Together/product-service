package io.pinkspider.leveluptogethermvp.gamificationservice.experience.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LUT-593/607: 가입 보상 아이템(성장의 새싹) 설정. 지급 레벨 설정은 LUT-607(가입 즉시 지급)에서 제거됐다.
 *
 * <p>성장의 새싹 {@code shop_item_id} 는 환경별로 다르다(dev 6 / prod 15) — 코드 상수가 아니라 config-repository 의
 * {@code app.level-reward.sprout-item-id} 로 받는다. 값이 비어 있으면 지급하지 않는다.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.level-reward")
public class LevelRewardProperties {

    /** 성장의 새싹 shop_item_id (환경별). null 이면 미지급 */
    private Long sproutItemId;
}
