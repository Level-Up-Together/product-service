package io.pinkspider.leveluptogethermvp.guildservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildExperienceResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildExperienceHistory;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildExperienceHistory.GuildExpSourceType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildLevelConfig;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildLevelConfigRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.global.cache.LevelConfigCacheService;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.levelconfig.domain.entity.LevelConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class GuildExperienceServiceTest {

    @Mock
    private GuildRepository guildRepository;

    @Mock
    private GuildLevelConfigRepository levelConfigRepository;

    @Mock
    private GuildExperienceHistoryRepository historyRepository;

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @Mock
    private LevelConfigCacheService levelConfigCacheService;

    @InjectMocks
    private GuildExperienceService guildExperienceService;

    private Guild testGuild;
    private GuildLevelConfig testLevelConfig;
    private String testUserId;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-id";

        testGuild = Guild.builder()
            .name("테스트 길드")
            .description("테스트 길드 설명")
            .visibility(GuildVisibility.PUBLIC)
            .masterId(testUserId)
            .maxMembers(50)
            .categoryId(1L)
            .build();
        setGuildId(testGuild, 1L);

        testLevelConfig = GuildLevelConfig.builder()
            .level(1)
            .requiredExp(500)
            .cumulativeExp(0)
            .maxMembers(20)
            .title("초보 길드")
            .build();
    }

    private void setGuildId(Guild guild, Long id) {
        try {
            java.lang.reflect.Field idField = Guild.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(guild, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("경험치 추가 테스트")
    class AddExperienceTest {

        @Test
        @DisplayName("정상적으로 경험치를 추가한다")
        void addExperience_success() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(levelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(testLevelConfig));
            when(levelConfigRepository.findByLevel(1)).thenReturn(Optional.of(testLevelConfig));
            when(historyRepository.save(any(GuildExperienceHistory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(levelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(LevelConfig.builder().level(1).requiredExp(100).build());

            // when
            GuildExperienceResponse response = guildExperienceService.addExperience(
                1L, 100, GuildExpSourceType.GUILD_MISSION_EXECUTION, 1L, testUserId, "미션 완료 보상"
            );

            // then
            assertThat(response).isNotNull();
            assertThat(response.getGuildId()).isEqualTo(1L);
            assertThat(response.getCurrentExp()).isEqualTo(100);
            verify(historyRepository).save(any(GuildExperienceHistory.class));
        }

        @Test
        @DisplayName("존재하지 않는 길드에 경험치 추가 시 예외 발생")
        void addExperience_failWhenGuildNotFound() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildExperienceService.addExperience(
                999L, 100, GuildExpSourceType.GUILD_MISSION_EXECUTION, 1L, testUserId, "미션 완료 보상"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("길드를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("경험치 추가로 레벨업 처리")
        void addExperience_levelUp() {
            // given
            testGuild.addExperience(400); // 기존 400 exp

            GuildLevelConfig level2Config = GuildLevelConfig.builder()
                .level(2)
                .requiredExp(800)
                .cumulativeExp(500)
                .maxMembers(30)
                .title("성장하는 길드")
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(levelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(testLevelConfig, level2Config));
            when(levelConfigRepository.findByLevel(any())).thenReturn(Optional.of(testLevelConfig));
            when(historyRepository.save(any(GuildExperienceHistory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(levelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(LevelConfig.builder().level(1).requiredExp(100).build());
            when(levelConfigCacheService.getMaxLevel()).thenReturn(50);

            // when
            GuildExperienceResponse response = guildExperienceService.addExperience(
                1L, 200, GuildExpSourceType.GUILD_MISSION_EXECUTION, 1L, testUserId, "미션 완료 보상"
            );

            // then
            assertThat(response).isNotNull();
            assertThat(testGuild.getCurrentLevel()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("레벨업 시 해당 레벨의 config가 없으면 기본 공식으로 maxMembers가 계산된다")
        void addExperience_levelUpWithNoConfigForNewLevel() {
            // given
            testGuild.addExperience(400);

            // 레벨1 config만 있고 레벨2 config는 없음
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(levelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(testLevelConfig)); // level 1만
            when(levelConfigRepository.findByLevel(any())).thenReturn(Optional.of(testLevelConfig));
            when(historyRepository.save(any(GuildExperienceHistory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(levelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(LevelConfig.builder().level(1).requiredExp(100).build());
            when(levelConfigCacheService.getMaxLevel()).thenReturn(50);

            // when - 400 + 200 = 600 exp, requiredExp = 5 * 100 = 500, 레벨업 가능
            GuildExperienceResponse response = guildExperienceService.addExperience(
                1L, 200, GuildExpSourceType.GUILD_MISSION_EXECUTION, 1L, testUserId, "미션 완료 보상"
            );

            // then
            assertThat(response).isNotNull();
            // 기본 공식: 10 + (level - 1) * 5, level 2일 때 15
            assertThat(testGuild.getMaxMembers()).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("경험치 조회 테스트")
    class GetExperienceTest {

        @Test
        @DisplayName("길드 경험치 정보를 조회한다")
        void getGuildExperience_success() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(levelConfigRepository.findByLevel(1)).thenReturn(Optional.of(testLevelConfig));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(levelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(LevelConfig.builder().level(1).requiredExp(100).build());

            // when
            GuildExperienceResponse response = guildExperienceService.getGuildExperience(1L);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getGuildId()).isEqualTo(1L);
            assertThat(response.getCurrentLevel()).isEqualTo(1);
            assertThat(response.getRequiredExpForNextLevel()).isEqualTo(500);
        }

        @Test
        @DisplayName("존재하지 않는 길드 경험치 조회 시 예외 발생")
        void getGuildExperience_failWhenGuildNotFound() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildExperienceService.getGuildExperience(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("길드를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("경험치 히스토리 조회 테스트")
    class GetHistoryTest {

        @Test
        @DisplayName("경험치 히스토리를 조회한다")
        void getExperienceHistory_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            GuildExperienceHistory history = GuildExperienceHistory.builder()
                .guild(testGuild)
                .sourceType(GuildExpSourceType.GUILD_MISSION_EXECUTION)
                .expAmount(100)
                .description("테스트")
                .build();
            Page<GuildExperienceHistory> historyPage = new PageImpl<>(List.of(history), pageable, 1);

            when(historyRepository.findByGuildIdOrderByCreatedAtDesc(1L, pageable)).thenReturn(historyPage);

            // when
            Page<GuildExperienceHistory> result = guildExperienceService.getExperienceHistory(1L, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getExpAmount()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("경험치 차감 테스트")
    class SubtractExperienceTest {

        @Test
        @DisplayName("정상적으로 경험치를 차감한다")
        void subtractExperience_success() {
            // given
            testGuild.addExperience(500);

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(levelConfigRepository.findByLevel(any())).thenReturn(Optional.of(testLevelConfig));
            when(historyRepository.save(any(GuildExperienceHistory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(levelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(LevelConfig.builder().level(1).requiredExp(100).build());

            // when
            GuildExperienceResponse response = guildExperienceService.subtractExperience(
                1L, 200, GuildExpSourceType.GUILD_MISSION_EXECUTION, 1L, testUserId, "보상 트랜잭션 취소"
            );

            // then
            assertThat(response).isNotNull();
            assertThat(testGuild.getCurrentExp()).isEqualTo(300);
        }

        @Test
        @DisplayName("경험치 차감으로 레벨 다운 시 0 이하면 레벨1로 초기화된다")
        void subtractExperience_levelDownToMinimum() {
            // given
            testGuild.addExperience(100);

            GuildLevelConfig level2Config = GuildLevelConfig.builder()
                .level(2)
                .requiredExp(800)
                .cumulativeExp(500)
                .maxMembers(30)
                .title("성장하는 길드")
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(levelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(testLevelConfig, level2Config));
            when(levelConfigRepository.findByLevel(any())).thenReturn(Optional.of(testLevelConfig));
            when(historyRepository.save(any(GuildExperienceHistory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(levelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(LevelConfig.builder().level(1).requiredExp(100).build());

            // when - 현재 100인데 200 차감
            GuildExperienceResponse response = guildExperienceService.subtractExperience(
                1L, 200, GuildExpSourceType.GUILD_MISSION_EXECUTION, 1L, testUserId, "보상 취소"
            );

            // then
            assertThat(response).isNotNull();
            assertThat(testGuild.getCurrentLevel()).isEqualTo(1);
            assertThat(testGuild.getCurrentExp()).isEqualTo(0);
            assertThat(testGuild.getTotalExp()).isEqualTo(0);
        }

        @Test
        @DisplayName("경험치 차감으로 레벨 다운이 발생하고 누적 경험치 기반으로 레벨이 재계산된다")
        void subtractExperience_levelDownWithCumulativeExp() {
            // given - 레벨2, totalExp 600, currentExp 100 (레벨2에서 100 exp 진행)
            setGuildTotalExp(testGuild, 600);
            setGuildCurrentExp(testGuild, 100);
            setGuildLevel(testGuild, 2);

            GuildLevelConfig level2Config = GuildLevelConfig.builder()
                .level(2)
                .requiredExp(800)
                .cumulativeExp(500)
                .maxMembers(30)
                .title("성장하는 길드")
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(levelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(testLevelConfig, level2Config));
            when(levelConfigRepository.findByLevel(any())).thenReturn(Optional.of(testLevelConfig));
            when(historyRepository.save(any(GuildExperienceHistory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(levelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(LevelConfig.builder().level(1).requiredExp(100).build());

            // when - currentExp 100에서 200 차감하면 -100이 되어 processLevelDown 호출
            GuildExperienceResponse response = guildExperienceService.subtractExperience(
                1L, 200, GuildExpSourceType.GUILD_MISSION_EXECUTION, 1L, testUserId, "보상 취소"
            );

            // then - totalExp = 400, 레벨1 (cumulativeExp 0 기준으로 계산)
            assertThat(response).isNotNull();
            assertThat(testGuild.getCurrentLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("존재하지 않는 길드에서 경험치 차감 시 예외 발생")
        void subtractExperience_failWhenGuildNotFound() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildExperienceService.subtractExperience(
                999L, 100, GuildExpSourceType.GUILD_MISSION_EXECUTION, 1L, testUserId, "보상 취소"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("길드를 찾을 수 없습니다");
        }
    }

    private void setGuildLevel(Guild guild, int level) {
        try {
            java.lang.reflect.Field levelField = Guild.class.getDeclaredField("currentLevel");
            levelField.setAccessible(true);
            levelField.set(guild, level);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setGuildCurrentExp(Guild guild, int exp) {
        try {
            java.lang.reflect.Field expField = Guild.class.getDeclaredField("currentExp");
            expField.setAccessible(true);
            expField.set(guild, exp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setGuildTotalExp(Guild guild, int exp) {
        try {
            java.lang.reflect.Field expField = Guild.class.getDeclaredField("totalExp");
            expField.setAccessible(true);
            expField.set(guild, exp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("레벨 설정 테스트")
    class LevelConfigTest {

        @Test
        @DisplayName("모든 레벨 설정을 조회한다")
        void getAllLevelConfigs_success() {
            // given
            when(levelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(testLevelConfig));

            // when
            List<GuildLevelConfig> configs = guildExperienceService.getAllLevelConfigs();

            // then
            assertThat(configs).hasSize(1);
            assertThat(configs.get(0).getLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("레벨 설정을 생성하거나 업데이트한다")
        void createOrUpdateLevelConfig_success() {
            // given
            when(levelConfigRepository.findByLevel(5)).thenReturn(Optional.empty());
            when(levelConfigRepository.save(any(GuildLevelConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            GuildLevelConfig config = guildExperienceService.createOrUpdateLevelConfig(
                5, 2000, 5000, 60, "상급 길드", "상급 길드입니다"
            );

            // then
            assertThat(config).isNotNull();
            assertThat(config.getLevel()).isEqualTo(5);
            assertThat(config.getRequiredExp()).isEqualTo(2000);
            verify(levelConfigRepository).save(any(GuildLevelConfig.class));
        }
    }
}
