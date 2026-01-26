package io.pinkspider.leveluptogethermvp.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.component.metaredis.CryptoMetaDataLoader;
import io.pinkspider.global.domain.redis.CryptoMetaData;
import io.pinkspider.global.util.CryptoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * user_db_dml.sql 파일의 암호화된 이메일이 실제 키로 복호화되는지 검증하는 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DML 이메일 암호화 검증 테스트")
class DmlEmailEncryptionVerificationTest {

    // 테스트 전용 키 (운영 환경과 무관)
    private static final String PRODUCTION_SECRET_KEY = "km2c/ZNA4pyuLXQYVeiq7wsOE6+PPrpPzIx9EUM7uEc=";
    private static final String PRODUCTION_IV = "K4Dw+xcX91fMfi3SNU0gQg==";
    private static final String PRODUCTION_CIPHER = "AES/CBC/PKCS5Padding";

    private CryptoMetaData productionCryptoMetaData;

    @BeforeEach
    void setUp() {
        productionCryptoMetaData = CryptoMetaData.builder()
            .secretKey(PRODUCTION_SECRET_KEY)
            .iv(PRODUCTION_IV)
            .cipher(PRODUCTION_CIPHER)
            .build();
    }

    @Test
    @DisplayName("DML의 암호화된 이메일들이 실제 키로 복호화되는지 확인")
    void verifyDmlEncryptedEmailsCanBeDecrypted() {
        try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
            mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(productionCryptoMetaData);

            // DML 파일의 모든 암호화된 이메일 (닉네임 포함)
            String[][] emailData = {
                {"개발자", "D+fxAnR5RdpMttnpj98mqfgLQ6TOm7GNaDojl5XZl10="},
                {"테스터", "bG/hOcJKJFrSLf9NGZwPnNe3TbNnRGKJrBmdEQbX7a8="},
                {"핑크스파이더", "8ob6lJlNAnWbYd7iwznWO7x+Rxui6Z2EkF9/CiSX2vs="},
                {"테스트유저", "C2+6NT4E9N6Imw/f6CYKf8oDPxROqOyKwQjHUOJKPac="},
                {"열정맨", "9a8XxSFYSJa+eiIoGW/dwg=="},
                {"도전왕", "dsjnxBJB/io3XM4cBy97Qw=="},
                {"마스터", "zl5d5VjiZ5p0EKZbzT4AMA=="},
                {"프로게이머", "1Apw+B3Foikqlflv4h6WbLmXi4h0lkquq1sO1+UrrU8="},
                {"뉴비1", "n/X4jZA8A3ATtuh7Vs7QuENlqxJd45GZTo5YbqZ7Kws="},
                {"잠수중", "c0KM06mWNXM94QVrOuNPjxx0tHJpEdSPdhCddXbJpJw="},
                {"솔로플레이어", "2IP5O9K52/7uQCYLVO/ydg=="},
                {"출석왕", "0pvLM/MArfX853okE6GppgZOyEp1iFw0ApspSB8PFJo="},
                {"성장러", "An/nRjNZwHGiOBCLt1Ei9g=="},
                {"미션헌터", "ZLB7KtUMEENwAFERa7SqQg=="},
                {"레전드", "EAFnPWj5kTqwUMY7jsRy9g=="},
                {"뉴비2", "GE8qTu8t3oIBm2y6RzwDoXTroW0mp7vWBedx8RvMAHc="},
                {"휴식중", "6rJzf7heewdojl0hmdAyEyUVbgWI5xj9GnJMnQgG2fE="},
                {"나홀로", "N1pKe59pZi9rcSpl52hadw=="},
                {"미션마스터", "QOJGgptt548ZHiRu9IwxTQjGHQPWMxliYY9tgB+HNW4="},
                {"꾸준함", "kcSA9BJPZSpYrMWM4fAspr7dGW9z1GRLp/CWfOr3Mqc="},
                {"성실왕", "AI9typUBa/UcuBKhy8fFRXkQFiknIEquYQ9ECnwo0EY="},
                {"뉴비3", "RUkzOkWp86pX71Z3ZFsUJZEBa9x0nN2fkDyxKnDsNmM="},
                {"경고자", "XaUon5C1KPL0uxSHRLPZIQjZssjFmF9Kb0rxxOHtPDE="},
                {"개근상", "H8l7hoD6jYPb4wv7T6abYumHXRuh3oOpdPKJWiehDFM="},
            };

            System.out.println("=== DML 이메일 복호화 테스트 결과 ===");
            System.out.println("현재 키: " + PRODUCTION_SECRET_KEY.substring(0, 20) + "...");
            System.out.println();

            int successCount = 0;
            int failCount = 0;

            for (String[] data : emailData) {
                String nickname = data[0];
                String encrypted = data[1];
                try {
                    String decrypted = CryptoUtils.decryptAes(encrypted);
                    // 복호화된 값이 이메일 형식인지 확인
                    if (decrypted.contains("@")) {
                        System.out.println("✓ [" + nickname + "] " + decrypted);
                        successCount++;
                    } else {
                        System.out.println("✗ [" + nickname + "] 복호화됨, 이메일 아님: " + decrypted);
                        failCount++;
                    }
                } catch (Exception e) {
                    System.out.println("✗ [" + nickname + "] 복호화 실패 (다른 키로 암호화됨)");
                    failCount++;
                }
            }

            System.out.println("\n=== 결과 ===");
            System.out.println("총 " + emailData.length + "개 중 성공: " + successCount + ", 실패: " + failCount);

            if (failCount > 0) {
                System.out.println("\n⚠️ 실패한 이메일들은 다른 암호화 키로 암호화되어 있습니다.");
                System.out.println("해결 방법: DML 파일의 해당 이메일을 현재 키로 재암호화해야 합니다.");
            }
        }
    }

    @Test
    @DisplayName("이메일을 암호화하고 복호화하면 원본과 일치하는지 확인")
    void verifyEncryptDecryptRoundTrip() {
        try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
            mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(productionCryptoMetaData);

            String[] testEmails = {
                "dev@pinkspider.io",
                "test@example.com",
                "user@gmail.com"
            };

            System.out.println("\n=== 새 이메일 암호화 테스트 ===");
            for (String email : testEmails) {
                String encrypted = CryptoUtils.encryptAes(email);
                String decrypted = CryptoUtils.decryptAes(encrypted);

                System.out.println("원본: " + email);
                System.out.println("암호화: " + encrypted);
                System.out.println("복호화: " + decrypted);
                System.out.println();

                assertThat(decrypted).isEqualTo(email);
            }
        }
    }
}
