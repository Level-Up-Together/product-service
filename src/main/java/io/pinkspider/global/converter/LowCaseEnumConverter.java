package io.pinkspider.global.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;


// EnumConvertable 위한 추상 클래스
@Converter
@RequiredArgsConstructor
public abstract class LowCaseEnumConverter<T extends Enum<T> & EnumConvertable> implements AttributeConverter<T, String> {

    private final Class<T> clazz;

    // enum 상수 값을 가져와서 lowerCase로 반환한다.
    @Override
    public String convertToDatabaseColumn(T attribute) {
        return attribute.value().toLowerCase();
    }

    // 디비 정보는 lowerCase로 upperCase로 변환후 비교후 해당 enum객체를 반환하게 한다.
    @Override
    public T convertToEntityAttribute(String dbData) {
        T[] enums = clazz.getEnumConstants();
        for (T en : enums) {
            try {
                if (en.value().equals(dbData.toUpperCase())) {
                    return en;
                }
            } catch (NullPointerException e) {
                return (T) en.ofNull();
            }
        }
        throw new UnsupportedOperationException();
    }

}
