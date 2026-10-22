package io.pinkspider.leveluptogethermvp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * LUT-595: Flyway 마이그레이션 파일 규칙 가드. 폴더 8개가 있고, 모든 .sql 이 {@code
 * V<major>_<minor>_<patch>_<seq3>__<desc>.sql} 이며, 폴더 안에서 버전이 겹치지 않아야 한다. 규칙을 어기면 서버가 아니라 이 테스트가 먼저
 * 알려 준다.
 */
class FlywayMigrationNamingTest {

    static final Pattern NAME =
            Pattern.compile("^V\\d+_\\d+_\\d+_\\d{3}__[a-z0-9][a-z0-9_]*\\.sql$");
    static final List<String> DATABASES =
            List.of(
                    "user",
                    "mission",
                    "guild",
                    "chat",
                    "meta",
                    "feed",
                    "notification",
                    "gamification");
    static final Path ROOT = Path.of("src/main/resources/db/migration");

    @Test
    @DisplayName("DB 8개 폴더가 모두 있다")
    void allDatabaseFoldersExist() {
        for (String db : DATABASES) {
            assertThat(ROOT.resolve(db)).as("db/migration/%s", db).isDirectory();
        }
    }

    @Test
    @DisplayName("모든 .sql 파일 이름이 규칙에 맞고, 폴더 안에서 버전이 겹치지 않는다")
    void allSqlFilesFollowNamingRule() throws IOException {
        for (String db : DATABASES) {
            Set<String> versions = new HashSet<>();
            try (Stream<Path> files = Files.list(ROOT.resolve(db))) {
                List<String> sqlNames =
                        files.map(p -> p.getFileName().toString())
                                .filter(n -> n.endsWith(".sql"))
                                .sorted()
                                .toList();
                for (String name : sqlNames) {
                    assertThat(NAME.matcher(name).matches())
                            .as(
                                    "%s/%s 는 V<major>_<minor>_<patch>_<seq3>__<desc>.sql 형식이어야 한다",
                                    db, name)
                            .isTrue();
                    String version = name.substring(1, name.indexOf("__"));
                    assertThat(versions.add(version))
                            .as("%s 폴더에 버전 %s 가 두 번 있다", db, version)
                            .isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("이름 규칙 자체: 릴리스 번호 3자리 + 일련번호 3자리 + 소문자 설명")
    void namingPattern() {
        assertThat(NAME.matcher("V1_1_4_001__lut600_add_mission_tag.sql").matches()).isTrue();
        assertThat(NAME.matcher("V10_0_0_123__x.sql").matches()).isTrue();
        // 예전 규칙(릴리스마다 V001 재시작)은 거부
        assertThat(NAME.matcher("V001__lut570_user_level_config.sql").matches()).isFalse();
        // 일련번호는 3자리
        assertThat(NAME.matcher("V1_1_4_1__x.sql").matches()).isFalse();
        // 밑줄 2개 구분자
        assertThat(NAME.matcher("V1_1_4_001_x.sql").matches()).isFalse();
        // 대문자·공백 설명 거부
        assertThat(NAME.matcher("V1_1_4_001__Add Tag.sql").matches()).isFalse();
    }
}
