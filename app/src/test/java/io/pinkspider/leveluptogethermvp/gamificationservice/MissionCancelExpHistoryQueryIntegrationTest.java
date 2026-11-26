package io.pinkspider.leveluptogethermvp.gamificationservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.pinkspider.global.enums.ExpSourceType;
import io.pinkspider.global.enums.GuildExpSourceType;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.ExperienceHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.ExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildExperienceHistory;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-860: 완료한 미션 수행 취소로 바뀐 경험치 내역 합계 쿼리 검증.
 *
 * <p>취소는 금액 전체를 음수 행({@code MISSION_CANCEL}, 마일스톤 보너스는 {@code DAILY_MISSION_MILESTONE}, 길드는 {@code
 * GUILD_MISSION_EXECUTION})으로 남긴다. 기간 합계 쿼리의 필터가 {@code expAmount > 0} 에서 "양수 행 + 그 음수 행" 으로 바뀌었고,
 * 순합이 0 이하인 유저·길드는 {@code HAVING}(단건 합계는 {@code GREATEST})으로 뺀다. 조건이 모두 쿼리 안에 있어 Mockito 단위 테스트로는
 * 검증되지 않으므로 실제 SQL 실행 결과를 H2 로 확인한다.
 *
 * <p>캘린더 일별 합계({@code sumDailyExpByUserIdAndPeriod})는 PostgreSQL 전용 문법({@code AT TIME ZONE} 두 번,
 * 예약어 별칭 {@code day})이라 H2 가 해석하지 못해 여기서 다루지 않는다.
 *
 * <p>{@code created_at} 은 저장 시각으로 찍히므로(auditing, updatable=false) 저장한 뒤 SQL 로 원하는 시각으로 옮긴다. 다른 테스트가
 * 넣은 행과 섞이지 않게 먼 미래의 하루({@link #DAY})를 기간으로 쓴다.
 */
@SpringBootTest
@ActiveProfiles("test")
class MissionCancelExpHistoryQueryIntegrationTest {

    private static final String PREFIX = "lut860-";

    /** 조회 기간 = 이 하루 */
    private static final LocalDateTime DAY = LocalDateTime.of(2031, 3, 10, 0, 0);

    private static final LocalDateTime NEXT_DAY = DAY.plusDays(1);

    /** 기간 안의 한 시각 */
    private static final LocalDateTime IN = DAY.plusHours(12);

    /** 기간 밖(전날) */
    private static final LocalDateTime BEFORE = DAY.minusHours(1);

    @Nested
    @DisplayName("ExperienceHistoryRepository — 유저 경험치 기간 합계")
    @Transactional(transactionManager = "gamificationTransactionManager")
    class UserExperienceHistory {

        private static final String EXERCISE = PREFIX + "운동";
        private static final String READING = PREFIX + "독서";

        @Autowired private ExperienceHistoryRepository repository;

        @PersistenceContext(unitName = "gamification")
        private EntityManager entityManager;

        private void save(
                String user,
                ExpSourceType sourceType,
                Long sourceId,
                int amount,
                String categoryName,
                LocalDateTime createdAt) {
            ExperienceHistory history =
                    repository.saveAndFlush(
                            ExperienceHistory.builder()
                                    .userId(PREFIX + user)
                                    .sourceType(sourceType)
                                    .sourceId(sourceId)
                                    .expAmount(amount)
                                    .description(PREFIX + sourceType)
                                    .categoryName(categoryName)
                                    .levelBefore(1)
                                    .levelAfter(1)
                                    .build());
            entityManager
                    .createNativeQuery(
                            "UPDATE experience_history SET created_at = :at WHERE id = :id")
                    .setParameter("at", createdAt)
                    .setParameter("id", history.getId())
                    .executeUpdate();
        }

        private void mission(String user, int amount, String categoryName) {
            save(user, ExpSourceType.MISSION_EXECUTION, 1L, amount, categoryName, IN);
        }

        private void cancel(String user, int amount, String categoryName) {
            save(user, ExpSourceType.MISSION_CANCEL, 1L, -amount, categoryName, IN);
        }

        /**
         * 기간 안의 내역:
         *
         * <ul>
         *   <li>done-cancel-redo: 미션 100 → 취소 → 다른 미션 30 = 순합 30
         *   <li>cancelled-all: 미션 50 → 취소 = 순합 0 (목록에서 빠진다)
         *   <li>milestone: 미션 40 + 마일스톤 20 → 마일스톤 회수 = 순합 40
         *   <li>compensated: 미션 60 + Saga 보상 -60(MISSION_EXECUTION 음수) = 60 (보상 음수 행은 예전처럼 합계에 넣지
         *       않는다)
         *   <li>cancel-only: 전날 완료한 미션(기간 밖 +10)을 기간 안에 취소(-10) = 순합 -10 (목록에서 빠진다)
         *   <li>outside: 기간 밖 내역뿐
         * </ul>
         */
        private void givenHistories() {
            mission("done-cancel-redo", 100, EXERCISE);
            cancel("done-cancel-redo", 100, EXERCISE);
            mission("done-cancel-redo", 30, EXERCISE);

            mission("cancelled-all", 50, EXERCISE);
            cancel("cancelled-all", 50, EXERCISE);

            mission("milestone", 40, EXERCISE);
            save("milestone", ExpSourceType.DAILY_MISSION_MILESTONE, 3L, 20, READING, IN);
            save("milestone", ExpSourceType.DAILY_MISSION_MILESTONE, 3L, -20, READING, IN);

            mission("compensated", 60, EXERCISE);
            save("compensated", ExpSourceType.MISSION_EXECUTION, 1L, -60, EXERCISE, IN);

            save("cancel-only", ExpSourceType.MISSION_EXECUTION, 1L, 10, EXERCISE, BEFORE);
            cancel("cancel-only", 10, EXERCISE);

            save("outside", ExpSourceType.MISSION_EXECUTION, 1L, 999, EXERCISE, BEFORE);
            save("outside", ExpSourceType.MISSION_EXECUTION, 1L, 999, EXERCISE, NEXT_DAY);
        }

        private List<org.assertj.core.groups.Tuple> userAndExp(List<Object[]> rows) {
            return rows.stream()
                    .filter(row -> ((String) row[0]).startsWith(PREFIX))
                    .map(row -> tuple(row[0], ((Number) row[1]).longValue()))
                    .toList();
        }

        @Test
        @DisplayName("기간 랭킹(주간·월간·시즌): 취소 음수 행을 함께 더하고, 순합이 0 이하인 유저는 목록에서 빠진다")
        void findUserExpRankingByPeriod() {
            givenHistories();

            List<Object[]> rows = repository.findUserExpRankingByPeriod(DAY, NEXT_DAY);

            assertThat(userAndExp(rows))
                    .containsExactly(
                            tuple(PREFIX + "compensated", 60L),
                            tuple(PREFIX + "milestone", 40L),
                            tuple(PREFIX + "done-cancel-redo", 30L));
        }

        @Test
        @DisplayName("친구 랭킹: 주어진 유저 중 순합이 양수인 유저만 행이 있다")
        void findUserExpByPeriodAndUserIdIn() {
            givenHistories();

            List<Object[]> rows =
                    repository.findUserExpByPeriodAndUserIdIn(
                            List.of(
                                    PREFIX + "done-cancel-redo",
                                    PREFIX + "cancelled-all",
                                    PREFIX + "cancel-only",
                                    PREFIX + "outside",
                                    PREFIX + "nobody"),
                            DAY,
                            NEXT_DAY);

            assertThat(userAndExp(rows)).containsExactly(tuple(PREFIX + "done-cancel-redo", 30L));
        }

        @Test
        @DisplayName("오늘의 플레이어(MVP): 카테고리 있는 행만, 취소를 뺀 순합으로 줄 세우고 순합 0 이하는 빠진다")
        void findTopExpGainersByPeriod() {
            givenHistories();
            // 카테고리 없는 경험치(업적 보상 등)는 MVP 합계에 들지 않는다
            save("done-cancel-redo", ExpSourceType.ACHIEVEMENT, 9L, 500, null, IN);

            List<Object[]> all =
                    repository.findTopExpGainersByPeriod(DAY, NEXT_DAY, PageRequest.of(0, 50));
            List<Object[]> top2 =
                    repository.findTopExpGainersByPeriod(DAY, NEXT_DAY, PageRequest.of(0, 2));

            assertThat(userAndExp(all))
                    .containsExactly(
                            tuple(PREFIX + "compensated", 60L),
                            tuple(PREFIX + "milestone", 40L),
                            tuple(PREFIX + "done-cancel-redo", 30L));
            assertThat(userAndExp(top2))
                    .containsExactly(
                            tuple(PREFIX + "compensated", 60L), tuple(PREFIX + "milestone", 40L));
        }

        @Test
        @DisplayName("카테고리별 오늘의 플레이어: 그 카테고리의 순합으로 — 마일스톤을 회수한 카테고리에는 아무도 없다")
        void findTopExpGainersByCategoryAndPeriod() {
            givenHistories();

            List<Object[]> exercise =
                    repository.findTopExpGainersByCategoryAndPeriod(
                            EXERCISE, DAY, NEXT_DAY, PageRequest.of(0, 50));
            List<Object[]> reading =
                    repository.findTopExpGainersByCategoryAndPeriod(
                            READING, DAY, NEXT_DAY, PageRequest.of(0, 50));

            assertThat(userAndExp(exercise))
                    .containsExactly(
                            tuple(PREFIX + "compensated", 60L),
                            tuple(PREFIX + "milestone", 40L),
                            tuple(PREFIX + "done-cancel-redo", 30L));
            assertThat(reading).isEmpty();
        }

        @Test
        @DisplayName("카테고리별 레벨 랭킹(전체 기간): 취소를 뺀 순합으로, 순합 0 이하는 빠진다")
        void findUserExpRankingByCategory() {
            givenHistories();

            List<Object[]> rows =
                    repository
                            .findUserExpRankingByCategory(EXERCISE, Pageable.unpaged())
                            .getContent();

            // 전체 기간이라 기간 밖 내역도 든다: outside 1998, cancel-only 10 - 10 = 0(빠짐)
            assertThat(userAndExp(rows))
                    .containsExactly(
                            tuple(PREFIX + "outside", 1998L),
                            tuple(PREFIX + "compensated", 60L),
                            tuple(PREFIX + "milestone", 40L),
                            tuple(PREFIX + "done-cancel-redo", 30L));
        }

        @Test
        @DisplayName("카테고리 유저 수: 그 카테고리에 내역이 있는 유저를 센다")
        void countUsersByCategory() {
            givenHistories();

            // 운동: done-cancel-redo, cancelled-all, milestone, compensated, cancel-only, outside
            assertThat(repository.countUsersByCategory(EXERCISE)).isEqualTo(6L);
            assertThat(repository.countUsersByCategory(READING)).isEqualTo(1L);
            assertThat(repository.countUsersByCategory(PREFIX + "없는 카테고리")).isZero();
        }

        @Test
        @DisplayName("유저 기간 합계: 취소를 뺀 순합이고, 음수가 되면 0 으로 답한다")
        void sumExpByUserIdAndPeriod() {
            givenHistories();

            assertThat(
                            repository.sumExpByUserIdAndPeriod(
                                    PREFIX + "done-cancel-redo", DAY, NEXT_DAY))
                    .isEqualTo(30L);
            assertThat(repository.sumExpByUserIdAndPeriod(PREFIX + "cancelled-all", DAY, NEXT_DAY))
                    .isZero();
            assertThat(repository.sumExpByUserIdAndPeriod(PREFIX + "milestone", DAY, NEXT_DAY))
                    .isEqualTo(40L);
            assertThat(repository.sumExpByUserIdAndPeriod(PREFIX + "compensated", DAY, NEXT_DAY))
                    .isEqualTo(60L);
            // 기간 안에 취소 음수 행만 있다 → -10 이 아니라 0
            assertThat(repository.sumExpByUserIdAndPeriod(PREFIX + "cancel-only", DAY, NEXT_DAY))
                    .isZero();
            assertThat(repository.sumExpByUserIdAndPeriod(PREFIX + "outside", DAY, NEXT_DAY))
                    .isZero();
            assertThat(repository.sumExpByUserIdAndPeriod(PREFIX + "nobody", DAY, NEXT_DAY))
                    .isZero();
        }

        @Test
        @DisplayName("유저·카테고리 기간 합계: 그 카테고리의 순합이고, 음수가 되면 0 으로 답한다")
        void sumExpByUserIdAndCategoryAndPeriod() {
            givenHistories();

            assertThat(
                            repository.sumExpByUserIdAndCategoryAndPeriod(
                                    PREFIX + "done-cancel-redo", EXERCISE, DAY, NEXT_DAY))
                    .isEqualTo(30L);
            assertThat(
                            repository.sumExpByUserIdAndCategoryAndPeriod(
                                    PREFIX + "milestone", EXERCISE, DAY, NEXT_DAY))
                    .isEqualTo(40L);
            assertThat(
                            repository.sumExpByUserIdAndCategoryAndPeriod(
                                    PREFIX + "milestone", READING, DAY, NEXT_DAY))
                    .isZero();
            assertThat(
                            repository.sumExpByUserIdAndCategoryAndPeriod(
                                    PREFIX + "cancel-only", EXERCISE, DAY, NEXT_DAY))
                    .isZero();
        }

        @Test
        @DisplayName("유저의 카테고리별 통계(MVP 이력): 순합과 '완료 − 취소' 건수, 순합 0 이하인 카테고리는 빠진다")
        void findUserCategoryExpByPeriod() {
            mission("stats", 100, EXERCISE);
            cancel("stats", 100, EXERCISE);
            mission("stats", 30, EXERCISE);
            mission("stats", 25, EXERCISE);
            mission("stats", 70, READING);
            // 전부 취소한 카테고리
            mission("stats", 40, PREFIX + "명상");
            cancel("stats", 40, PREFIX + "명상");
            // 카테고리 없는 경험치는 들지 않는다
            save("stats", ExpSourceType.ACHIEVEMENT, 9L, 500, null, IN);

            List<Object[]> rows =
                    repository.findUserCategoryExpByPeriod(PREFIX + "stats", DAY, NEXT_DAY);

            assertThat(rows)
                    .extracting(
                            row -> row[0],
                            row -> ((Number) row[2]).longValue(),
                            row -> ((Number) row[3]).longValue())
                    .containsExactly(tuple(READING, 70L, 1L), tuple(EXERCISE, 55L, 2L));
        }

        @Test
        @DisplayName("어드민 카테고리별 미션 수행 통계: 취소한 수행은 건수·경험치에서 빠지고, 남은 건수가 없는 카테고리는 빠진다")
        void findCategoryMissionStatsByPeriod() {
            String meditation = PREFIX + "명상";
            mission("admin-a", 100, EXERCISE);
            cancel("admin-a", 100, EXERCISE);
            mission("admin-a", 30, EXERCISE);
            mission("admin-b", 25, EXERCISE);
            mission("admin-b", 70, READING);
            // 전부 취소한 카테고리
            mission("admin-b", 40, meditation);
            cancel("admin-b", 40, meditation);
            // 미션 수행·취소가 아닌 출처는 들지 않는다
            save("admin-b", ExpSourceType.DAILY_MISSION_MILESTONE, 3L, 20, READING, IN);

            List<Object[]> rows = repository.findCategoryMissionStatsByPeriod(DAY, NEXT_DAY);

            assertThat(rows)
                    .filteredOn(row -> List.of(EXERCISE, READING, meditation).contains(row[0]))
                    .extracting(
                            row -> row[0],
                            row -> ((Number) row[1]).longValue(),
                            row -> ((Number) row[2]).longValue())
                    .containsExactly(tuple(EXERCISE, 2L, 55L), tuple(READING, 1L, 70L));
        }

        @Test
        @DisplayName("출처별 기간 순합(마일스톤 보너스가 남아 있는가): 지급 − 회수, 출처·출처 ID·기간·유저로 가른다")
        void sumExpByUserIdAndSourceAndPeriod() {
            // 3개 보너스: 받았다가 회수 → 0 / 5개 보너스: 받은 채 → 20
            save("bonus", ExpSourceType.DAILY_MISSION_MILESTONE, 3L, 20, READING, IN);
            save("bonus", ExpSourceType.DAILY_MISSION_MILESTONE, 3L, -20, READING, IN);
            save("bonus", ExpSourceType.DAILY_MISSION_MILESTONE, 5L, 20, READING, IN);
            // 다른 출처·다른 날·다른 유저는 세지 않는다
            save("bonus", ExpSourceType.MISSION_EXECUTION, 5L, 100, READING, IN);
            save("bonus", ExpSourceType.DAILY_MISSION_MILESTONE, 5L, 20, READING, BEFORE);
            save("bonus", ExpSourceType.DAILY_MISSION_MILESTONE, 5L, 20, READING, NEXT_DAY);
            save("other", ExpSourceType.DAILY_MISSION_MILESTONE, 5L, 20, READING, IN);
            // 다시 3개를 채워 또 받았다가 또 회수한 날 — 여전히 0
            save("twice", ExpSourceType.DAILY_MISSION_MILESTONE, 3L, 20, READING, IN);
            save("twice", ExpSourceType.DAILY_MISSION_MILESTONE, 3L, -20, READING, IN);
            save("twice", ExpSourceType.DAILY_MISSION_MILESTONE, 3L, 20, READING, IN);

            assertThat(sumMilestone("bonus", 3L)).isZero();
            assertThat(sumMilestone("bonus", 5L)).isEqualTo(20L);
            assertThat(sumMilestone("twice", 3L)).isEqualTo(20L);
            // 기록이 없으면 null 이 아니라 0
            assertThat(sumMilestone("nobody", 3L)).isZero();
            // 끝 시각은 포함하지 않는다(다음 날 00:00 행 제외), 시작 시각은 포함한다
            assertThat(
                            repository.sumExpByUserIdAndSourceAndPeriod(
                                    PREFIX + "bonus",
                                    ExpSourceType.DAILY_MISSION_MILESTONE,
                                    5L,
                                    NEXT_DAY,
                                    NEXT_DAY.plusDays(1)))
                    .isEqualTo(20L);
        }

        private Long sumMilestone(String user, Long milestone) {
            return repository.sumExpByUserIdAndSourceAndPeriod(
                    PREFIX + user, ExpSourceType.DAILY_MISSION_MILESTONE, milestone, DAY, NEXT_DAY);
        }

        @Test
        @DisplayName("내 순위(나보다 많은 유저 수, native): 취소를 뺀 순합으로 센다")
        void countUsersWithMoreExpByPeriod() {
            givenHistories();

            // 순합: compensated 60, milestone 40, done-cancel-redo 30, cancelled-all 0, cancel-only
            // -10
            assertThat(repository.countUsersWithMoreExpByPeriod(DAY, NEXT_DAY, 30L)).isEqualTo(2L);
            assertThat(repository.countUsersWithMoreExpByPeriod(DAY, NEXT_DAY, 40L)).isEqualTo(1L);
            assertThat(repository.countUsersWithMoreExpByPeriod(DAY, NEXT_DAY, 60L)).isZero();
            // 취소 전 금액(100)으로 세면 done-cancel-redo 가 130 으로 잡혀 1 이 된다
            assertThat(repository.countUsersWithMoreExpByPeriod(DAY, NEXT_DAY, 99L)).isZero();
        }

        @Test
        @DisplayName("카테고리 내 순위(native): 그 카테고리의 취소를 뺀 순합으로 센다")
        void countUsersWithMoreExpByCategoryAndPeriod() {
            givenHistories();

            assertThat(
                            repository.countUsersWithMoreExpByCategoryAndPeriod(
                                    EXERCISE, DAY, NEXT_DAY, 30L))
                    .isEqualTo(2L);
            assertThat(
                            repository.countUsersWithMoreExpByCategoryAndPeriod(
                                    EXERCISE, DAY, NEXT_DAY, 99L))
                    .isZero();
            // 마일스톤을 회수한 카테고리: 순합 0 — 0 보다 많은 유저는 없다
            assertThat(
                            repository.countUsersWithMoreExpByCategoryAndPeriod(
                                    READING, DAY, NEXT_DAY, 0L))
                    .isZero();
        }
    }

    @Nested
    @DisplayName("GuildExperienceHistoryRepository — 길드 경험치 기간 합계")
    @Transactional(transactionManager = "guildTransactionManager")
    class GuildExperience {

        @Autowired private GuildRepository guildRepository;
        @Autowired private GuildExperienceHistoryRepository repository;

        @PersistenceContext(unitName = "guild")
        private EntityManager entityManager;

        private Guild guild(String name) {
            return guildRepository.saveAndFlush(
                    Guild.builder()
                            .name(PREFIX + name)
                            .visibility(GuildVisibility.PUBLIC)
                            .masterId(PREFIX + "master-" + name)
                            .categoryId(1L)
                            .build());
        }

        private void save(
                Guild guild, GuildExpSourceType sourceType, int amount, LocalDateTime createdAt) {
            GuildExperienceHistory history =
                    repository.saveAndFlush(
                            GuildExperienceHistory.builder()
                                    .guild(guild)
                                    .sourceType(sourceType)
                                    .sourceId(1L)
                                    .contributorId(PREFIX + "user")
                                    .expAmount(amount)
                                    .description(PREFIX + sourceType)
                                    .levelBefore(1)
                                    .levelAfter(1)
                                    .build());
            entityManager
                    .createNativeQuery(
                            "UPDATE guild_experience_history SET created_at = :at WHERE id = :id")
                    .setParameter("at", createdAt)
                    .setParameter("id", history.getId())
                    .executeUpdate();
        }

        private void mission(Guild guild, int amount) {
            save(guild, GuildExpSourceType.GUILD_MISSION_EXECUTION, amount, IN);
        }

        @Test
        @DisplayName("MVP 길드: 취소(수행 경험치의 음수 행)를 함께 더하고, 순합이 0 이하인 길드는 목록에서 빠진다")
        void findTopExpGuildsByPeriod() {
            Guild redo = guild("redo"); // 100 → 취소 → 40
            mission(redo, 100);
            mission(redo, -100);
            mission(redo, 40);
            Guild cancelledAll = guild("cancelled-all"); // 50 → 취소
            mission(cancelledAll, 50);
            mission(cancelledAll, -50);
            Guild plain = guild("plain"); // 70
            mission(plain, 70);
            Guild cancelOnly = guild("cancel-only"); // 전날 완료(기간 밖)한 것을 기간 안에 취소
            save(cancelOnly, GuildExpSourceType.GUILD_MISSION_EXECUTION, 10, BEFORE);
            mission(cancelOnly, -10);
            Guild adminMinus = guild("admin-minus"); // 다른 출처의 음수 행은 예전처럼 합계에 넣지 않는다
            mission(adminMinus, 20);
            save(adminMinus, GuildExpSourceType.ADMIN_GRANT, -20, IN);
            Guild outside = guild("outside");
            save(outside, GuildExpSourceType.GUILD_MISSION_EXECUTION, 999, NEXT_DAY);

            List<Object[]> rows =
                    repository.findTopExpGuildsByPeriod(DAY, NEXT_DAY, PageRequest.of(0, 50));

            assertThat(rows)
                    .extracting(row -> row[0], row -> ((Number) row[1]).longValue())
                    .containsExactly(
                            tuple(plain.getId(), 70L),
                            tuple(redo.getId(), 40L),
                            tuple(adminMinus.getId(), 20L));
        }

        @Test
        @DisplayName("길드 기간 합계: 취소를 뺀 순합이고, 음수가 되면 0 으로 답한다")
        void sumExpByGuildIdAndPeriod() {
            Guild redo = guild("redo");
            mission(redo, 100);
            mission(redo, -100);
            mission(redo, 40);
            Guild cancelOnly = guild("cancel-only");
            save(cancelOnly, GuildExpSourceType.GUILD_MISSION_EXECUTION, 10, BEFORE);
            mission(cancelOnly, -10);
            Guild empty = guild("empty");

            assertThat(repository.sumExpByGuildIdAndPeriod(redo.getId(), DAY, NEXT_DAY))
                    .isEqualTo(40L);
            assertThat(repository.sumExpByGuildIdAndPeriod(cancelOnly.getId(), DAY, NEXT_DAY))
                    .isZero();
            assertThat(repository.sumExpByGuildIdAndPeriod(empty.getId(), DAY, NEXT_DAY)).isZero();
        }

        @Test
        @DisplayName("길드 순위(나보다 많은 길드 수, native): 취소를 뺀 순합으로 센다")
        void countGuildsWithMoreExpByPeriod() {
            Guild redo = guild("redo"); // 순합 40
            mission(redo, 100);
            mission(redo, -100);
            mission(redo, 40);
            Guild plain = guild("plain"); // 70
            mission(plain, 70);

            assertThat(repository.countGuildsWithMoreExpByPeriod(DAY, NEXT_DAY, 40L)).isEqualTo(1L);
            assertThat(repository.countGuildsWithMoreExpByPeriod(DAY, NEXT_DAY, 39L)).isEqualTo(2L);
            // 취소 전 금액으로 세면 redo 가 140 으로 잡혀 1 이 된다
            assertThat(repository.countGuildsWithMoreExpByPeriod(DAY, NEXT_DAY, 70L)).isZero();
        }
    }
}
