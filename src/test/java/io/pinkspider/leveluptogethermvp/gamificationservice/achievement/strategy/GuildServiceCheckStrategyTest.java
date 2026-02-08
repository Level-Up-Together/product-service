package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuildServiceCheckStrategyTest {

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @InjectMocks
    private GuildServiceCheckStrategy strategy;

    private static final String TEST_USER_ID = "test-user-123";

    private Achievement createTestAchievement(Long id, String dataField, String operator, int requiredCount) {
        Achievement achievement = Achievement.builder()
            .name("테스트 업적")
            .checkLogicDataSource("GUILD_SERVICE")
            .checkLogicDataField(dataField)
            .comparisonOperator(operator)
            .requiredCount(requiredCount)
            .build();
        setId(achievement, id);
        return achievement;
    }

    private GuildMember createTestGuildMember(Long id, String userId, GuildMemberRole role) {
        GuildMember member = GuildMember.builder()
            .userId(userId)
            .role(role)
            .status(GuildMemberStatus.ACTIVE)
            .build();
        setId(member, id);
        return member;
    }

    @Nested
    @DisplayName("getDataSource 테스트")
    class GetDataSourceTest {

        @Test
        @DisplayName("데이터 소스를 반환한다")
        void getDataSource_returnsGuildService() {
            // when
            String result = strategy.getDataSource();

            // then
            assertThat(result).isEqualTo("GUILD_SERVICE");
        }
    }

    @Nested
    @DisplayName("fetchCurrentValue 테스트")
    class FetchCurrentValueTest {

        @Test
        @DisplayName("길드 멤버이면 isGuildMember가 true를 반환한다")
        void fetchCurrentValue_isGuildMember_true() {
            // given
            GuildMember member = createTestGuildMember(1L, TEST_USER_ID, GuildMemberRole.MEMBER);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID))
                .thenReturn(List.of(member));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "isGuildMember");

            // then
            assertThat(result).isEqualTo(true);
        }

        @Test
        @DisplayName("길드 멤버가 아니면 isGuildMember가 false를 반환한다")
        void fetchCurrentValue_isGuildMember_false() {
            // given
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID))
                .thenReturn(Collections.emptyList());

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "isGuildMember");

            // then
            assertThat(result).isEqualTo(false);
        }

        @Test
        @DisplayName("길드 마스터이면 isGuildMaster가 true를 반환한다")
        void fetchCurrentValue_isGuildMaster_true() {
            // given
            GuildMember master = createTestGuildMember(1L, TEST_USER_ID, GuildMemberRole.MASTER);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID))
                .thenReturn(List.of(master));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "isGuildMaster");

            // then
            assertThat(result).isEqualTo(true);
        }

        @Test
        @DisplayName("길드 마스터가 아니면 isGuildMaster가 false를 반환한다")
        void fetchCurrentValue_isGuildMaster_false() {
            // given
            GuildMember member = createTestGuildMember(1L, TEST_USER_ID, GuildMemberRole.MEMBER);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID))
                .thenReturn(List.of(member));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "isGuildMaster");

            // then
            assertThat(result).isEqualTo(false);
        }

        @Test
        @DisplayName("알 수 없는 필드면 false를 반환한다")
        void fetchCurrentValue_unknownField_returnsFalse() {
            // given
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID))
                .thenReturn(Collections.emptyList());

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "unknownField");

            // then
            assertThat(result).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("checkCondition 테스트")
    class CheckConditionTest {

        @Test
        @DisplayName("Boolean 조건을 만족하면 true를 반환한다")
        void checkCondition_boolean_satisfied_returnsTrue() {
            // given
            Achievement achievement = createTestAchievement(1L, "isGuildMember", "EQ", 1);
            GuildMember member = createTestGuildMember(1L, TEST_USER_ID, GuildMemberRole.MEMBER);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID))
                .thenReturn(List.of(member));

            // when
            boolean result = strategy.checkCondition(TEST_USER_ID, achievement);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Boolean 조건을 만족하지 않으면 false를 반환한다")
        void checkCondition_boolean_notSatisfied_returnsFalse() {
            // given
            Achievement achievement = createTestAchievement(1L, "isGuildMaster", "EQ", 1);
            GuildMember member = createTestGuildMember(1L, TEST_USER_ID, GuildMemberRole.MEMBER);
            when(guildMemberRepository.findAllActiveGuildMemberships(TEST_USER_ID))
                .thenReturn(List.of(member));

            // when
            boolean result = strategy.checkCondition(TEST_USER_ID, achievement);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("dataField가 null이면 false를 반환한다")
        void checkCondition_nullDataField_returnsFalse() {
            // given
            Achievement achievement = createTestAchievement(1L, null, "EQ", 1);

            // when
            boolean result = strategy.checkCondition(TEST_USER_ID, achievement);

            // then
            assertThat(result).isFalse();
        }
    }
}
