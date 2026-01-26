package io.pinkspider.leveluptogethermvp.util;

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
 * 이메일을 현재 키로 암호화하는 헬퍼 테스트
 * DML 파일 수정 시 사용
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("이메일 암호화 헬퍼")
class EmailEncryptionHelper {

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
    @DisplayName("새 이메일 암호화 - DML용 암호화 값 생성")
    void generateEncryptedEmails() {
        try (MockedStatic<CryptoMetaDataLoader> mockedLoader = Mockito.mockStatic(CryptoMetaDataLoader.class)) {
            mockedLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(productionCryptoMetaData);

            System.out.println("=== 이메일 암호화 결과 (DML용) ===\n");

            // 수정이 필요한 이메일들
            String[][] emailsToEncrypt = {
                {"테스트유저", "ceo@pink-spider.io"},
            };

            for (String[] data : emailsToEncrypt) {
                String nickname = data[0];
                String email = data[1];
                String encrypted = CryptoUtils.encryptAes(email);

                System.out.println("-- " + nickname + " --");
                System.out.println("원본 이메일: " + email);
                System.out.println("암호화 값: " + encrypted);
                System.out.println();

                // 검증
                String decrypted = CryptoUtils.decryptAes(encrypted);
                System.out.println("복호화 검증: " + decrypted);
                System.out.println("일치 여부: " + (email.equals(decrypted) ? "✓" : "✗"));
                System.out.println();
            }
        }
    }
}
