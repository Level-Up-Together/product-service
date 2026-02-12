package io.pinkspider.global.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

@Converter
public class BigDecimalNullCheckConverter implements AttributeConverter<BigDecimal, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @Override
    public BigDecimal convertToEntityAttribute(BigDecimal dbData) {
        return dbData == null ? BigDecimal.ZERO : dbData;
    }
}
