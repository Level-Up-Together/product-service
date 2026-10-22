package io.pinkspider.leveluptogethermvp.gamificationservice.experience.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.LevelRewardItemGrantedEvent;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.UserItemService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** LUT-593: 레벨 5 도달 시 성장의 새싹 지급 — 경계(4→5, 4→6, 5→6), 보유자 스킵, 설정 없음, 실패 격리 */
@ExtendWith(MockitoExtension.class)
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
                .nameEn("Sprout of Growth")
                .nameAr("برعم النمو")
                .nameJa("成長の芽")
                .build();
    }

    @Nested
    @DisplayName("지급 경계")
    class Boundary {

        @Test
        @DisplayName("4 → 5: 지급하고 ITEM_GRANTED 이벤트를 발행한다")
        void fourToFive_grants() {
            when(userItemService.grantItem(USER_ID, SPROUT_ID)).thenReturn(sprout());

            service.grantOnLevelUp(USER_ID, 4, 5);

            ArgumentCaptor<LevelRewardItemGrantedEvent> captor =
                    ArgumentCaptor.forClass(LevelRewardItemGrantedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            LevelRewardItemGrantedEvent event = captor.getValue();
            assertThat(event.userId()).isEqualTo(USER_ID);
            assertThat(event.shopItemId()).isEqualTo(SPROUT_ID);
            assertThat(event.itemName()).isEqualTo("성장의 새싹");
            assertThat(event.itemNameEn()).isEqualTo("Sprout of Growth");
            assertThat(event.itemNameAr()).isEqualTo("برعم النمو");
            assertThat(event.itemNameJa()).isEqualTo("成長の芽");
            assertThat(event.reachedLevel()).isEqualTo(5);
            assertThat(event.occurredAt()).isNotNull();
        }

        @Test
        @DisplayName("4 → 6 (레벨 건너뜀): 5 이상이 됐으므로 지급한다")
        void fourToSix_grants() {
            when(userItemService.grantItem(USER_ID, SPROUT_ID)).thenReturn(sprout());

            service.grantOnLevelUp(USER_ID, 4, 6);

            verify(userItemService).grantItem(USER_ID, SPROUT_ID);
            verify(eventPublisher).publishEvent(any(LevelRewardItemGrantedEvent.class));
        }

        @Test
        @DisplayName("1 → 5 (여러 레벨 한 번에): 지급한다")
        void oneToFive_grants() {
            when(userItemService.grantItem(USER_ID, SPROUT_ID)).thenReturn(sprout());

            service.grantOnLevelUp(USER_ID, 1, 5);

            verify(userItemService).grantItem(USER_ID, SPROUT_ID);
        }

        @Test
        @DisplayName("5 → 6: 이미 5 이상에서의 레벨업은 대상이 아니다 (회수된 아이템이 되살아나지 않게)")
        void fiveToSix_skips() {
            service.grantOnLevelUp(USER_ID, 5, 6);

            verify(userItemService, never()).grantItem(anyString(), anyLong());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("3 → 4: 아직 5 미만이면 지급하지 않는다")
        void threeToFour_skips() {
            service.grantOnLevelUp(USER_ID, 3, 4);

            verify(userItemService, never()).grantItem(anyString(), anyLong());
        }

        @Test
        @DisplayName("지급 레벨 설정을 바꾸면 그 레벨 기준으로 판정한다")
        void customLevel() {
            properties.setSproutLevel(10);
            when(userItemService.grantItem(USER_ID, SPROUT_ID)).thenReturn(sprout());

            service.grantOnLevelUp(USER_ID, 4, 5);
            verify(userItemService, never()).grantItem(anyString(), anyLong());

            service.grantOnLevelUp(USER_ID, 9, 10);
            verify(userItemService).grantItem(USER_ID, SPROUT_ID);
        }
    }

    @Nested
    @DisplayName("멱등·설정·실패 격리")
    class Guards {

        @Test
        @DisplayName("이미 보유(구매·시즌 보상 포함)하면 지급도 알림도 없다")
        void alreadyOwned_noEvent() {
            when(userItemService.grantItem(USER_ID, SPROUT_ID)).thenReturn(null);

            service.grantOnLevelUp(USER_ID, 4, 5);

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("아이템 ID 설정이 비어 있으면 아무것도 하지 않는다 (환경 미설정 안전장치)")
        void notConfigured_skips() {
            properties.setSproutItemId(null);

            service.grantOnLevelUp(USER_ID, 4, 5);

            verify(userItemService, never()).grantItem(anyString(), anyLong());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("지급이 실패해도 예외를 던지지 않는다 (레벨업은 유지, 알림 없음)")
        void grantFailure_isSwallowed() {
            when(userItemService.grantItem(USER_ID, SPROUT_ID))
                    .thenThrow(new CustomException("120602", "error.useritem.item_not_found"));

            service.grantOnLevelUp(USER_ID, 4, 5);

            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
