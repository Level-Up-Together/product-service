package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.ExcludedUsers;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.SignupRecord;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.UserEvent;
import java.sql.ResultSet;
import java.time.LocalDateTime;
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
@DisplayName("첫날 행동 원천 리더 (LUT-779)")
class FirstDaySourceReadersTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 10, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 10, 3, 0, 0);
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 1, 9, 0);
    private static final List<String> IDS = List.of("u1");

    @Mock private NamedParameterJdbcTemplate jdbc;

    /** RowMapper 쿼리 — 결과 1행을 매핑해 돌려준다 */
    @SuppressWarnings("unchecked")
    private <T> void mapOneRow(String sql, ResultSet rs) {
        when(jdbc.query(eq(sql), anyMap(), any(RowMapper.class)))
                .thenAnswer(inv -> List.of(((RowMapper<T>) inv.getArgument(2)).mapRow(rs, 0)));
    }

    /** RowCallbackHandler 쿼리 — 결과 1행을 넘긴다 */
    private void callbackOneRow(String sql, ResultSet rs) {
        doAnswer(
                        inv -> {
                            ((RowCallbackHandler) inv.getArgument(2)).processRow(rs);
                            return null;
                        })
                .when(jdbc)
                .query(eq(sql), anyMap(), any(RowCallbackHandler.class));
    }

    @Test
    @DisplayName("가입자 — 테스트·제외 유저는 빼고 탈퇴자는 남긴다, 가입 시각 순")
    @SuppressWarnings("unchecked")
    void signups() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("id")).thenReturn("u1");
        when(rs.getObject("created_at", LocalDateTime.class)).thenReturn(AT);
        when(rs.getString("signup_device_type")).thenReturn("ios");
        mapOneRow(UserSourceReader.FIND_SIGNUPS, rs);

        List<SignupRecord> rows =
                new UserSourceReader(jdbc).findSignups(FROM, TO, ExcludedUsers.of(Set.of("staff")));

        assertThat(rows).containsExactly(new SignupRecord("u1", AT, "ios"));
        assertThat(UserSourceReader.FIND_SIGNUPS)
                .contains("provider <> 'test'")
                .contains("id NOT IN (:excluded)")
                .doesNotContain("withdrawn_at")
                .contains("ORDER BY created_at");
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(jdbc)
                .query(eq(UserSourceReader.FIND_SIGNUPS), params.capture(), any(RowMapper.class));
        assertThat((java.util.Collection<String>) params.getValue().get("excluded"))
                .containsExactly("staff");
    }

    @Test
    @DisplayName("친구 요청·수락 — 요청자·상대 모두, 요청·수락 시각마다")
    void friendships() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("user_id")).thenReturn("u1");
        when(rs.getString("friend_id")).thenReturn("u2");
        when(rs.getObject("requested_at", LocalDateTime.class)).thenReturn(AT);
        when(rs.getObject("accepted_at", LocalDateTime.class)).thenReturn(null);
        callbackOneRow(UserSourceReader.FIND_FRIENDSHIP_EVENTS, rs);

        List<UserEvent> events = new UserSourceReader(jdbc).findFriendshipEvents(IDS, FROM, TO);

        assertThat(events).containsExactly(new UserEvent("u1", AT), new UserEvent("u2", AT));
        assertThat(UserSourceReader.FIND_FRIENDSHIP_EVENTS)
                .contains("user_id IN (:ids) OR friend_id IN (:ids)")
                .contains("COALESCE(requested_at, created_at)");
    }

    @Test
    @DisplayName("미션 완료 — 일반·고정 미션 모두 COMPLETED 만")
    void completions() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("user_id")).thenReturn("u1");
        when(rs.getObject("at", LocalDateTime.class)).thenReturn(AT);
        mapOneRow(MissionSourceReader.FIND_COMPLETIONS, rs);

        assertThat(new MissionSourceReader(jdbc).findCompletions(IDS, FROM, TO))
                .containsExactly(new UserEvent("u1", AT));
        assertThat(MissionSourceReader.FIND_COMPLETIONS)
                .contains("mission_execution")
                .contains("daily_mission_instance")
                .contains("e.status = 'COMPLETED'")
                .contains("d.status = 'COMPLETED'");
    }

    @Test
    @DisplayName("길드 가입")
    void guildJoins() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("user_id")).thenReturn("u1");
        when(rs.getObject("at", LocalDateTime.class)).thenReturn(AT);
        mapOneRow(GuildSourceReader.FIND_JOINS, rs);

        assertThat(new GuildSourceReader(jdbc).findJoins(IDS, FROM, TO))
                .containsExactly(new UserEvent("u1", AT));
        assertThat(GuildSourceReader.FIND_JOINS).contains("joined_at >= :from");
    }

    @Test
    @DisplayName("피드 좋아요·댓글 — 준 쪽·받은 쪽 모두, 자기 피드는 제외")
    void feed() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("actor_id")).thenReturn("u1");
        when(rs.getString("owner_id")).thenReturn("u9");
        when(rs.getObject("at", LocalDateTime.class)).thenReturn(AT);
        callbackOneRow(FeedSourceReader.FIND_LIKES_AND_COMMENTS, rs);

        assertThat(new FeedSourceReader(jdbc).findLikesAndComments(IDS, FROM, TO))
                .containsExactly(new UserEvent("u1", AT), new UserEvent("u9", AT));
        assertThat(FeedSourceReader.FIND_LIKES_AND_COMMENTS)
                .contains("l.user_id <> f.user_id")
                .contains("c.user_id <> f.user_id");
    }

    @Test
    @DisplayName("DM — 대화 두 사람 모두")
    void directMessages() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("user_id_1")).thenReturn("u1");
        when(rs.getString("user_id_2")).thenReturn("u2");
        when(rs.getObject("at", LocalDateTime.class)).thenReturn(AT);
        callbackOneRow(ChatSourceReader.FIND_DIRECT_MESSAGES, rs);

        assertThat(new ChatSourceReader(jdbc).findDirectMessages(IDS, FROM, TO))
                .containsExactly(new UserEvent("u1", AT), new UserEvent("u2", AT));
    }

    @Test
    @DisplayName("탑 — 전달된 채팅(SENT·MASKED)과 받은 응원")
    void tower() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("user_id")).thenReturn("u1");
        when(rs.getObject("at", LocalDateTime.class)).thenReturn(AT);
        mapOneRow(TowerSourceReader.FIND_CHATS_AND_CHEERS, rs);

        assertThat(new TowerSourceReader(jdbc).findChatsAndCheers(IDS, FROM, TO))
                .containsExactly(new UserEvent("u1", AT));
        assertThat(TowerSourceReader.FIND_CHATS_AND_CHEERS)
                .contains("result IN ('SENT', 'MASKED')")
                .contains("cheer_count > 0");
    }
}
