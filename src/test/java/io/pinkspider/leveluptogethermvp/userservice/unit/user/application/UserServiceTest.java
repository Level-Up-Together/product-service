package io.pinkspider.leveluptogethermvp.userservice.unit.user.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.util.CryptoUtils;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private static final String TEST_USER_ID = "test-user-123";

    private Users createTestUser(String userId, String nickname, String email) {
        Users user = Users.builder()
            .nickname(nickname)
            .email(email)
            .build();
        setId(user, userId);
        return user;
    }

    @Nested
    @DisplayName("findByUserId 테스트")
    class FindByUserIdTest {

        @Test
        @DisplayName("사용자를 ID로 조회한다")
        void findByUserId_success() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스트유저", "test@test.com");

            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

            // when
            Users result = userService.findByUserId(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(TEST_USER_ID);
            assertThat(result.getNickname()).isEqualTo("테스트유저");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 조회 시 예외가 발생한다")
        void findByUserId_notFound_throwsException() {
            // given
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.findByUserId(TEST_USER_ID))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("findByEmailAndProvider 테스트")
    class FindByEmailAndProviderTest {

        @Test
        @DisplayName("이메일과 provider로 사용자를 조회한다")
        void findByEmailAndProvider_success() {
            // given
            Users user = createTestUser(TEST_USER_ID, "테스트유저", "test@test.com");
            String email = "test@test.com";
            String provider = "GOOGLE";
            String encryptedEmail = "encrypted_email";

            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                mockedCrypto.when(() -> CryptoUtils.encryptAes(email)).thenReturn(encryptedEmail);
                when(userRepository.findByEncryptedEmailAndProvider(encryptedEmail, provider))
                    .thenReturn(Optional.of(user));

                // when
                Users result = userService.findByEmailAndProvider(email, provider);

                // then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(TEST_USER_ID);
                assertThat(result.getNickname()).isEqualTo("테스트유저");
            }
        }

        @Test
        @DisplayName("이메일과 provider로 사용자를 찾을 수 없을 때 예외가 발생한다")
        void findByEmailAndProvider_notFound_throwsException() {
            // given
            String email = "notfound@test.com";
            String provider = "GOOGLE";
            String encryptedEmail = "encrypted_notfound";

            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                mockedCrypto.when(() -> CryptoUtils.encryptAes(email)).thenReturn(encryptedEmail);
                when(userRepository.findByEncryptedEmailAndProvider(encryptedEmail, provider))
                    .thenReturn(Optional.empty());

                // when & then
                assertThatThrownBy(() -> userService.findByEmailAndProvider(email, provider))
                    .isInstanceOf(CustomException.class);
            }
        }
    }
}
