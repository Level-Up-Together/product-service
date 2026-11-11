package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionCategoryName;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * LUT-790: meta_db 읽기 전용 조회 — 미션 통계의 카테고리 이름. 미션 행의 {@code category_name} 은 만들 때 이름이 굳어 있어(카테고리 이름을
 * 바꿔도 그대로) 지금 이름을 여기서 읽는다.
 */
public class MetaSourceReader {

    static final String FIND_CATEGORY_NAMES =
            "SELECT id, name, name_en FROM mission_category WHERE id IN (:ids)";

    private final NamedParameterJdbcTemplate jdbc;

    public MetaSourceReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 카테고리 ID → 이름. 지워진 카테고리는 결과에 없다 */
    public Map<Long, MissionCategoryName> findCategoryNames(Collection<Long> ids) {
        Map<Long, MissionCategoryName> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        jdbc.query(
                FIND_CATEGORY_NAMES,
                Map.of("ids", ids),
                rs -> {
                    names.put(
                            rs.getLong("id"),
                            new MissionCategoryName(rs.getString("name"), rs.getString("name_en")));
                });
        return names;
    }
}
