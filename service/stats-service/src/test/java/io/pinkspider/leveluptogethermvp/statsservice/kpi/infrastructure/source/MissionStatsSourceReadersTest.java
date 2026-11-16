package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.ExcludedUsers;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionCategoryName;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionCompletedGroup;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionCreatedGroup;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.MissionScheduledGroup;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserCompletionStat;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserNickname;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("미션 통계 원천 리더 (LUT-790)")
class MissionStatsSourceReadersTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 10, 31, 15, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 11, 30, 15, 0);
    private static final LocalDate DAY = LocalDate.of(2026, 11, 3);
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 3, 1, 0);
    private static final ExcludedUsers STAFF = ExcludedUsers.of(Set.of("staff"));

    @Mock private NamedParameterJdbcTemplate jdbc;

    /** RowMapper 쿼리 — 결과 1행을 매핑해 돌려준다 */
    @SuppressWarnings("unchecked")
    private <T> void mapOneRow(String sql, ResultSet rs) {
        when(jdbc.query(eq(sql), anyMap(), any(RowMapper.class)))
                .thenAnswer(inv -> List.of(((RowMapper<T>) inv.getArgument(2)).mapRow(rs, 0)));
    }

    /** RowCallbackHandler 쿼리 — 결과 행을 차례로 넘긴다 */
    private void callbackRows(String sql, ResultSet... rows) {
        doAnswer(
                        inv -> {
                            for (ResultSet rs : rows) {
                                ((RowCallbackHandler) inv.getArgument(2)).processRow(rs);
                            }
                            return null;
                        })
                .when(jdbc)
                .query(eq(sql), anyMap(), any(RowCallbackHandler.class));
    }

    /** 그룹 행 공통 열 — 카테고리 ID 는 null 여부를 고른다 */
    private static ResultSet groupRow(Long categoryId) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("day", LocalDate.class)).thenReturn(DAY);
        when(rs.getString("kind")).thenReturn("GUILD");
        when(rs.getString("mode")).thenReturn("TIMED");
        when(rs.getLong("category_id")).thenReturn(categoryId == null ? 0L : categoryId);
        when(rs.wasNull()).thenReturn(categoryId == null);
        return rs;
    }

    @Test
    @DisplayName("생성 묶음 — 고정·삭제 여부와 개수, 카테고리 ID")
    void createdGroups() throws Exception {
        ResultSet rs = groupRow(3L);
        when(rs.getBoolean("pinned")).thenReturn(true);
        when(rs.getBoolean("deleted")).thenReturn(false);
        when(rs.getInt("cnt")).thenReturn(4);
        mapOneRow(MissionSourceReader.GROUP_CREATED, rs);

        assertThat(new MissionSourceReader(jdbc).findCreatedGroups(FROM, TO, STAFF))
                .containsExactly(
                        new MissionCreatedGroup(DAY, "GUILD", "TIMED", 3L, true, false, 4));
    }

    @Test
    @DisplayName("완료 묶음 — 카테고리가 없으면 null, 자동 종료 수")
    void completedGroups() throws Exception {
        ResultSet rs = groupRow(null);
        when(rs.getInt("cnt")).thenReturn(7);
        when(rs.getInt("auto_cnt")).thenReturn(2);
        mapOneRow(MissionSourceReader.GROUP_COMPLETED, rs);

        assertThat(new MissionSourceReader(jdbc).findCompletedGroups(FROM, TO, STAFF))
                .containsExactly(new MissionCompletedGroup(DAY, "GUILD", "TIMED", null, 7, 2));
    }

    @Test
    @DisplayName("예정 묶음 — 예정일(날짜) 범위로 묻고 예정·완료 수를 읽는다")
    @SuppressWarnings("unchecked")
    void scheduledGroups() throws Exception {
        ResultSet rs = groupRow(5L);
        when(rs.getInt("cnt")).thenReturn(10);
        when(rs.getInt("done_cnt")).thenReturn(6);
        mapOneRow(MissionSourceReader.GROUP_SCHEDULED, rs);

        assertThat(new MissionSourceReader(jdbc).findScheduledGroups(DAY, DAY.plusDays(6), STAFF))
                .containsExactly(new MissionScheduledGroup(DAY, "GUILD", "TIMED", 5L, 10, 6));
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(jdbc)
                .query(
                        eq(MissionSourceReader.GROUP_SCHEDULED),
                        params.capture(),
                        any(RowMapper.class));
        assertThat(params.getValue())
                .containsEntry("fromDate", DAY)
                .containsEntry("toDate", DAY.plusDays(6));
        assertThat((Collection<String>) params.getValue().get("excluded")).containsExactly("staff");
    }

    @Test
    @DisplayName("유저별 완료 — 완료 수·타이머 분·마지막 완료 시각")
    void userCompletions() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("user_id")).thenReturn("u1");
        when(rs.getInt("completions")).thenReturn(12);
        when(rs.getLong("timer_minutes")).thenReturn(340L);
        when(rs.getObject("last_completed_at", LocalDateTime.class)).thenReturn(AT);
        mapOneRow(MissionSourceReader.USER_COMPLETIONS, rs);

        assertThat(new MissionSourceReader(jdbc).findUserCompletions(FROM, TO, STAFF))
                .containsExactly(new UserCompletionStat("u1", 12, 340L, AT));
    }

    @Test
    @DisplayName("완료·예정·유저별 쿼리는 일반·고정 두 갈래 모두 제외 유저를 빼고, 지운 미션도 센다(LUT-789)")
    void missionQueriesExcludeUsersAndKeepDeleted() {
        for (String sql :
                List.of(
                        MissionSourceReader.GROUP_COMPLETED,
                        MissionSourceReader.GROUP_SCHEDULED,
                        MissionSourceReader.USER_COMPLETIONS)) {
            assertThat(sql.split("p\\.user_id NOT IN \\(:excluded\\)", -1)).hasSize(3);
            assertThat(sql).contains("UNION ALL").doesNotContain("is_deleted");
        }
        // 완료 조건은 KPI COUNT_COMPLETIONS 와 같다 — 숫자가 KPI 화면과 맞아야 한다
        for (String sql :
                List.of(
                        MissionSourceReader.GROUP_COMPLETED,
                        MissionSourceReader.USER_COMPLETIONS)) {
            assertThat(sql)
                    .contains("e.status = 'COMPLETED' AND e.completed_at >= :from")
                    .contains("d.status = 'COMPLETED' AND d.completed_at >= :from");
        }
        // 예정 = 월간 리포트(LUT-566): 고정 미션은 1회차 전 상태 + 2회차 이상 완료만
        assertThat(MissionSourceReader.GROUP_SCHEDULED)
                .contains("(d.sequence_number = 1 OR d.status = 'COMPLETED')");
        // 생성: 만든 사람이 제외 유저면 빼고, 어드민이 직접 만든 시스템 미션도 뺀다. 지운 미션은 deleted 로 따로 센다
        assertThat(MissionSourceReader.GROUP_CREATED)
                .contains("m.creator_id NOT IN (:excluded)")
                .contains("m.source IS DISTINCT FROM 'SYSTEM' OR m.base_mission_id IS NOT NULL")
                .contains("m.is_deleted AS deleted")
                .containsSubsequence("NOT IN (:excluded)", "GROUP BY");
        // 종류 분류는 어드민 유저 미션 이력과 같다 — 길드가 먼저(길드 미션은 source=USER)
        assertThat(MissionSourceReader.KIND)
                .containsSubsequence("m.type = 'GUILD'", "m.source = 'SYSTEM'", "'PERSONAL'");
    }

    @Test
    @DisplayName("카테고리 이름 — ID 가 없으면 조회하지 않는다")
    void categoryNames() throws Exception {
        MetaSourceReader reader = new MetaSourceReader(jdbc);
        assertThat(reader.findCategoryNames(List.of())).isEmpty();
        verifyNoInteractions(jdbc);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(3L);
        when(rs.getString("name")).thenReturn("운동");
        when(rs.getString("name_en")).thenReturn("Workout");
        callbackRows(MetaSourceReader.FIND_CATEGORY_NAMES, rs);

        assertThat(reader.findCategoryNames(List.of(3L, 99L)))
                .containsExactly(Map.entry(3L, new MissionCategoryName("운동", "Workout")));
    }

    @Test
    @DisplayName("닉네임 — 탈퇴 여부를 함께, ID 가 없으면 조회하지 않는다")
    void nicknames() throws Exception {
        UserSourceReader reader = new UserSourceReader(jdbc);
        assertThat(reader.findNicknames(List.of())).isEmpty();
        verify(jdbc, never()).query(anyString(), anyMap(), any(RowCallbackHandler.class));

        ResultSet active = mock(ResultSet.class);
        when(active.getString("id")).thenReturn("u1");
        when(active.getString("nickname")).thenReturn("Rumi");
        when(active.getString("status")).thenReturn("ACTIVE");
        ResultSet withdrawn = mock(ResultSet.class);
        when(withdrawn.getString("id")).thenReturn("u2");
        when(withdrawn.getString("nickname")).thenReturn("탈퇴한 사용자");
        when(withdrawn.getString("status")).thenReturn("WITHDRAWN");
        callbackRows(UserSourceReader.FIND_NICKNAMES, active, withdrawn);

        assertThat(reader.findNicknames(List.of("u1", "u2")))
                .containsEntry("u1", new UserNickname("Rumi", false))
                .containsEntry("u2", new UserNickname("탈퇴한 사용자", true));
    }

    @Test
    @DisplayName("레벨 — 값이 null 인 행은 빼고, ID 가 없으면 조회하지 않는다")
    void levels() throws Exception {
        GamificationSourceReader reader = new GamificationSourceReader(jdbc);
        assertThat(reader.findLevels(List.of())).isEmpty();
        verify(jdbc, never()).query(anyString(), anyMap(), any(RowCallbackHandler.class));

        ResultSet leveled = mock(ResultSet.class);
        when(leveled.getString("user_id")).thenReturn("u1");
        when(leveled.getInt("current_level")).thenReturn(12);
        when(leveled.wasNull()).thenReturn(false);
        ResultSet empty = mock(ResultSet.class);
        when(empty.getInt("current_level")).thenReturn(0);
        when(empty.wasNull()).thenReturn(true);
        callbackRows(GamificationSourceReader.FIND_LEVELS, leveled, empty);

        assertThat(reader.findLevels(List.of("u1", "u2"))).containsExactly(Map.entry("u1", 12));
    }
}
