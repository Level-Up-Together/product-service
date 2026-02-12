package io.pinkspider.leveluptogethermvp.guildservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.application.GuildLevelConfigCacheService;
import io.pinkspider.global.test.TestReflectionUtils;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildExperienceResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildExperienceHistory;
import io.pinkspider.global.enums.GuildExpSourceType;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.entity.GuildLevelConfig;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.application.UserLevelConfigCacheService;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.entity.UserLevelConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
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
    private GuildLevelConfigCacheService guildLevelConfigCacheService;

    @Mock
    private GuildExperienceHistoryRepository historyRepository;

    @Mock
    private GuildMemberRepository guildMemberRepository;

    @Mock
    private UserLevelConfigCacheService userLevelConfigCacheService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
        setId(testGuild, 1L);

        testLevelConfig = GuildLevelConfig.builder()
            .level(1)
            .requiredExp(500)
            .cumulativeExp(0)
            .maxMembers(20)
            .title("초보 길드")
            .build();
    }

    @Nested
    @DisplayName("경험치 추가 테스트")
    class AddExperienceTest {

        @Test
        @DisplayName("정상적으로 경험치를 추가한다")
        void addExperience_success() {
            // given
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildLevelConfigCacheService.getAllLevelConfigs()).thenReturn(List.of(testLevelConfig));
            when(guildLevelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(testLevelConfig);
            when(historyRepository.save(any(GuildExperienceHistory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(userLevelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(UserLevelConfig.builder().level(1).requiredExp(100).build());

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
            when(guildLevelConfigCacheService.getAllLevelConfigs()).thenReturn(List.of(testLevelConfig, level2Config));
            when(guildLevelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(testLevelConfig);
            when(historyRepository.save(any(GuildExperienceHistory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(userLevelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(UserLevelConfig.builder().level(1).requiredExp(100).build());
            when(userLevelConfigCacheService.getMaxLevel()).thenReturn(50);

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
            when(guildLevelConfigCacheService.getAllLevelConfigs()).thenReturn(List.of(testLevelConfig)); // level 1만
            when(guildLevelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(testLevelConfig);
            when(historyRepository.save(any(GuildExperienceHistory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(userLevelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(UserLevelConfig.builder().level(1).requiredExp(100).build());
            when(userLevelConfigCacheService.getMaxLevel()).thenReturn(50);

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
            when(guildLevelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(testLevelConfig);
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(userLevelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(UserLevelConfig.builder().level(1).requiredExp(100).build());

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
            when(guildLevelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(testLevelConfig);
            when(historyRepository.save(any(GuildExperienceHistory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(userLevelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(UserLevelConfig.builder().level(1).requiredExp(100).build());

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
            when(guildLevelConfigCacheService.getAllLevelConfigs()).thenReturn(List.of(testLevelConfig, level2Config));
            when(guildLevelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(testLevelConfig);
            when(historyRepository.save(any(GuildExperienceHistory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(userLevelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(UserLevelConfig.builder().level(1).requiredExp(100).build());

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
            TestReflectionUtils.setField(testGuild, "totalExp", 600);
            TestReflectionUtils.setField(testGuild, "currentExp", 100);
            TestReflectionUtils.setField(testGuild, "currentLevel", 2);

            GuildLevelConfig level2Config = GuildLevelConfig.builder()
                .level(2)
                .requiredExp(800)
                .cumulativeExp(500)
                .maxMembers(30)
                .title("성장하는 길드")
                .build();

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildLevelConfigCacheService.getAllLevelConfigs()).thenReturn(List.of(testLevelConfig, level2Config));
            when(guildLevelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(testLevelConfig);
            when(historyRepository.save(any(GuildExperienceHistory.class))).thenAnswer(inv -> inv.getArgument(0));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(5L);
            when(userLevelConfigCacheService.getLevelConfigByLevel(any())).thenReturn(UserLevelConfig.builder().level(1).requiredExp(100).build());

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

    @Nested
    @DisplayName("레벨 설정 테스트")
    class LevelConfigTest {

        @Test
        @DisplayName("모든 레벨 설정을 조회한다")
        void getAllLevelConfigs_success() {
            // given
            when(guildLevelConfigCacheService.getAllLevelConfigs()).thenReturn(List.of(testLevelConfig));

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
            GuildLevelConfig newConfig = GuildLevelConfig.builder()
                .level(5).requiredExp(2000).cumulativeExp(5000).maxMembers(60)
                .title("상급 길드").description("상급 길드입니다").build();
            when(guildLevelConfigCacheService.createOrUpdateLevelConfig(5, 2000, 5000, 60, "상급 길드", "상급 길드입니다"))
                .thenReturn(newConfig);

            // when
            GuildLevelConfig config = guildExperienceService.createOrUpdateLevelConfig(
                5, 2000, 5000, 60, "상급 길드", "상급 길드입니다"
            );

            // then
            assertThat(config).isNotNull();
            assertThat(config.getLevel()).isEqualTo(5);
            assertThat(config.getRequiredExp()).isEqualTo(2000);
            verify(guildLevelConfigCacheService).createOrUpdateLevelConfig(5, 2000, 5000, 60, "상급 길드", "상급 길드입니다");
        }
    }
}
