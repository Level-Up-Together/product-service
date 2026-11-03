package io.pinkspider.leveluptogethermvp.userservice.core.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LUT-548: {@code List<Long>} ↔ 쉼표 구분 문자열 컬럼 변환기.
 *
 * <p>빈 목록은 NULL 로 저장하고, NULL/빈 문자열은 빈(가변) 목록으로 읽는다 — 호출부가 "비면 전체" 의미를 바로 쓸 수 있게 한다. 숫자로 파싱되지 않는 토큰은
 * 조용히 버린다 (수동 SQL 로 오염된 값이 조회 전체를 깨뜨리지 않도록).
 */
@Converter
public class LongListConverter implements AttributeConverter<List<Long>, String> {

    private static final String DELIMITER = ",";

    @Override
    public String convertToDatabaseColumn(List<Long> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return attribute.stream()
                .filter(v -> v != null)
                .map(String::valueOf)
                .collect(Collectors.joining(DELIMITER));
    }

    @Override
    public List<Long> convertToEntityAttribute(String dbData) {
        List<Long> result = new ArrayList<>();
        if (dbData == null || dbData.isBlank()) {
            return result;
        }
        for (String token : Arrays.asList(dbData.split(DELIMITER))) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                result.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // 잘못된 토큰은 무시
            }
        }
        return result;
    }
}
