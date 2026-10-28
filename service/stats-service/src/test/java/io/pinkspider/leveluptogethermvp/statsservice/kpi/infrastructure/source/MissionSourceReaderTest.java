package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionDailyCounts;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionSourceReader 테스트 (LUT-601)")
class MissionSourceReaderTest {

    @Mock private NamedParameterJdbcTemplate jdbc;

    @Test
    @DisplayName("완료 수·유니크 유저 수를 읽고, 숫자가 아니면 0")
    void counts() {
        MissionSourceReader reader = new MissionSourceReader(jdbc);
        Map<String, Object> row = new HashMap<>();
        row.put("completions", 4L);
        row.put("users", null);
        when(jdbc.queryForMap(eq(MissionSourceReader.COUNT_COMPLETIONS), anyMap())).thenReturn(row);

        MissionDailyCounts c =
                reader.countCompletions(
                        LocalDateTime.of(2026, 10, 22, 15, 0),
                        LocalDateTime.of(2026, 10, 23, 15, 0));

        assertThat(c.completions()).isEqualTo(4);
        assertThat(c.activeUsers()).isZero();
        assertThat(MissionSourceReader.COUNT_COMPLETIONS)
                .contains("daily_mission_instance")
                .contains("UNION ALL");
    }
}
