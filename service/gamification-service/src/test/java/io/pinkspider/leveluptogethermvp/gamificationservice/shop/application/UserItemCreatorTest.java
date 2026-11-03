package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ShopItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.UserItem;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.ShopItemRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure.UserItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** LUT-539: user_item 지급 행 생성을 별도 트랜잭션(REQUIRES_NEW)에서 수행하는 전용 빈. */
@ExtendWith(MockitoExtension.class)
class UserItemCreatorTest {

    @Mock private UserItemRepository userItemRepository;
    @Mock private ShopItemRepository shopItemRepository;

    @InjectMocks private UserItemCreator creator;

    @Test
    @DisplayName("ShopItem 은 id 참조로만 받아 보유 행을 즉시 flush 하며 생성한다")
    void create_usesReferenceAndFlushes() {
        ShopItem ref = ShopItem.builder().build();
        when(shopItemRepository.getReferenceById(3L)).thenReturn(ref);
        when(userItemRepository.saveAndFlush(any(UserItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        creator.create("user-1", 3L);

        ArgumentCaptor<UserItem> captor = ArgumentCaptor.forClass(UserItem.class);
        verify(userItemRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
        assertThat(captor.getValue().getShopItem()).isSameAs(ref);
        assertThat(captor.getValue().getAcquiredAt()).isNotNull();
        assertThat(captor.getValue().getIsEquipped()).isFalse();
    }

    @Test
    @DisplayName("중복 키(uk_user_item) 예외는 삼키지 않고 호출자에게 전파한다")
    void create_propagatesDuplicateKey() {
        when(shopItemRepository.getReferenceById(3L)).thenReturn(ShopItem.builder().build());
        when(userItemRepository.saveAndFlush(any(UserItem.class)))
                .thenThrow(new DataIntegrityViolationException("uk_user_item"));

        assertThatThrownBy(() -> creator.create("user-1", 3L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
