package io.pinkspider.global.util;

import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.component.metaredis.CryptoMetaDataLoader;
import io.pinkspider.global.constants.CryptoUtilConstants;
import io.pinkspider.global.domain.redis.CryptoMetaData;
import io.pinkspider.global.exception.CryptoException;
import io.pinkspider.global.exception.CustomException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Component;

@Component
public class CryptoUtils {

    public static String encryptAes(String str) throws CryptoException {
        try {
            CryptoMetaData cryptoMetaData = CryptoMetaDataLoader.getCryptoMetaDataDto(); // redis에서 읽어옴
            byte[] keyData = Base64.decodeBase64(cryptoMetaData.getSecretKey());
            SecretKey secureKey = new SecretKeySpec(keyData, CryptoUtilConstants.CRYPTO_ALGORITHM_AES);
            Cipher c = Cipher.getInstance(cryptoMetaData.getCipher());
            byte[] iv = Base64.decodeBase64(cryptoMetaData.getIv());
            c.init(Cipher.ENCRYPT_MODE, secureKey, new IvParameterSpec(iv));
            byte[] encrypted = c.doFinal(str.getBytes(StandardCharsets.UTF_8));
            return new String(Base64.encodeBase64(encrypted));
        } catch (Exception e) {
            e.printStackTrace();
            throw new CryptoException(ApiStatus.CRYPTO_ENCRYPT_ERROR.getResultCode(), ApiStatus.CRYPTO_ENCRYPT_ERROR.getResultMessage());
        }
    }

    public static String decryptAes(String str) throws CustomException {
        try {
            CryptoMetaData cryptoMetaData = CryptoMetaDataLoader.getCryptoMetaDataDto(); // redis에서 읽어옴
            byte[] keyData = Base64.decodeBase64(cryptoMetaData.getSecretKey());
            SecretKey secureKey = new SecretKeySpec(keyData, CryptoUtilConstants.CRYPTO_ALGORITHM_AES);
            Cipher c = Cipher.getInstance(cryptoMetaData.getCipher());
            byte[] iv = Base64.decodeBase64(cryptoMetaData.getIv());
            c.init(Cipher.DECRYPT_MODE, secureKey, new IvParameterSpec(iv));
            byte[] byteStr = Base64.decodeBase64(str.getBytes());
            return new String(c.doFinal(byteStr), StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            throw new CryptoException(ApiStatus.CRYPTO_DECRYPT_ERROR.getResultCode(), ApiStatus.CRYPTO_DECRYPT_ERROR.getResultMessage());
        }
    }

    public static String encryptSha256(String password) throws CustomException {
        try {
            MessageDigest digest = MessageDigest.getInstance(CryptoUtilConstants.CRYPTO_ALGORITHM_SHA256);
            byte[] hash = digest.digest(password.getBytes(CryptoUtilConstants.CRYPTO_CHARSET_UTF8));
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            throw new CryptoException(ApiStatus.CRYPTO_ENCRYPT_ERROR.getResultCode(), ApiStatus.CRYPTO_ENCRYPT_ERROR.getResultMessage());
        }
    }
}
