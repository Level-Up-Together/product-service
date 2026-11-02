package io.pinkspider.leveluptogethermvp.gamificationservice.experience.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.LevelRewardItemGrantedEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.UserItemService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("LevelRewardItemService 테스트 — 가입 즉시 성장의 새싹 지급 (LUT-607)")
class LevelRewardItemServiceTest {

    private static final String USER_ID = "user-1";
    private static final long SPROUT_ID = 6L;

    @Mock private UserItemService userItemService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private LevelRewardProperties properties;
    private LevelRewardItemService service;

    @BeforeEach
    void setUp() {
        properties = new LevelRewardProperties();
        properties.setSproutItemId(SPROUT_ID);
        service = new LevelRewardItemService(properties, userItemService, eventPublisher);
    }

    private ShopItem sprout() {
        return ShopItem.builder()
                .id(SPROUT_ID)
                .name("성장의 새싹")
                .nameEn("Sprout")
                .nameAr("برعم")
                .nameJa("成長の芽")
                .build();
    }

    @Test
    @DisplayName("가입 완료 유저에게 지급하고 ITEM_GRANTED 이벤트를 발행한다 (미착용은 grantItem 의 몫)")
    void grantsOnSignupAndPublishes() {
        when(userItemService.grantItem(USER_ID, SPROUT_ID)).thenReturn(sprout());

        assertThat(service.grantOnSignup(USER_ID)).isTrue();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        LevelRewardItemGrantedEvent event = (LevelRewardItemGrantedEvent) captor.getValue();
        assertThat(event.userId()).isEqualTo(USER_ID);
        assertThat(event.shopItemId()).isEqualTo(SPROUT_ID);
        assertThat(event.itemNameEn()).isEqualTo("Sprout");
        assertThat(event.reachedLevel()).isEqualTo(LevelRewardItemService.SIGNUP_LEVEL);
    }

    @Test
    @DisplayName("이미 보유한 유저는 지급·알림 모두 없다 (멱등)")
    void skipsWhenAlreadyOwned() {
        when(userItemService.grantItem(USER_ID, SPROUT_ID)).thenReturn(null);

        assertThat(service.grantOnSignup(USER_ID)).isFalse();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("아이템 설정이 비어 있으면 아무것도 하지 않는다")
    void noopWithoutConfiguredItem() {
        properties.setSproutItemId(null);

        assertThat(service.grantOnSignup(USER_ID)).isFalse();
        verify(userItemService, never()).grantItem(anyString(), anyLong());
    }

    @Test
    @DisplayName("지급 실패는 삼키고 false — 가입 응답·다른 가입 보상을 막지 않는다")
    void swallowsGrantFailure() {
        when(userItemService.grantItem(USER_ID, SPROUT_ID))
                .thenThrow(new IllegalStateException("shop_item 없음"));

        assertThat(service.grantOnSignup(USER_ID)).isFalse();
        verify(eventPublisher, never()).publishEvent(any());
    }
}
