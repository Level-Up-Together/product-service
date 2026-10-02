package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserAchievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserAchievementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** LUT-538: user_achievement 첫 행 생성을 별도 트랜잭션에서 수행하는 전용 빈. */
@ExtendWith(MockitoExtension.class)
class UserAchievementCreatorTest {

    @Mock private UserAchievementRepository userAchievementRepository;

    @Mock private AchievementRepository achievementRepository;

    @InjectMocks private UserAchievementCreator userAchievementCreator;

    private static final String TEST_USER_ID = "test-user-123";

    @Test
    @DisplayName("achievementId 로 참조를 얻어 진행도 0 인 행을 생성한다")
    void create_savesNewRowWithZeroProgress() {
        // given — 호출자가 가진 Achievement 인스턴스는 캐시에서 온 detached 일 수 있어 id 로만 받는다
        Achievement reference = Achievement.builder().name("MISSION_COMPLETE_10").build();
        when(achievementRepository.getReferenceById(1L)).thenReturn(reference);
        when(userAchievementRepository.saveAndFlush(any(UserAchievement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        userAchievementCreator.create(TEST_USER_ID, 1L);

        // then
        ArgumentCaptor<UserAchievement> captor = ArgumentCaptor.forClass(UserAchievement.class);
        verify(userAchievementRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(captor.getValue().getCurrentCount()).isZero();
        assertThat(captor.getValue().getAchievement()).isSameAs(reference);
    }

    @Test
    @DisplayName("중복 키 예외는 삼키지 않고 호출자에게 전파한다 (호출자가 재조회로 복구)")
    void create_propagatesDuplicateKey() {
        // given
        when(achievementRepository.getReferenceById(1L))
                .thenReturn(Achievement.builder().name("MISSION_COMPLETE_10").build());
        when(userAchievementRepository.saveAndFlush(any(UserAchievement.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        // when & then
        assertThatThrownBy(() -> userAchievementCreator.create(TEST_USER_ID, 1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
