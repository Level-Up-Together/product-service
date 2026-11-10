package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserAchievementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * LUT-538: user_achievement 첫 행 생성을 별도 트랜잭션에서 수행하는 전용 빈. LUT-719: ON CONFLICT DO NOTHING 이라 경합에서 예외
 * 대신 false 를 돌려준다.
 */
@ExtendWith(MockitoExtension.class)
class UserAchievementCreatorTest {

    @Mock private UserAchievementRepository userAchievementRepository;

    @InjectMocks private UserAchievementCreator userAchievementCreator;

    private static final String TEST_USER_ID = "test-user-123";

    @Test
    @DisplayName("없으면 진행도 0 인 행을 넣고 true 를 돌려준다")
    void create_insertsNewRow() {
        // given
        when(userAchievementRepository.insertIfAbsent(TEST_USER_ID, 1L)).thenReturn(1);

        // when
        boolean inserted = userAchievementCreator.create(TEST_USER_ID, 1L);

        // then
        assertThat(inserted).isTrue();
        verify(userAchievementRepository).insertIfAbsent(TEST_USER_ID, 1L);
    }

    @Test
    @DisplayName("이미 있으면(다른 요청이 먼저 생성) 예외 없이 false 를 돌려준다 — ERROR 로그가 남지 않는 경로")
    void create_returnsFalseWhenAlreadyExists() {
        // given — ON CONFLICT DO NOTHING 은 0행
        when(userAchievementRepository.insertIfAbsent(TEST_USER_ID, 1L)).thenReturn(0);

        // when
        boolean inserted = userAchievementCreator.create(TEST_USER_ID, 1L);

        // then
        assertThat(inserted).isFalse();
    }
}
