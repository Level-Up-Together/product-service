package io.pinkspider.leveluptogethermvp.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * DML 파일용 암호화된 이메일 값 생성 유틸리티
 *
 * 이 클래스는 테스트 환경에서 사용되는 암호화 키를 사용하여
 * 샘플 이메일을 암호화합니다.
 */
public class EmailEncryptionGenerator {

    // 테스트 환경 암호화 키 (32바이트 = AES-256)
    private static final String SECRET_KEY = "12345678901234567890123456789012";
    // IV (16바이트)
    private static final String IV = "1234567890123456";
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";

    public static String encrypt(String plainText) throws Exception {
        byte[] keyData = SECRET_KEY.getBytes(StandardCharsets.UTF_8);
        SecretKey secureKey = new SecretKeySpec(keyData, "AES");
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        byte[] ivBytes = IV.getBytes(StandardCharsets.UTF_8);
        cipher.init(Cipher.ENCRYPT_MODE, secureKey, new IvParameterSpec(ivBytes));
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static void main(String[] args) throws Exception {
        String[] emails = {
            "user1@gmail.com",
            "user2@gmail.com",
            "vip1@gmail.com",
            "premium1@gmail.com",
            "newbie1@gmail.com",
            "inactive1@gmail.com",
            "solo1@gmail.com",
            "streak1@gmail.com",
            "user3@kakao.com",
            "user4@kakao.com",
            "vip2@kakao.com",
            "newbie2@kakao.com",
            "inactive2@kakao.com",
            "solo2@kakao.com",
            "mission_master@kakao.com",
            "user5@icloud.com",
            "premium2@icloud.com",
            "newbie3@icloud.com",
            "suspended1@icloud.com",
            "streak2@icloud.com"
        };

        System.out.println("-- Encrypted emails for user_db_dml.sql --");
        for (String email : emails) {
            String encrypted = encrypt(email);
            System.out.println("-- " + email);
            System.out.println("'" + encrypted + "',");
        }
    }
}
