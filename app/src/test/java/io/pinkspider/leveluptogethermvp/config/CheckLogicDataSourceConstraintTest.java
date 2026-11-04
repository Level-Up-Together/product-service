package io.pinkspider.leveluptogethermvp.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.CheckLogicDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * LUT-634: 업적 판정 소스 enum({@link CheckLogicDataSource})에 값을 더하면 gamification_db 의 {@code
 * check_logic_type.chk_data_source} 제약도 넓혀야 한다. 탑 소스(TOWER_SERVICE)를 enum 에만 넣어 시드 SQL 이 23514 로
 * 실패했다. 가장 최근 마이그레이션의 제약 목록이 enum 과 같은지 본다.
 */
class CheckLogicDataSourceConstraintTest {

    private static final Path DIR = Path.of("src/main/resources/db/migration/gamification");
    private static final Pattern CONSTRAINT =
            Pattern.compile(
                    "chk_data_source\\s+CHECK\\s*\\(\\s*data_source\\s+IN\\s*\\(([^)]*)\\)",
                    Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("가장 최근 chk_data_source 제약이 CheckLogicDataSource enum 값과 같다")
    void constraintMatchesEnum() throws IOException {
        Optional<String> latest;
        try (Stream<Path> files = Files.list(DIR)) {
            List<Path> sorted = files.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
            latest =
                    sorted.stream()
                            .map(CheckLogicDataSourceConstraintTest::read)
                            .map(CONSTRAINT::matcher)
                            .filter(Matcher::find)
                            .map(m -> m.group(1))
                            .reduce((first, second) -> second);
        }
        assertThat(latest).as("gamification 마이그레이션에 chk_data_source 정의가 있어야 한다").isPresent();
        Set<String> inConstraint =
                Arrays.stream(latest.get().split(","))
                        .map(s -> s.trim().replace("'", ""))
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> inEnum =
                Arrays.stream(CheckLogicDataSource.values())
                        .map(Enum::name)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(inConstraint).containsExactlyInAnyOrderElementsOf(inEnum);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
