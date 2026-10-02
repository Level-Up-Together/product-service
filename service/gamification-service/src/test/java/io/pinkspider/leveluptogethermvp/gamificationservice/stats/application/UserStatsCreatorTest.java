package io.pinkspider.leveluptogethermvp.gamificationservice.stats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserStatsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** LUT-538: user_stats 첫 행 생성을 별도 트랜잭션에서 수행하는 전용 빈. */
@ExtendWith(MockitoExtension.class)
class UserStatsCreatorTest {

    @Mock private UserStatsRepository userStatsRepository;

    @InjectMocks private UserStatsCreator userStatsCreator;

    private static final String TEST_USER_ID = "test-user-123";

    @Test
    @DisplayName("userId 로 새 user_stats 행을 즉시 flush 하며 생성한다")
    void create_savesAndFlushesNewRow() {
        // given
        when(userStatsRepository.saveAndFlush(any(UserStats.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        userStatsCreator.create(TEST_USER_ID);

        // then — saveAndFlush 여야 제약 위반이 이 내부 트랜잭션 안에서 즉시 드러난다
        ArgumentCaptor<UserStats> captor = ArgumentCaptor.forClass(UserStats.class);
        org.mockito.Mockito.verify(userStatsRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(TEST_USER_ID);
    }

    @Test
    @DisplayName("중복 키 예외는 삼키지 않고 호출자에게 전파한다 (호출자가 재조회로 복구)")
    void create_propagatesDuplicateKey() {
        // given — 내부에서 catch 하면 rollback-only 트랜잭션이 커밋되며 UnexpectedRollbackException 이 난다
        when(userStatsRepository.saveAndFlush(any(UserStats.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        // when & then
        assertThatThrownBy(() -> userStatsCreator.create(TEST_USER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
