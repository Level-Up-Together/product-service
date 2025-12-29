package io.pinkspider.leveluptogethermvp.guildservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildHeadquartersInfoResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildHeadquartersValidationResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildHeadquartersConfig;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildHeadquartersConfigRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCategoryResponse;
import java.lang.reflect.Field;
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

@ExtendWith(MockitoExtension.class)
class GuildHeadquartersServiceTest {

    @Mock
    private GuildRepository guildRepository;

    @Mock
    private GuildHeadquartersConfigRepository configRepository;

    @Mock
    private MissionCategoryService missionCategoryService;

    @InjectMocks
    private GuildHeadquartersService guildHeadquartersService;

    private GuildHeadquartersConfig testConfig;
    private Guild testGuild1;
    private Guild testGuild2;

    @BeforeEach
    void setUp() {
        // 기본 설정: 100m 기본, 레벨 10당 20m 증가
        testConfig = GuildHeadquartersConfig.builder()
            .baseRadiusMeters(100)
            .radiusIncreasePerLevelTier(20)
            .levelTierSize(10)
            .isActive(true)
            .build();
        setId(testConfig, 1L);

        // 테스트 길드 1: 서울시청 (레벨 1)
        testGuild1 = Guild.builder()
            .name("테스트 길드 1")
            .description("테스트 길드 1 설명")
            .visibility(GuildVisibility.PUBLIC)
            .masterId("user-001")
            .maxMembers(50)
            .categoryId(1L)
            .baseLatitude(37.5665)
            .baseLongitude(126.978)
            .build();
        setGuildIdAndLevel(testGuild1, 1L, 1);

        // 테스트 길드 2: 서울시청 근처 50m (레벨 20)
        testGuild2 = Guild.builder()
            .name("테스트 길드 2")
            .description("테스트 길드 2 설명")
            .visibility(GuildVisibility.PUBLIC)
            .masterId("user-002")
            .maxMembers(50)
            .categoryId(2L)
            .baseLatitude(37.5669)  // 약 45m 북쪽
            .baseLongitude(126.978)
            .build();
        setGuildIdAndLevel(testGuild2, 2L, 20);
    }

    private void setId(GuildHeadquartersConfig config, Long id) {
        try {
            Field idField = GuildHeadquartersConfig.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(config, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setGuildIdAndLevel(Guild guild, Long id, int level) {
        try {
            Field idField = Guild.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(guild, id);

            Field levelField = Guild.class.getDeclaredField("currentLevel");
            levelField.setAccessible(true);
            levelField.set(guild, level);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("보호 반경 계산 테스트")
    class ProtectionRadiusCalculationTest {

        @Test
        @DisplayName("레벨 1 길드의 보호 반경은 100m이다")
        void calculateProtectionRadius_level1() {
            // given
            int level = 1;

            // when
            int radius = testConfig.calculateProtectionRadius(level);

            // then
            assertThat(radius).isEqualTo(100);
        }

        @Test
        @DisplayName("레벨 10 길드의 보호 반경은 120m이다")
        void calculateProtectionRadius_level10() {
            // given
            int level = 10;

            // when
            int radius = testConfig.calculateProtectionRadius(level);

            // then
            assertThat(radius).isEqualTo(120); // 100 + (10/10) * 20 = 120
        }

        @Test
        @DisplayName("레벨 25 길드의 보호 반경은 140m이다")
        void calculateProtectionRadius_level25() {
            // given
            int level = 25;

            // when
            int radius = testConfig.calculateProtectionRadius(level);

            // then
            assertThat(radius).isEqualTo(140); // 100 + (25/10) * 20 = 100 + 2*20 = 140
        }

        @Test
        @DisplayName("레벨 50 길드의 보호 반경은 200m이다")
        void calculateProtectionRadius_level50() {
            // given
            int level = 50;

            // when
            int radius = testConfig.calculateProtectionRadius(level);

            // then
            assertThat(radius).isEqualTo(200); // 100 + (50/10) * 20 = 100 + 5*20 = 200
        }
    }

    @Nested
    @DisplayName("거점 위치 검증 테스트")
    class ValidateHeadquartersLocationTest {

        @Test
        @DisplayName("다른 길드 거점과 충분히 멀리 떨어진 위치는 유효하다")
        void validateLocation_validWhenFarEnough() {
            // given
            when(configRepository.findActiveConfig()).thenReturn(Optional.of(testConfig));
            when(guildRepository.findAllWithHeadquartersExcluding(3L)).thenReturn(List.of(testGuild1));

            // 서울시청에서 약 200m 떨어진 위치 (남쪽)
            double latitude = 37.5647;
            double longitude = 126.978;

            // when
            GuildHeadquartersValidationResponse response =
                guildHeadquartersService.validateHeadquartersLocation(3L, latitude, longitude);

            // then
            assertThat(response.isValid()).isTrue();
            assertThat(response.getMessage()).contains("설정할 수 있습니다");
            assertThat(response.getNearbyGuilds()).isEmpty();
        }

        @Test
        @DisplayName("다른 길드 거점 보호 반경 내 위치는 유효하지 않다")
        void validateLocation_invalidWhenTooClose() {
            // given
            when(configRepository.findActiveConfig()).thenReturn(Optional.of(testConfig));
            when(guildRepository.findAllWithHeadquartersExcluding(3L)).thenReturn(List.of(testGuild1));

            // 서울시청에서 약 50m 떨어진 위치 (레벨1 보호반경 100m 내)
            double latitude = 37.5669;
            double longitude = 126.978;

            // when
            GuildHeadquartersValidationResponse response =
                guildHeadquartersService.validateHeadquartersLocation(3L, latitude, longitude);

            // then
            assertThat(response.isValid()).isFalse();
            assertThat(response.getMessage()).contains("설정할 수 없습니다");
            assertThat(response.getNearbyGuilds()).hasSize(1);
            assertThat(response.getNearbyGuilds().get(0).getGuildName()).isEqualTo("테스트 길드 1");
        }

        @Test
        @DisplayName("높은 레벨 길드의 넓은 보호 반경을 검증한다")
        void validateLocation_respectsHigherLevelRadius() {
            // given
            when(configRepository.findActiveConfig()).thenReturn(Optional.of(testConfig));
            when(guildRepository.findAllWithHeadquartersExcluding(3L)).thenReturn(List.of(testGuild2));

            // 레벨 20 길드: 보호 반경 140m (100 + 2*20)
            // testGuild2에서 약 130m 떨어진 위치 (보호 반경 내)
            double latitude = 37.5657;
            double longitude = 126.978;

            // when
            GuildHeadquartersValidationResponse response =
                guildHeadquartersService.validateHeadquartersLocation(3L, latitude, longitude);

            // then
            assertThat(response.isValid()).isFalse();
            assertThat(response.getNearbyGuilds().get(0).getProtectionRadiusMeters()).isEqualTo(140);
        }

        @Test
        @DisplayName("신규 길드 생성 시 검증 (guildId가 null)")
        void validateLocation_forNewGuild() {
            // given
            when(configRepository.findActiveConfig()).thenReturn(Optional.of(testConfig));
            when(guildRepository.findAllWithHeadquarters()).thenReturn(List.of(testGuild1));

            // 서울시청에서 약 50m 떨어진 위치
            double latitude = 37.5669;
            double longitude = 126.978;

            // when
            GuildHeadquartersValidationResponse response =
                guildHeadquartersService.validateHeadquartersLocation(null, latitude, longitude);

            // then
            assertThat(response.isValid()).isFalse();
        }

        @Test
        @DisplayName("설정이 없으면 기본값으로 검증한다")
        void validateLocation_usesDefaultConfigWhenNotFound() {
            // given
            when(configRepository.findActiveConfig()).thenReturn(Optional.empty());
            when(guildRepository.findAllWithHeadquartersExcluding(3L)).thenReturn(List.of(testGuild1));

            // 서울시청에서 약 200m 떨어진 위치
            double latitude = 37.5647;
            double longitude = 126.978;

            // when
            GuildHeadquartersValidationResponse response =
                guildHeadquartersService.validateHeadquartersLocation(3L, latitude, longitude);

            // then
            assertThat(response.isValid()).isTrue();
            assertThat(response.getBaseRadiusMeters()).isEqualTo(100); // 기본값
        }
    }

    @Nested
    @DisplayName("거점 정보 조회 테스트")
    class GetAllHeadquartersInfoTest {

        @Test
        @DisplayName("모든 거점 정보를 조회한다")
        void getAllHeadquartersInfo_success() {
            // given
            when(configRepository.findActiveConfig()).thenReturn(Optional.of(testConfig));
            when(guildRepository.findAllWithHeadquarters()).thenReturn(List.of(testGuild1, testGuild2));

            MissionCategoryResponse category1 = MissionCategoryResponse.builder()
                .id(1L).name("운동").icon("💪").build();
            MissionCategoryResponse category2 = MissionCategoryResponse.builder()
                .id(2L).name("공부").icon("📚").build();

            when(missionCategoryService.getCategory(1L)).thenReturn(category1);
            when(missionCategoryService.getCategory(2L)).thenReturn(category2);

            // when
            GuildHeadquartersInfoResponse response = guildHeadquartersService.getAllHeadquartersInfo();

            // then
            assertThat(response.getGuilds()).hasSize(2);
            assertThat(response.getConfig().getBaseRadiusMeters()).isEqualTo(100);
            assertThat(response.getConfig().getRadiusIncreasePerLevelTier()).isEqualTo(20);
            assertThat(response.getConfig().getLevelTierSize()).isEqualTo(10);

            // 첫 번째 길드 (레벨 1): 보호 반경 100m
            assertThat(response.getGuilds().get(0).getProtectionRadiusMeters()).isEqualTo(100);

            // 두 번째 길드 (레벨 20): 보호 반경 140m
            assertThat(response.getGuilds().get(1).getProtectionRadiusMeters()).isEqualTo(140);
        }
    }

    @Nested
    @DisplayName("거점 검증 예외 발생 테스트")
    class ValidateAndThrowTest {

        @Test
        @DisplayName("유효하지 않은 위치에서 예외가 발생한다")
        void validateAndThrowIfInvalid_throwsException() {
            // given
            when(configRepository.findActiveConfig()).thenReturn(Optional.of(testConfig));
            when(guildRepository.findAllWithHeadquartersExcluding(3L)).thenReturn(List.of(testGuild1));

            // 서울시청에서 약 50m 떨어진 위치
            double latitude = 37.5669;
            double longitude = 126.978;

            // when & then
            assertThatThrownBy(() ->
                guildHeadquartersService.validateAndThrowIfInvalid(3L, latitude, longitude))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("보호 구역 내입니다");
        }

        @Test
        @DisplayName("유효한 위치에서는 예외가 발생하지 않는다")
        void validateAndThrowIfInvalid_noException() {
            // given
            when(configRepository.findActiveConfig()).thenReturn(Optional.of(testConfig));
            when(guildRepository.findAllWithHeadquartersExcluding(3L)).thenReturn(List.of(testGuild1));

            // 서울시청에서 약 200m 떨어진 위치
            double latitude = 37.5647;
            double longitude = 126.978;

            // when & then (예외가 발생하지 않으면 성공)
            guildHeadquartersService.validateAndThrowIfInvalid(3L, latitude, longitude);
        }
    }
}
