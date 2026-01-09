package io.pinkspider;

import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AES 키 생성 유틸리티 테스트
 * Spring 컨텍스트 불필요 - 순수 Java 암호화 기능 테스트
 */
@Slf4j
public class AesKeyGenerator {

    @Test
    public void keyGenerator() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        SecretKey secretKey = keyGenerator.generateKey();

        byte[] iv = new byte[16];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(iv);

        log.info("Secret Key (Base64} : {}", Base64.getEncoder().encodeToString(secretKey.getEncoded()));
        log.info("Secret Key (native} : {}", secretKey);
        log.info("IV (Base64} : {}", Base64.getEncoder().encodeToString(iv));
    }

    @Test
    public void generateBase64RandomKey() {
        int AES_KEY_SIZE = 32; //256 bit
        int IV_SIZE = 16;

        byte[] secretKeyBytes = new byte[AES_KEY_SIZE];
        byte[] ivBytes = new byte[IV_SIZE];

        new SecureRandom().nextBytes(secretKeyBytes);
        new SecureRandom().nextBytes(ivBytes);

        log.info("secretKeyBytes: {}", Base64.getEncoder().encodeToString(secretKeyBytes));
        log.info("ivBytes: {}", Base64.getEncoder().encodeToString(ivBytes));
    }

    @Test
    public void generateHexRandomKey() {
        int AES_KEY_SIZE = 32; //256 bit
        int IV_SIZE = 16;

        byte[] secretKeyBytes = new byte[AES_KEY_SIZE];
        byte[] ivBytes = new byte[IV_SIZE];

        new SecureRandom().nextBytes(secretKeyBytes);
        new SecureRandom().nextBytes(ivBytes);

        log.info("secretKeyBytes: {}", bytesToHex(secretKeyBytes));
        log.info("ivBytes: {}", bytesToHex(ivBytes));
    }

    @Test
    public void generatePBKDF2Key() throws NoSuchAlgorithmException, InvalidKeySpecException {
        String password = "moneymovepassword";
        String salt = "randomSalt";
        int keySize = 32;

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), 10000, keySize * 8);
        byte[] key = factory.generateSecret(spec).getEncoded();

        log.info(Base64.getEncoder().encodeToString(key));
    }

    @Test
    public void generateRandomString() {
        String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(32);

        for (int i = 0; i < 16; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }

        log.info(sb.toString());
    }

    @Test
    @DisplayName("32자리 랜덤문자열 만들고 그걸로 secretkey 만들기")
    public void generateRandomStringAndGenerateKey() {
        int size = 32;
        String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(size);

        for (int i = 0; i < size; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }

        log.info("32자 문자열: {}", sb);
        log.info(String.valueOf(sb.length()));
        log.info("16자 문자열: {}", sb.substring(0, 16));

        String secretKeyBase64 = Base64.getEncoder().encodeToString(sb.toString().getBytes());
        String ivBase64 = Base64.getEncoder().encodeToString(sb.substring(0, 16).getBytes());
        String decoded = new String(Base64.getDecoder().decode(secretKeyBase64));

        log.info("secretKeyBase64: {}", secretKeyBase64);
        log.info("ivBase64: {}", ivBase64);
        log.info("decoded: {}", decoded); // sb와 같아야 함.
    }

    @Test
    @DisplayName("32자리 지정문자열로 secretkey 만들기")
    public void generateKeyWithString() {

        String input = "MONEYMOVE_SAVINGSBANK_SECRET_KEY";
        log.info(String.valueOf(input.length()));

        String secretKeyBase64 = Base64.getEncoder().encodeToString(input.getBytes());
        String ivBase64 = Base64.getEncoder().encodeToString(input.substring(0, 16).getBytes());
        String decoded = new String(Base64.getDecoder().decode(secretKeyBase64));

        log.info("secretKeyBase64: {}", secretKeyBase64);
        log.info("ivBase64: {}", ivBase64);
        log.info("decoded: {}", decoded); // sb와 같아야 함.
    }

    @Test
    public void bigDecimal() {
        String income = "103582500";

        log.info(String.valueOf(new BigDecimal(income)));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }

}
