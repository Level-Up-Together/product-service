package io.pinkspider.global.converter;

import io.pinkspider.global.util.CryptoUtils;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute != null ? CryptoUtils.encryptAes(attribute) : null;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData != null ? CryptoUtils.decryptAes(dbData) : null;
    }
}
