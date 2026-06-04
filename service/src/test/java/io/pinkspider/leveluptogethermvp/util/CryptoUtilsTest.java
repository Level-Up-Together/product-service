package io.pinkspider.leveluptogethermvp.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pinkspider.global.component.metaredis.CryptoMetaDataLoader;
import io.pinkspider.global.domain.redis.CryptoMetaData;
import io.pinkspider.global.exception.CryptoException;
import io.pinkspider.global.util.CryptoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CryptoUtils 테스트")
class CryptoUtilsTest {

    // 테스트 전용 키/IV (운영 환경과 무관)
    private static final String TEST_SECRET_KEY = "km2c/ZNA4pyuLXQYVeiq7wsOE6+PPrpPzIx9EUM7uEc=";
    private static final String TEST_IV = "K4Dw+xcX91fMfi3SNU0gQg==";
    private static final String TEST_CIPHER = "AES/CBC/PKCS5Padding";

    private CryptoMetaData testCryptoMetaData;

    @BeforeEach
    void setUp() {
        testCryptoMetaData = CryptoMetaData.builder()
                .secretKey(TEST_SECRET_KEY)
                .iv(TEST_IV)
                .cipher(TEST_CIPHER)
                .build();
    }

    @Nested
    @DisplayName("encryptAes 메서드")
    class EncryptAesTest {

        @Test
        @DisplayName("문자열을 AES로 암호화한다")
        void encryptString() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                String plainText = "test@example.com";
                String encrypted = CryptoUtils.encryptAes(plainText);

                assertThat(encrypted).isNotNull();
                assertThat(encrypted).isNotEmpty();
                assertThat(encrypted).isNotEqualTo(plainText);
                // Base64 인코딩된 결과 확인
                assertThat(encrypted).matches("^[A-Za-z0-9+/=]+$");
            }
        }

        @Test
        @DisplayName("동일한 문자열은 동일한 암호문을 생성한다")
        void encryptSameStringProducesSameResult() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                String plainText = "consistent@test.com";
                String encrypted1 = CryptoUtils.encryptAes(plainText);
                String encrypted2 = CryptoUtils.encryptAes(plainText);

                assertThat(encrypted1).isEqualTo(encrypted2);
            }
        }

        @Test
        @DisplayName("한글 문자열을 암호화한다")
        void encryptKoreanString() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                String plainText = "안녕하세요 테스트입니다";
                String encrypted = CryptoUtils.encryptAes(plainText);

                assertThat(encrypted).isNotNull();
                assertThat(encrypted).isNotEmpty();
                assertThat(encrypted).isNotEqualTo(plainText);
            }
        }

        @Test
        @DisplayName("빈 문자열을 암호화한다")
        void encryptEmptyString() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                String plainText = "";
                String encrypted = CryptoUtils.encryptAes(plainText);

                assertThat(encrypted).isNotNull();
                assertThat(encrypted).isNotEmpty();
            }
        }

        @Test
        @DisplayName("특수문자가 포함된 문자열을 암호화한다")
        void encryptStringWithSpecialCharacters() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                String plainText = "test!@#$%^&*()_+-=[]{}|;':\",./<>?";
                String encrypted = CryptoUtils.encryptAes(plainText);

                assertThat(encrypted).isNotNull();
                assertThat(encrypted).isNotEmpty();
            }
        }

        @Test
        @DisplayName("잘못된 키로 암호화 시 CryptoException 발생")
        void encryptWithInvalidKeyThrowsException() {
            CryptoMetaData invalidMetaData = CryptoMetaData.builder()
                    .secretKey("invalidKey")
                    .iv(TEST_IV)
                    .cipher(TEST_CIPHER)
                    .build();

            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(invalidMetaData);

                assertThatThrownBy(() -> CryptoUtils.encryptAes("test")).isInstanceOf(CryptoException.class);
            }
        }
    }

    @Nested
    @DisplayName("decryptAes 메서드")
    class DecryptAesTest {

        @Test
        @DisplayName("암호화된 문자열을 복호화한다")
        void decryptEncryptedString() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                String plainText = "test@example.com";
                String encrypted = CryptoUtils.encryptAes(plainText);
                String decrypted = CryptoUtils.decryptAes(encrypted);

                assertThat(decrypted).isEqualTo(plainText);
            }
        }

        @Test
        @DisplayName("한글 문자열을 암호화 후 복호화한다")
        void decryptKoreanString() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                String plainText = "안녕하세요 테스트입니다 123";
                String encrypted = CryptoUtils.encryptAes(plainText);
                String decrypted = CryptoUtils.decryptAes(encrypted);

                assertThat(decrypted).isEqualTo(plainText);
            }
        }

        @Test
        @DisplayName("빈 문자열을 암호화 후 복호화한다")
        void decryptEmptyString() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                String plainText = "";
                String encrypted = CryptoUtils.encryptAes(plainText);
                String decrypted = CryptoUtils.decryptAes(encrypted);

                assertThat(decrypted).isEqualTo(plainText);
            }
        }

        @Test
        @DisplayName("특수문자가 포함된 문자열을 암호화 후 복호화한다")
        void decryptStringWithSpecialCharacters() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                String plainText = "!@#$%^&*()_+-=[]{}|;':\",./<>?";
                String encrypted = CryptoUtils.encryptAes(plainText);
                String decrypted = CryptoUtils.decryptAes(encrypted);

                assertThat(decrypted).isEqualTo(plainText);
            }
        }

        @Test
        @DisplayName("긴 문자열을 암호화 후 복호화한다")
        void decryptLongString() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                String plainText = "a".repeat(1000);
                String encrypted = CryptoUtils.encryptAes(plainText);
                String decrypted = CryptoUtils.decryptAes(encrypted);

                assertThat(decrypted).isEqualTo(plainText);
            }
        }

        @Test
        @DisplayName("잘못된 암호문으로 복호화 시 CryptoException 발생")
        void decryptInvalidCipherTextThrowsException() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                assertThatThrownBy(() -> CryptoUtils.decryptAes("invalidCipherText"))
                        .isInstanceOf(CryptoException.class);
            }
        }

        @Test
        @DisplayName("다른 키로 암호화된 데이터 복호화 시 CryptoException 발생")
        void decryptWithDifferentKeyThrowsException() {
            String encrypted;
            // 첫 번째 키로 암호화
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);
                encrypted = CryptoUtils.encryptAes("test@example.com");
            }

            // 다른 키로 복호화 시도 (테스트 전용)
            CryptoMetaData differentKeyMetaData = CryptoMetaData.builder()
                    .secretKey("cFSXGDjBl5gNFLOHP+5WVdktX5d7xt9seNAwrUPVwns=") // 다른 테스트 키
                    .iv(TEST_IV)
                    .cipher(TEST_CIPHER)
                    .build();

            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(differentKeyMetaData);

                String finalEncrypted = encrypted;
                assertThatThrownBy(() -> CryptoUtils.decryptAes(finalEncrypted)).isInstanceOf(CryptoException.class);
            }
        }
    }

    @Nested
    @DisplayName("encryptSha256 메서드")
    class EncryptSha256Test {

        @Test
        @DisplayName("문자열을 SHA-256으로 해시한다")
        void hashString() {
            String plainText = "password123";
            String hashed = CryptoUtils.encryptSha256(plainText);

            assertThat(hashed).isNotNull();
            assertThat(hashed).hasSize(64); // SHA-256은 64자의 hex 문자열
            assertThat(hashed).matches("^[a-f0-9]+$"); // hex 문자열
        }

        @Test
        @DisplayName("동일한 문자열은 동일한 해시값을 생성한다")
        void sameStringProducesSameHash() {
            String plainText = "consistentPassword";
            String hash1 = CryptoUtils.encryptSha256(plainText);
            String hash2 = CryptoUtils.encryptSha256(plainText);

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("다른 문자열은 다른 해시값을 생성한다")
        void differentStringsProduceDifferentHashes() {
            String hash1 = CryptoUtils.encryptSha256("password1");
            String hash2 = CryptoUtils.encryptSha256("password2");

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("빈 문자열도 해시한다")
        void hashEmptyString() {
            String hashed = CryptoUtils.encryptSha256("");

            assertThat(hashed).isNotNull();
            assertThat(hashed).hasSize(64);
            // 빈 문자열의 SHA-256 해시값
            assertThat(hashed).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        }

        @Test
        @DisplayName("한글 문자열을 해시한다")
        void hashKoreanString() {
            String hashed = CryptoUtils.encryptSha256("안녕하세요");

            assertThat(hashed).isNotNull();
            assertThat(hashed).hasSize(64);
        }

        @Test
        @DisplayName("특수문자가 포함된 문자열을 해시한다")
        void hashStringWithSpecialCharacters() {
            String hashed = CryptoUtils.encryptSha256("!@#$%^&*()");

            assertThat(hashed).isNotNull();
            assertThat(hashed).hasSize(64);
        }

        @Test
        @DisplayName("긴 문자열을 해시한다")
        void hashLongString() {
            String longText = "a".repeat(10000);
            String hashed = CryptoUtils.encryptSha256(longText);

            assertThat(hashed).isNotNull();
            assertThat(hashed).hasSize(64);
        }

        @Test
        @DisplayName("알려진 SHA-256 해시값과 일치하는지 확인")
        void verifyKnownHashValue() {
            // "hello"의 SHA-256 해시값
            String hashed = CryptoUtils.encryptSha256("hello");

            assertThat(hashed).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        }
    }

    @Nested
    @DisplayName("암호화/복호화 라운드트립 테스트")
    class RoundTripTest {

        @Test
        @DisplayName("이메일 형식 문자열의 암호화/복호화 라운드트립")
        void emailRoundTrip() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                String[] emails = {"test@example.com", "user.name@domain.co.kr", "user+tag@gmail.com", "한글이메일@테스트.kr"};

                for (String email : emails) {
                    String encrypted = CryptoUtils.encryptAes(email);
                    String decrypted = CryptoUtils.decryptAes(encrypted);
                    assertThat(decrypted).isEqualTo(email);
                }
            }
        }

        @Test
        @DisplayName("다양한 유니코드 문자의 암호화/복호화 라운드트립")
        void unicodeRoundTrip() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                String[] texts = {"日本語テスト", "中文测试", "العربية", "🎉🎊🎁", "Привет мир"};

                for (String text : texts) {
                    String encrypted = CryptoUtils.encryptAes(text);
                    String decrypted = CryptoUtils.decryptAes(encrypted);
                    assertThat(decrypted).isEqualTo(text);
                }
            }
        }
    }
}
