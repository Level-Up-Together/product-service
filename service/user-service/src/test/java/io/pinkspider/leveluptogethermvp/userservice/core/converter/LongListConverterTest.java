package io.pinkspider.leveluptogethermvp.userservice.core.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LongListConverterTest {

    private final LongListConverter converter = new LongListConverter();

    @Test
    @DisplayName("목록을 쉼표 구분 문자열로 저장한다")
    void toDb_joinsWithComma() {
        assertThat(converter.convertToDatabaseColumn(List.of(3L, 7L, 12L))).isEqualTo("3,7,12");
    }

    @Test
    @DisplayName("null/빈 목록은 NULL 로 저장한다 (= 전체)")
    void toDb_emptyBecomesNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToDatabaseColumn(List.of())).isNull();
    }

    @Test
    @DisplayName("null 원소는 건너뛴다")
    void toDb_skipsNullElements() {
        List<Long> withNull = new ArrayList<>();
        withNull.add(1L);
        withNull.add(null);
        withNull.add(2L);
        assertThat(converter.convertToDatabaseColumn(withNull)).isEqualTo("1,2");
    }

    @Test
    @DisplayName("문자열을 목록으로 읽는다 (공백 허용)")
    void toEntity_parses() {
        assertThat(converter.convertToEntityAttribute("3, 7 ,12")).containsExactly(3L, 7L, 12L);
    }

    @Test
    @DisplayName("NULL/빈 문자열은 빈 가변 목록으로 읽는다")
    void toEntity_nullBecomesMutableEmptyList() {
        List<Long> fromNull = converter.convertToEntityAttribute(null);
        List<Long> fromBlank = converter.convertToEntityAttribute("  ");
        assertThat(fromNull).isEmpty();
        assertThat(fromBlank).isEmpty();
        fromNull.add(1L); // 가변이어야 엔티티 setter 없이도 다룰 수 있다
        assertThat(fromNull).containsExactly(1L);
    }

    @Test
    @DisplayName("숫자가 아닌 토큰과 빈 토큰은 조용히 버린다")
    void toEntity_ignoresGarbageTokens() {
        assertThat(converter.convertToEntityAttribute("1,,abc,2,")).containsExactly(1L, 2L);
    }
}
