package io.pinkspider.leveluptogethermvp.gamificationservice.event.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.pinkspider.global.event.UserSignedUpEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.application.LevelRewardItemService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.UserItemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserSignedUpEventListener 테스트 (QA-89 · LUT-296 · LUT-607)")
class UserSignedUpEventListenerTest {

    @Mock private TitleService titleService;
    @Mock private UserItemService userItemService;
    @Mock private LevelRewardItemService levelRewardItemService;

    @InjectMocks private UserSignedUpEventListener listener;

    @Test
    @DisplayName("가입 완료 시 기본 칭호·기본 아이템·성장의 새싹을 모두 지급한다")
    void grantsTitlesDefaultItemsAndSprout() {
        listener.handleUserSignedUp(new UserSignedUpEvent("user-1"));

        verify(titleService).grantAndEquipDefaultTitles("user-1");
        verify(userItemService).grantDefaultItems("user-1");
        verify(levelRewardItemService).grantOnSignup("user-1");
    }

    @Test
    @DisplayName("앞 단계가 실패해도 성장의 새싹 지급은 수행되고, 어떤 실패도 전파되지 않는다")
    void eachStepIsIndependent() {
        doThrow(new RuntimeException("title"))
                .when(titleService)
                .grantAndEquipDefaultTitles("user-1");
        doThrow(new RuntimeException("item")).when(userItemService).grantDefaultItems("user-1");
        doThrow(new RuntimeException("sprout"))
                .when(levelRewardItemService)
                .grantOnSignup("user-1");

        assertThatCode(() -> listener.handleUserSignedUp(new UserSignedUpEvent("user-1")))
                .doesNotThrowAnyException();
        verify(levelRewardItemService).grantOnSignup("user-1");
    }
}
