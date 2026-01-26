package io.pinkspider.leveluptogethermvp.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.component.metaredis.CryptoMetaDataLoader;
import io.pinkspider.global.converter.CryptoConverter;
import io.pinkspider.global.domain.redis.CryptoMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CryptoConverter 테스트")
class CryptoConverterTest {

    // 테스트 전용 키 (운영 환경과 무관)
    private static final String TEST_SECRET_KEY = "km2c/ZNA4pyuLXQYVeiq7wsOE6+PPrpPzIx9EUM7uEc=";
    private static final String TEST_IV = "K4Dw+xcX91fMfi3SNU0gQg==";
    private static final String TEST_CIPHER = "AES/CBC/PKCS5Padding";

    private CryptoConverter cryptoConverter;
    private CryptoMetaData testCryptoMetaData;

    @BeforeEach
    void setUp() {
        cryptoConverter = new CryptoConverter();
        testCryptoMetaData = CryptoMetaData.builder()
            .secretKey(TEST_SECRET_KEY)
            .iv(TEST_IV)
            .cipher(TEST_CIPHER)
            .build();
    }

    @Nested
    @DisplayName("convertToDatabaseColumn 메서드")
    class ConvertToDatabaseColumnTest {

        @Test
        @DisplayName("문자열을 암호화하여 DB 컬럼값으로 변환한다")
        void convertStringToDbColumn() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                String plainText = "test@example.com";
                String encrypted = cryptoConverter.convertToDatabaseColumn(plainText);

                assertThat(encrypted).isNotNull();
                assertThat(encrypted).isNotEmpty();
                assertThat(encrypted).isNotEqualTo(plainText);
            }
        }

        @Test
        @DisplayName("null 입력 시 null 반환")
        void returnNullForNullInput() {
            String result = cryptoConverter.convertToDatabaseColumn(null);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute 메서드")
    class ConvertToEntityAttributeTest {

        @Test
        @DisplayName("암호화된 DB값을 복호화하여 엔티티 속성으로 변환한다")
        void convertDbColumnToEntityAttribute() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                String plainText = "test@example.com";
                String encrypted = cryptoConverter.convertToDatabaseColumn(plainText);
                String decrypted = cryptoConverter.convertToEntityAttribute(encrypted);

                assertThat(decrypted).isEqualTo(plainText);
            }
        }

        @Test
        @DisplayName("null 입력 시 null 반환")
        void returnNullForNullInput() {
            String result = cryptoConverter.convertToEntityAttribute(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("평문 이메일 데이터 감지 시 그대로 반환")
        void returnPlainEmailAsIs() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                // @ 포함, = 미포함 → 평문 이메일로 판단
                String plainEmail = "plaintext@example.com";
                String result = cryptoConverter.convertToEntityAttribute(plainEmail);

                assertThat(result).isEqualTo(plainEmail);
            }
        }

        @Test
        @DisplayName("키 불일치로 복호화 실패 시 플레이스홀더 반환")
        void returnPlaceholderOnDecryptionFailure() {
            String encrypted;
            // 첫 번째 키로 암호화
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);
                encrypted = cryptoConverter.convertToDatabaseColumn("test@example.com");
            }

            // 다른 키로 복호화 시도 (테스트 전용)
            CryptoMetaData differentKeyMetaData = CryptoMetaData.builder()
                .secretKey("cFSXGDjBl5gNFLOHP+5WVdktX5d7xt9seNAwrUPVwns=") // 다른 테스트 키
                .iv(TEST_IV)
                .cipher(TEST_CIPHER)
                .build();

            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(differentKeyMetaData);

                String result = cryptoConverter.convertToEntityAttribute(encrypted);

                // 복호화 실패 시 플레이스홀더 값 반환
                assertThat(result).isEqualTo("decryption_failed@placeholder.com");
            }
        }
    }

    @Nested
    @DisplayName("라운드트립 테스트")
    class RoundTripTest {

        @Test
        @DisplayName("암호화/복호화 라운드트립이 정상 동작한다")
        void encryptDecryptRoundTrip() {
            try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
                mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(testCryptoMetaData);

                String[] testData = {
                    "test@example.com",
                    "user.name+tag@domain.co.kr",
                    "한글이메일@테스트.kr",
                    "special!@#$%^&*()chars@test.com"
                };

                for (String original : testData) {
                    String encrypted = cryptoConverter.convertToDatabaseColumn(original);
                    String decrypted = cryptoConverter.convertToEntityAttribute(encrypted);
                    assertThat(decrypted).isEqualTo(original);
                }
            }
        }
    }
}
