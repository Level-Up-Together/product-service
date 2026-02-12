package io.pinkspider.leveluptogethermvp.userservice.terms.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure.UserTermAgreementsRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserTermAgreement;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTermAgreementsServiceTest {

    @Mock
    private UserTermAgreementsRepository userTermAgreementsRepository;

    @InjectMocks
    private UserTermAgreementsService userTermAgreementsService;

    private static final String TEST_USER_ID = "test-user-123";
    private static final Long TERM_VERSION_ID = 1L;

    @Nested
    @DisplayName("findAllByUserIdAndTermVersionId 테스트")
    class FindAllByUserIdAndTermVersionIdTest {

        @Test
        @DisplayName("사용자와 약관 버전으로 동의 기록을 조회한다")
        void findAllByUserIdAndTermVersionId_found() {
            // given
            UserTermAgreement agreement = UserTermAgreement.builder()
                .isAgreed(true)
                .build();

            when(userTermAgreementsRepository.findAllByUserIdAndTermVersionId(TEST_USER_ID, TERM_VERSION_ID))
                .thenReturn(Optional.of(agreement));

            // when
            UserTermAgreement result = userTermAgreementsService.findAllByUserIdAndTermVersionId(TEST_USER_ID, TERM_VERSION_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getIsAgreed()).isTrue();
        }

        @Test
        @DisplayName("동의 기록이 없으면 null을 반환한다")
        void findAllByUserIdAndTermVersionId_notFound() {
            // given
            when(userTermAgreementsRepository.findAllByUserIdAndTermVersionId(TEST_USER_ID, TERM_VERSION_ID))
                .thenReturn(Optional.empty());

            // when
            UserTermAgreement result = userTermAgreementsService.findAllByUserIdAndTermVersionId(TEST_USER_ID, TERM_VERSION_ID);

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("save 테스트")
    class SaveTest {

        @Test
        @DisplayName("동의 기록을 저장한다")
        void save_success() {
            // given
            UserTermAgreement agreement = UserTermAgreement.builder()
                .isAgreed(true)
                .build();

            // when
            userTermAgreementsService.save(agreement);

            // then
            verify(userTermAgreementsRepository).save(agreement);
        }
    }
}
