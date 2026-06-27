package io.pinkspider.leveluptogethermvp.guildservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.GuildExpSourceType;
import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildExperienceResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildExperienceHistory;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildExperienceHistoryRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.application.GuildLevelConfigCacheService;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.entity.GuildLevelConfig;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.application.UserLevelConfigCacheService;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class GuildExperienceServiceTest {

    @Mock private GuildRepository guildRepository;

    @Mock private GuildLevelConfigCacheService guildLevelConfigCacheService;

    @Mock private GuildExperienceHistoryRepository historyRepository;

    // calculateGuildRequiredExp(레거시) 가 의존하므로 @InjectMocks 주입을 위해 유지.
    @Mock private GuildMemberRepository guildMemberRepository;

    @Mock private UserLevelConfigCacheService userLevelConfigCacheService;

    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private GuildExperienceService guildExperienceService;

    private Guild testGuild;
    private GuildLevelConfig level1Config;
    private GuildLevelConfig level2Config;
    private String testUserId;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-id";

        testGuild =
                Guild.builder()
                        .name("테스트 길드")
                        .description("테스트 길드 설명")
                        .visibility(GuildVisibility.PUBLIC)
                        .masterId(testUserId)
                        .maxMembers(50)
                        .categoryId(1L)
                        .build();
        setId(testGuild, 1L);

        // 어드민 설정 (QA-204 재현): L1 누적 0, L2 누적 6020
        level1Config =
                GuildLevelConfig.builder()
                        .level(1)
                        .requiredExp(6020)
                        .cumulativeExp(0)
                        .maxMembers(20)
                        .title("신생 길드")
                        .build();
        level2Config =
                GuildLevelConfig.builder()
                        .level(2)
                        .requiredExp(8000)
                        .cumulativeExp(6020)
                        .maxMembers(30)
                        .title("성장 길드")
                        .build();
    }

    @Nested
    @DisplayName("경험치 추가 / 레벨 계산")
    class AddExperienceTest {

        @Test
        @DisplayName("정상적으로 경험치를 추가한다")
        void addExperience_success() {
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(List.of(level1Config, level2Config));
            when(guildLevelConfigCacheService.getLevelConfigByLevel(anyInt())).thenReturn(level1Config);
            when(historyRepository.save(any(GuildExperienceHistory.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            GuildExperienceResponse response =
                    guildExperienceService.addExperience(
                            1L, 100, GuildExpSourceType.GUILD_MISSION_EXECUTION, 1L, testUserId, "미션 완료 보상");

            assertThat(response).isNotNull();
            assertThat(response.getGuildId()).isEqualTo(1L);
            assertThat(response.getCurrentExp()).isEqualTo(100);
            verify(historyRepository).save(any(GuildExperienceHistory.class));
        }

        @Test
        @DisplayName("QA-204: 누적 92 는 레벨2(누적 6020)에 못 미쳐 레벨 1로 계산된다")
        void addExperience_92ExpStaysLevel1() {
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(List.of(level1Config, level2Config));
            when(guildLevelConfigCacheService.getLevelConfigByLevel(anyInt())).thenReturn(level1Config);
            when(historyRepository.save(any(GuildExperienceHistory.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            guildExperienceService.addExperience(
                    1L, 92, GuildExpSourceType.GUILD_MISSION_EXECUTION, 1L, testUserId, "미션 완료 보상");

            assertThat(testGuild.getCurrentLevel()).isEqualTo(1);
            assertThat(testGuild.getCurrentExp()).isEqualTo(92);
        }

        @Test
        @DisplayName("QA-204: 누적 6020 도달 시 레벨 2로 올라가고 maxMembers 가 갱신된다")
        void addExperience_reaching6020LevelsUpToLevel2() {
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(List.of(level1Config, level2Config));
            when(guildLevelConfigCacheService.getLevelConfigByLevel(2)).thenReturn(level2Config);
            when(historyRepository.save(any(GuildExperienceHistory.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            guildExperienceService.addExperience(
                    1L, 6020, GuildExpSourceType.GUILD_MISSION_EXECUTION, 1L, testUserId, "미션 완료 보상");

            assertThat(testGuild.getCurrentLevel()).isEqualTo(2);
            assertThat(testGuild.getCurrentExp()).isEqualTo(0); // 6020 - 6020
            assertThat(testGuild.getMaxMembers()).isEqualTo(30); // L2 config maxMembers
        }

        @Test
        @DisplayName("상위 레벨 설정이 없으면 레벨이 더 오르지 않는다")
        void addExperience_noHigherConfigStaysLevel1() {
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            // L1 설정만 존재
            when(guildLevelConfigCacheService.getAllLevelConfigs()).thenReturn(List.of(level1Config));
            when(guildLevelConfigCacheService.getLevelConfigByLevel(anyInt())).thenReturn(level1Config);
            when(historyRepository.save(any(GuildExperienceHistory.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            guildExperienceService.addExperience(
                    1L, 999999, GuildExpSourceType.GUILD_MISSION_EXECUTION, 1L, testUserId, "미션 완료 보상");

            assertThat(testGuild.getCurrentLevel()).isEqualTo(1);
            assertThat(testGuild.getMaxMembers()).isEqualTo(20); // L1 config maxMembers
        }

        @Test
        @DisplayName("존재하지 않는 길드에 경험치 추가 시 예외 발생")
        void addExperience_failWhenGuildNotFound() {
            when(guildRepository.findByIdAndIsActiveTrue(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    guildExperienceService.addExperience(
                                            999L,
                                            100,
                                            GuildExpSourceType.GUILD_MISSION_EXECUTION,
                                            1L,
                                            testUserId,
                                            "미션 완료 보상"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("길드를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("경험치 조회 테스트")
    class GetExperienceTest {

        @Test
        @DisplayName("길드 경험치 정보를 조회한다 (다음 레벨 필요 경험치는 어드민 required_exp)")
        void getGuildExperience_success() {
            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildLevelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(level1Config);

            GuildExperienceResponse response = guildExperienceService.getGuildExperience(1L);

            assertThat(response).isNotNull();
            assertThat(response.getGuildId()).isEqualTo(1L);
            assertThat(response.getCurrentLevel()).isEqualTo(1);
            assertThat(response.getRequiredExpForNextLevel()).isEqualTo(6020);
        }

        @Test
        @DisplayName("존재하지 않는 길드 경험치 조회 시 예외 발생")
        void getGuildExperience_failWhenGuildNotFound() {
            when(guildRepository.findByIdAndIsActiveTrue(999L)).thenReturn(Optional.empty());

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
            Pageable pageable = PageRequest.of(0, 10);
            GuildExperienceHistory history =
                    GuildExperienceHistory.builder()
                            .guild(testGuild)
                            .sourceType(GuildExpSourceType.GUILD_MISSION_EXECUTION)
                            .expAmount(100)
                            .description("테스트")
                            .build();
            Page<GuildExperienceHistory> historyPage = new PageImpl<>(List.of(history), pageable, 1);

            when(historyRepository.findByGuildIdOrderByCreatedAtDesc(1L, pageable))
                    .thenReturn(historyPage);

            Page<GuildExperienceHistory> result =
                    guildExperienceService.getExperienceHistory(1L, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getExpAmount()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("경험치 차감 테스트")
    class SubtractExperienceTest {

        @Test
        @DisplayName("정상적으로 경험치를 차감한다 (레벨 다운 없음)")
        void subtractExperience_success() {
            testGuild.addExperience(500);

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildLevelConfigCacheService.getLevelConfigByLevel(anyInt())).thenReturn(level1Config);
            when(historyRepository.save(any(GuildExperienceHistory.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            GuildExperienceResponse response =
                    guildExperienceService.subtractExperience(
                            1L, 200, GuildExpSourceType.GUILD_MISSION_EXECUTION, 1L, testUserId, "보상 트랜잭션 취소");

            assertThat(response).isNotNull();
            assertThat(testGuild.getCurrentExp()).isEqualTo(300);
        }

        @Test
        @DisplayName("경험치 차감으로 0 이하가 되면 레벨1로 초기화된다")
        void subtractExperience_levelDownToMinimum() {
            testGuild.addExperience(100);

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(List.of(level1Config, level2Config));
            when(guildLevelConfigCacheService.getLevelConfigByLevel(anyInt())).thenReturn(level1Config);
            when(historyRepository.save(any(GuildExperienceHistory.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            GuildExperienceResponse response =
                    guildExperienceService.subtractExperience(
                            1L, 200, GuildExpSourceType.GUILD_MISSION_EXECUTION, 1L, testUserId, "보상 취소");

            assertThat(response).isNotNull();
            assertThat(testGuild.getCurrentLevel()).isEqualTo(1);
            assertThat(testGuild.getCurrentExp()).isEqualTo(0);
            assertThat(testGuild.getTotalExp()).isEqualTo(0);
        }

        @Test
        @DisplayName("경험치 차감 시 누적 경험치 기반으로 레벨이 재계산된다")
        void subtractExperience_levelDownWithCumulativeExp() {
            // 레벨2(강제 세팅), totalExp 6100, currentExp 80
            TestReflectionUtils.setField(testGuild, "totalExp", 6100);
            TestReflectionUtils.setField(testGuild, "currentExp", 80);
            TestReflectionUtils.setField(testGuild, "currentLevel", 2);

            when(guildRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testGuild));
            when(guildLevelConfigCacheService.getAllLevelConfigs())
                    .thenReturn(List.of(level1Config, level2Config));
            when(guildLevelConfigCacheService.getLevelConfigByLevel(anyInt())).thenReturn(level1Config);
            when(historyRepository.save(any(GuildExperienceHistory.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // 200 차감 → currentExp 80-200 = -120 → processLevelDown(totalExp 5900)
            GuildExperienceResponse response =
                    guildExperienceService.subtractExperience(
                            1L, 200, GuildExpSourceType.GUILD_MISSION_EXECUTION, 1L, testUserId, "보상 취소");

            // 5900 < 6020 → 레벨 1
            assertThat(response).isNotNull();
            assertThat(testGuild.getCurrentLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("존재하지 않는 길드에서 경험치 차감 시 예외 발생")
        void subtractExperience_failWhenGuildNotFound() {
            when(guildRepository.findByIdAndIsActiveTrue(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    guildExperienceService.subtractExperience(
                                            999L,
                                            100,
                                            GuildExpSourceType.GUILD_MISSION_EXECUTION,
                                            1L,
                                            testUserId,
                                            "보상 취소"))
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
            when(guildLevelConfigCacheService.getAllLevelConfigs()).thenReturn(List.of(level1Config));

            List<GuildLevelConfig> configs = guildExperienceService.getAllLevelConfigs();

            assertThat(configs).hasSize(1);
            assertThat(configs.get(0).getLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("레벨 설정을 생성하거나 업데이트한다")
        void createOrUpdateLevelConfig_success() {
            GuildLevelConfig newConfig =
                    GuildLevelConfig.builder()
                            .level(5)
                            .requiredExp(2000)
                            .cumulativeExp(5000)
                            .maxMembers(60)
                            .title("상급 길드")
                            .description("상급 길드입니다")
                            .build();
            when(guildLevelConfigCacheService.createOrUpdateLevelConfig(
                            5, 2000, 5000, 60, "상급 길드", "상급 길드입니다"))
                    .thenReturn(newConfig);

            GuildLevelConfig config =
                    guildExperienceService.createOrUpdateLevelConfig(
                            5, 2000, 5000, 60, "상급 길드", "상급 길드입니다");

            assertThat(config).isNotNull();
            assertThat(config.getLevel()).isEqualTo(5);
            assertThat(config.getRequiredExp()).isEqualTo(2000);
            verify(guildLevelConfigCacheService)
                    .createOrUpdateLevelConfig(5, 2000, 5000, 60, "상급 길드", "상급 길드입니다");
        }
    }
}
