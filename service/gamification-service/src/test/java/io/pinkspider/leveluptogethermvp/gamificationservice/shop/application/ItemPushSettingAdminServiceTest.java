package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemPushSettingRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemPushSettingResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushSetting;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ItemPushSettingRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ItemPushSettingAdminService 테스트 (LUT-528)")
class ItemPushSettingAdminServiceTest {

    @Mock private ShopItemRepository shopItemRepository;
    @Mock private ItemPushSettingRepository itemPushSettingRepository;

    @InjectMocks private ItemPushSettingAdminService service;

    private static final Long HEAD_ID = 100L;
    private static final Long BASIC_ID = 200L;

    private ShopItem headItem;
    private ShopItem basicItem;

    @BeforeEach
    void setUp() {
        headItem = ShopItem.builder().name("시련의 장미").itemType(ShopItemType.HEAD).price(0).build();
        setId(headItem, HEAD_ID);
        basicItem = ShopItem.builder().name("천사의 날개").itemType(ShopItemType.BASIC).price(0).build();
        setId(basicItem, BASIC_ID);
    }

    private ItemPushSettingRequest request(String sendTime) {
        return ItemPushSettingRequest.builder().sendTime(sendTime).build();
    }

    @Test
    @DisplayName("설정이 없으면 새로 생성한다")
    void upsertCreatesWhenAbsent() {
        when(shopItemRepository.findById(HEAD_ID)).thenReturn(Optional.of(headItem));
        when(itemPushSettingRepository.findById(HEAD_ID)).thenReturn(Optional.empty());
        when(itemPushSettingRepository.save(any(ItemPushSetting.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ItemPushSettingResponse res = service.upsert(HEAD_ID, request("09:00"), 1L);

        assertThat(res.getShopItemId()).isEqualTo(HEAD_ID);
        assertThat(res.getSendTime()).isEqualTo("09:00");
        verify(itemPushSettingRepository).save(any(ItemPushSetting.class));
    }

    @Test
    @DisplayName("설정이 있으면 발송 시각을 수정한다")
    void upsertUpdatesWhenPresent() {
        ItemPushSetting existing = ItemPushSetting.of(HEAD_ID, "10:00", 1L);
        when(shopItemRepository.findById(HEAD_ID)).thenReturn(Optional.of(headItem));
        when(itemPushSettingRepository.findById(HEAD_ID)).thenReturn(Optional.of(existing));
        when(itemPushSettingRepository.save(any(ItemPushSetting.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.upsert(HEAD_ID, request("11:30"), 2L);

        assertThat(existing.getSendTime()).isEqualTo("11:30");
    }

    @Test
    @DisplayName("HEAD 가 아닌 아이템이면 400 (not_head_item)")
    void upsertOnNonHeadFails() {
        when(shopItemRepository.findById(BASIC_ID)).thenReturn(Optional.of(basicItem));

        assertThatThrownBy(() -> service.upsert(BASIC_ID, request("09:00"), 1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.item_push.not_head_item");

        verify(itemPushSettingRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 아이템이면 404 (shop_item.not_found)")
    void upsertOnMissingItemFails() {
        when(shopItemRepository.findById(HEAD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsert(HEAD_ID, request("09:00"), 1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.shop_item.not_found");
    }

    @Test
    @DisplayName("설정이 없으면 조회 결과는 null(미발송)")
    void getReturnsNullWhenAbsent() {
        when(shopItemRepository.findById(HEAD_ID)).thenReturn(Optional.of(headItem));
        when(itemPushSettingRepository.findById(HEAD_ID)).thenReturn(Optional.empty());

        assertThat(service.get(HEAD_ID)).isNull();
    }

    @Test
    @DisplayName("삭제는 설정 행을 제거한다(발송 해제)")
    void deleteRemovesSetting() {
        when(shopItemRepository.findById(HEAD_ID)).thenReturn(Optional.of(headItem));

        service.delete(HEAD_ID);

        verify(itemPushSettingRepository).deleteById(HEAD_ID);
    }
}
