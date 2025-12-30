package io.pinkspider.global.converter;

import io.pinkspider.global.util.CryptoUtils;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

@Converter
@Slf4j
public class CryptoConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute != null ? CryptoUtils.encryptAes(attribute) : null;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return CryptoUtils.decryptAes(dbData);
        } catch (Exception e) {
            // 복호화 실패 시 (키 불일치 또는 평문 데이터)
            // 이메일 형식인 경우 평문으로 판단하여 그대로 반환
            if (dbData.contains("@") && !dbData.contains("=")) {
                log.warn("암호화되지 않은 데이터 감지 (평문 이메일): {}", maskEmail(dbData));
                return dbData;
            }
            // Base64 형식의 암호화 데이터이지만 키 불일치로 복호화 실패
            log.error("암호화 키 불일치로 복호화 실패. 데이터 재암호화가 필요합니다. value_length={}", dbData.length());
            // 개발 환경에서는 마스킹된 값을 반환하여 서비스는 계속 동작하도록 함
            return "decryption_failed@placeholder.com";
        }
    }

    /**
     * 로깅을 위한 이메일 마스킹
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 2) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
