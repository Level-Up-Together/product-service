package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.SignupSessionData;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignupTokenService 단위 테스트")
class SignupTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SignupTokenService signupTokenService;

    private static final String TEST_PROVIDER = "google";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_NICKNAME = "TestUser";
    private static final String TEST_LOCALE = "ko";
    private static final String TEST_TIMEZONE = "Asia/Seoul";

    private SignupSessionData buildData(String token) {
        return new SignupSessionData(
            token, TEST_PROVIDER, TEST_EMAIL, TEST_NICKNAME, TEST_LOCALE, TEST_TIMEZONE
        );
    }

    @BeforeEach
    void setUp() {
        // 일부 테스트(hashEmail static, isValid blank input 등)는 opsForValue를 호출하지 않으므로 lenient 사용
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("createOrRefresh 테스트")
    class CreateOrRefreshTest {

        @Test
        @DisplayName("기존 세션이 없으면 새 토큰을 발급하고 양방향 인덱스를 저장한다")
        void createOrRefresh_newSession_savesNewToken() throws JsonProcessingException {
            // given
            SignupSessionData input = buildData(null);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(objectMapper.writeValueAsString(any(SignupSessionData.class))).thenReturn("{\"json\":true}");

            // when
            String token = signupTokenService.createOrRefresh(input);

            // then
            assertThat(token).isNotBlank();
            // 세션 키 + 토큰 인덱스 키 두 번 저장
            verify(valueOperations, times(2)).set(anyString(), anyString(), eq(Duration.ofMinutes(30)));
            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("기존 세션이 있으면 이전 토큰 인덱스를 무효화하고 새 토큰을 발급한다")
        void createOrRefresh_existingSession_invalidatesOldToken() throws JsonProcessingException {
            // given
            SignupSessionData input = buildData(null);
            String oldToken = "old-token";
            SignupSessionData existing = buildData(oldToken);
            String existingJson = "{\"signupToken\":\"old-token\"}";

            String emailHash = SignupTokenService.hashEmail(TEST_PROVIDER, TEST_EMAIL);
            String sessionKey = "signup:" + TEST_PROVIDER + ":" + emailHash;

            when(valueOperations.get(sessionKey)).thenReturn(existingJson);
            when(objectMapper.readValue(existingJson, SignupSessionData.class)).thenReturn(existing);
            when(objectMapper.writeValueAsString(any(SignupSessionData.class))).thenReturn("{\"json\":true}");

            // when
            String newToken = signupTokenService.createOrRefresh(input);

            // then
            assertThat(newToken).isNotBlank().isNotEqualTo(oldToken);
            verify(redisTemplate).delete("signup-token:" + oldToken);
            verify(valueOperations, times(2)).set(anyString(), anyString(), eq(Duration.ofMinutes(30)));
        }

        @Test
        @DisplayName("기존 세션 JSON 파싱 실패 시에도 새 토큰을 발급한다 (이전 토큰 무효화는 스킵)")
        void createOrRefresh_corruptedExistingSession_continuesWithNewToken() throws JsonProcessingException {
            // given
            SignupSessionData input = buildData(null);
            String emailHash = SignupTokenService.hashEmail(TEST_PROVIDER, TEST_EMAIL);
            String sessionKey = "signup:" + TEST_PROVIDER + ":" + emailHash;

            when(valueOperations.get(sessionKey)).thenReturn("invalid-json");
            when(objectMapper.readValue(eq("invalid-json"), eq(SignupSessionData.class)))
                .thenThrow(new JsonProcessingException("parse error") {});
            when(objectMapper.writeValueAsString(any(SignupSessionData.class))).thenReturn("{\"json\":true}");

            // when
            String token = signupTokenService.createOrRefresh(input);

            // then
            assertThat(token).isNotBlank();
            verify(valueOperations, times(2)).set(anyString(), anyString(), eq(Duration.ofMinutes(30)));
        }

        @Test
        @DisplayName("직렬화 실패 시 CustomException을 던진다")
        void createOrRefresh_serializationFails_throwsException() throws JsonProcessingException {
            // given
            SignupSessionData input = buildData(null);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(objectMapper.writeValueAsString(any(SignupSessionData.class)))
                .thenThrow(new JsonProcessingException("serialize error") {});

            // when & then
            assertThatThrownBy(() -> signupTokenService.createOrRefresh(input))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("signup session 직렬화 실패");
        }
    }

    @Nested
    @DisplayName("findByToken 테스트")
    class FindByTokenTest {

        @Test
        @DisplayName("유효한 토큰이면 세션 데이터를 반환한다")
        void findByToken_validToken_returnsSession() throws JsonProcessingException {
            // given
            String token = "valid-token";
            String emailHash = SignupTokenService.hashEmail(TEST_PROVIDER, TEST_EMAIL);
            String sessionKeyValue = TEST_PROVIDER + ":" + emailHash;
            String sessionJson = "{\"json\":true}";
            SignupSessionData expected = buildData(token);

            when(valueOperations.get("signup-token:" + token)).thenReturn(sessionKeyValue);
            when(valueOperations.get("signup:" + sessionKeyValue)).thenReturn(sessionJson);
            when(objectMapper.readValue(sessionJson, SignupSessionData.class)).thenReturn(expected);

            // when
            SignupSessionData result = signupTokenService.findByToken(token);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("토큰이 null/빈 문자열이면 INVALID_ACCESS 예외를 던진다")
        void findByToken_blankToken_throwsException() {
            assertThatThrownBy(() -> signupTokenService.findByToken(null))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.signup.token_invalid");

            assertThatThrownBy(() -> signupTokenService.findByToken("   "))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.signup.token_invalid");
        }

        @Test
        @DisplayName("토큰 인덱스가 만료된 경우 expired 예외를 던진다")
        void findByToken_expiredIndex_throwsException() {
            // given
            String token = "expired-token";
            when(valueOperations.get("signup-token:" + token)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> signupTokenService.findByToken(token))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.signup.token_expired");
        }

        @Test
        @DisplayName("토큰 인덱스는 살아있지만 세션 데이터가 사라진 경우 expired 예외를 던진다")
        void findByToken_orphanedIndex_throwsException() {
            // given
            String token = "orphan-token";
            String sessionKeyValue = TEST_PROVIDER + ":hash";

            when(valueOperations.get("signup-token:" + token)).thenReturn(sessionKeyValue);
            when(valueOperations.get("signup:" + sessionKeyValue)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> signupTokenService.findByToken(token))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.signup.token_expired");
        }

        @Test
        @DisplayName("세션 JSON 파싱 실패 시 SYSTEM_ERROR 예외를 던진다")
        void findByToken_parseError_throwsException() throws JsonProcessingException {
            // given
            String token = "broken-token";
            String sessionKeyValue = TEST_PROVIDER + ":hash";
            String sessionJson = "broken-json";

            when(valueOperations.get("signup-token:" + token)).thenReturn(sessionKeyValue);
            when(valueOperations.get("signup:" + sessionKeyValue)).thenReturn(sessionJson);
            when(objectMapper.readValue(sessionJson, SignupSessionData.class))
                .thenThrow(new JsonProcessingException("parse fail") {});

            // when & then
            assertThatThrownBy(() -> signupTokenService.findByToken(token))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("signup session 파싱 실패");
        }
    }

    @Nested
    @DisplayName("isValid 테스트")
    class IsValidTest {

        @Test
        @DisplayName("토큰 인덱스가 존재하면 true를 반환한다")
        void isValid_existingToken_returnsTrue() {
            // given
            String token = "valid";
            when(redisTemplate.hasKey("signup-token:" + token)).thenReturn(true);

            // when & then
            assertThat(signupTokenService.isValid(token)).isTrue();
        }

        @Test
        @DisplayName("토큰 인덱스가 없으면 false를 반환한다")
        void isValid_missingToken_returnsFalse() {
            // given
            String token = "missing";
            when(redisTemplate.hasKey("signup-token:" + token)).thenReturn(false);

            // when & then
            assertThat(signupTokenService.isValid(token)).isFalse();
        }

        @Test
        @DisplayName("토큰이 null/빈 문자열이면 false를 반환한다 (Redis 호출 없음)")
        void isValid_blankToken_returnsFalse() {
            assertThat(signupTokenService.isValid(null)).isFalse();
            assertThat(signupTokenService.isValid("")).isFalse();
            assertThat(signupTokenService.isValid("   ")).isFalse();
            verify(redisTemplate, never()).hasKey(anyString());
        }
    }

    @Nested
    @DisplayName("delete 테스트")
    class DeleteTest {

        @Test
        @DisplayName("세션 키와 토큰 인덱스 키를 모두 삭제한다")
        void delete_removesBothKeys() {
            // given
            String token = "to-delete";
            SignupSessionData data = buildData(token);
            String emailHash = SignupTokenService.hashEmail(TEST_PROVIDER, TEST_EMAIL);

            // when
            signupTokenService.delete(data);

            // then
            verify(redisTemplate).delete("signup:" + TEST_PROVIDER + ":" + emailHash);
            verify(redisTemplate).delete("signup-token:" + token);
        }
    }

    @Nested
    @DisplayName("hashEmail 테스트")
    class HashEmailTest {

        @Test
        @DisplayName("같은 (provider, email)은 같은 해시를 생성한다")
        void hashEmail_deterministic() {
            String hash1 = SignupTokenService.hashEmail("google", "user@example.com");
            String hash2 = SignupTokenService.hashEmail("google", "user@example.com");

            assertThat(hash1).isEqualTo(hash2).hasSize(64); // SHA-256 hex
        }

        @Test
        @DisplayName("provider가 다르면 해시가 다르다")
        void hashEmail_differentProviderProducesDifferentHash() {
            String googleHash = SignupTokenService.hashEmail("google", "user@example.com");
            String kakaoHash = SignupTokenService.hashEmail("kakao", "user@example.com");

            assertThat(googleHash).isNotEqualTo(kakaoHash);
        }

        @Test
        @DisplayName("email이 다르면 해시가 다르다")
        void hashEmail_differentEmailProducesDifferentHash() {
            String hash1 = SignupTokenService.hashEmail("google", "a@example.com");
            String hash2 = SignupTokenService.hashEmail("google", "b@example.com");

            assertThat(hash1).isNotEqualTo(hash2);
        }
    }
}
