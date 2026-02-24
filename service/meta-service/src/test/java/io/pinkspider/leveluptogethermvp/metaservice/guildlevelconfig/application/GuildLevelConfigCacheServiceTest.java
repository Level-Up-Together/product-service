package io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.dto.GuildLevelConfigRequest;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.dto.GuildLevelConfigResponse;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.entity.GuildLevelConfig;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.infrastructure.GuildLevelConfigRepository;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.application.UserLevelConfigCacheService;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.entity.UserLevelConfig;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
class GuildLevelConfigCacheServiceTest {

    @Mock
    private GuildLevelConfigRepository guildLevelConfigRepository;

    @Mock
    private UserLevelConfigCacheService userLevelConfigCacheService;

    @InjectMocks
    private GuildLevelConfigCacheService guildLevelConfigCacheService;

    private GuildLevelConfig createGuildLevelConfig(Long id, int level, int requiredExp,
                                                     Integer cumulativeExp, int maxMembers) {
        GuildLevelConfig config = GuildLevelConfig.builder()
            .level(level)
            .requiredExp(requiredExp)
            .cumulativeExp(cumulativeExp)
            .maxMembers(maxMembers)
            .title("레벨 " + level)
            .description("레벨 " + level + " 설명")
            .build();
        setId(config, id);
        return config;
    }

    private UserLevelConfig createUserLevelConfig(int level, int requiredExp) {
        UserLevelConfig config = UserLevelConfig.builder()
            .level(level)
            .requiredExp(requiredExp)
            .cumulativeExp(requiredExp)
            .build();
        setId(config, (long) level);
        return config;
    }

    @Nested
    @DisplayName("getAllLevelConfigs 테스트")
    class GetAllLevelConfigsTest {

        @Test
        @DisplayName("전체 길드 레벨 설정을 레벨 오름차순으로 조회한다")
        void getAllLevelConfigs_success() {
            // given
            GuildLevelConfig level1 = createGuildLevelConfig(1L, 1, 1000, 1000, 10);
            GuildLevelConfig level2 = createGuildLevelConfig(2L, 2, 2000, 3000, 15);
            when(guildLevelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(level1, level2));

            // when
            List<GuildLevelConfig> result = guildLevelConfigCacheService.getAllLevelConfigs();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getLevel()).isEqualTo(1);
            assertThat(result.get(1).getLevel()).isEqualTo(2);
            verify(guildLevelConfigRepository).findAllByOrderByLevelAsc();
        }

        @Test
        @DisplayName("길드 레벨 설정이 없으면 빈 목록을 반환한다")
        void getAllLevelConfigs_empty() {
            // given
            when(guildLevelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(Collections.emptyList());

            // when
            List<GuildLevelConfig> result = guildLevelConfigCacheService.getAllLevelConfigs();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getLevelConfigByLevel 테스트")
    class GetLevelConfigByLevelTest {

        @Test
        @DisplayName("특정 레벨의 길드 레벨 설정을 조회한다")
        void getLevelConfigByLevel_success() {
            // given
            GuildLevelConfig config = createGuildLevelConfig(1L, 1, 1000, 1000, 10);
            when(guildLevelConfigRepository.findByLevel(1)).thenReturn(Optional.of(config));

            // when
            GuildLevelConfig result = guildLevelConfigCacheService.getLevelConfigByLevel(1);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getLevel()).isEqualTo(1);
            assertThat(result.getMaxMembers()).isEqualTo(10);
        }

        @Test
        @DisplayName("존재하지 않는 레벨 조회 시 null을 반환한다")
        void getLevelConfigByLevel_notFound_returnsNull() {
            // given
            when(guildLevelConfigRepository.findByLevel(999)).thenReturn(Optional.empty());

            // when
            GuildLevelConfig result = guildLevelConfigCacheService.getLevelConfigByLevel(999);

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getMaxLevel 테스트")
    class GetMaxLevelTest {

        @Test
        @DisplayName("최대 길드 레벨을 반환한다")
        void getMaxLevel_success() {
            // given
            GuildLevelConfig level1 = createGuildLevelConfig(1L, 1, 1000, 1000, 10);
            GuildLevelConfig level2 = createGuildLevelConfig(2L, 5, 5000, 15000, 50);
            when(guildLevelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(level1, level2));

            // when
            Integer result = guildLevelConfigCacheService.getMaxLevel();

            // then
            assertThat(result).isEqualTo(5);
        }

        @Test
        @DisplayName("길드 레벨 설정이 없으면 0을 반환한다")
        void getMaxLevel_noConfigs_returnsZero() {
            // given
            when(guildLevelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(Collections.emptyList());

            // when
            Integer result = guildLevelConfigCacheService.getMaxLevel();

            // then
            assertThat(result).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("createOrUpdateLevelConfig 테스트")
    class CreateOrUpdateLevelConfigTest {

        @Test
        @DisplayName("존재하지 않는 레벨을 신규 생성한다")
        void createOrUpdateLevelConfig_create() {
            // given
            GuildLevelConfig saved = createGuildLevelConfig(1L, 2, 2000, 3000, 15);
            when(guildLevelConfigRepository.findByLevel(2)).thenReturn(Optional.empty());
            when(guildLevelConfigRepository.save(any(GuildLevelConfig.class))).thenReturn(saved);

            // when
            GuildLevelConfig result = guildLevelConfigCacheService.createOrUpdateLevelConfig(
                2, 2000, 3000, 15, "레벨 2", "레벨 2 설명");

            // then
            assertThat(result).isNotNull();
            assertThat(result.getLevel()).isEqualTo(2);
            verify(guildLevelConfigRepository).save(any(GuildLevelConfig.class));
        }

        @Test
        @DisplayName("이미 존재하는 레벨을 수정한다")
        void createOrUpdateLevelConfig_update() {
            // given
            GuildLevelConfig existing = createGuildLevelConfig(1L, 2, 1500, 2500, 12);
            GuildLevelConfig saved = createGuildLevelConfig(1L, 2, 2000, 3000, 15);
            when(guildLevelConfigRepository.findByLevel(2)).thenReturn(Optional.of(existing));
            when(guildLevelConfigRepository.save(existing)).thenReturn(saved);

            // when
            GuildLevelConfig result = guildLevelConfigCacheService.createOrUpdateLevelConfig(
                2, 2000, 3000, 15, "수정된 레벨 2", "수정된 설명");

            // then
            assertThat(existing.getRequiredExp()).isEqualTo(2000);
            assertThat(existing.getMaxMembers()).isEqualTo(15);
            verify(guildLevelConfigRepository).save(existing);
        }
    }

    @Nested
    @DisplayName("getAllLevelConfigResponses 테스트")
    class GetAllLevelConfigResponsesTest {

        @Test
        @DisplayName("모든 길드 레벨 설정 Response 목록을 조회한다")
        void getAllLevelConfigResponses_success() {
            // given
            GuildLevelConfig config1 = createGuildLevelConfig(1L, 1, 1000, 1000, 10);
            GuildLevelConfig config2 = createGuildLevelConfig(2L, 2, 2000, 3000, 15);
            when(guildLevelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(config1, config2));

            // when
            List<GuildLevelConfigResponse> result = guildLevelConfigCacheService.getAllLevelConfigResponses();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getLevel()).isEqualTo(1);
            assertThat(result.get(1).getLevel()).isEqualTo(2);
        }

        @Test
        @DisplayName("길드 레벨 설정이 없으면 빈 목록을 반환한다")
        void getAllLevelConfigResponses_empty() {
            // given
            when(guildLevelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(Collections.emptyList());

            // when
            List<GuildLevelConfigResponse> result = guildLevelConfigCacheService.getAllLevelConfigResponses();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("searchLevelConfigs 테스트")
    class SearchLevelConfigsTest {

        @Test
        @DisplayName("키워드로 길드 레벨 설정을 페이징 조회한다")
        void searchLevelConfigs_success() {
            // given
            GuildLevelConfig config = createGuildLevelConfig(1L, 1, 1000, 1000, 10);
            Pageable pageable = PageRequest.of(0, 10);
            Page<GuildLevelConfig> page = new PageImpl<>(List.of(config), pageable, 1);
            when(guildLevelConfigRepository.searchByKeyword("레벨", pageable)).thenReturn(page);

            // when
            var result = guildLevelConfigCacheService.searchLevelConfigs("레벨", pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("키워드 없이 전체 길드 레벨 설정을 페이징 조회한다")
        void searchLevelConfigs_noKeyword() {
            // given
            GuildLevelConfig config1 = createGuildLevelConfig(1L, 1, 1000, 1000, 10);
            GuildLevelConfig config2 = createGuildLevelConfig(2L, 2, 2000, 3000, 15);
            Pageable pageable = PageRequest.of(0, 10);
            Page<GuildLevelConfig> page = new PageImpl<>(List.of(config1, config2), pageable, 2);
            when(guildLevelConfigRepository.searchByKeyword(null, pageable)).thenReturn(page);

            // when
            var result = guildLevelConfigCacheService.searchLevelConfigs(null, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getLevelConfigById 테스트")
    class GetLevelConfigByIdTest {

        @Test
        @DisplayName("ID로 길드 레벨 설정을 조회한다")
        void getLevelConfigById_success() {
            // given
            GuildLevelConfig config = createGuildLevelConfig(1L, 1, 1000, 1000, 10);
            when(guildLevelConfigRepository.findById(1L)).thenReturn(Optional.of(config));

            // when
            GuildLevelConfigResponse result = guildLevelConfigCacheService.getLevelConfigById(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("존재하지 않는 ID 조회 시 CustomException을 던진다")
        void getLevelConfigById_notFound_throwsException() {
            // given
            when(guildLevelConfigRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildLevelConfigCacheService.getLevelConfigById(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("길드 레벨 설정을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("getLevelConfigResponseByLevel 테스트")
    class GetLevelConfigResponseByLevelTest {

        @Test
        @DisplayName("레벨 번호로 길드 레벨 설정 Response를 조회한다")
        void getLevelConfigResponseByLevel_success() {
            // given
            GuildLevelConfig config = createGuildLevelConfig(2L, 2, 2000, 3000, 15);
            when(guildLevelConfigRepository.findByLevel(2)).thenReturn(Optional.of(config));

            // when
            GuildLevelConfigResponse result = guildLevelConfigCacheService.getLevelConfigResponseByLevel(2);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getLevel()).isEqualTo(2);
            assertThat(result.getMaxMembers()).isEqualTo(15);
        }

        @Test
        @DisplayName("존재하지 않는 레벨 번호 조회 시 CustomException을 던진다")
        void getLevelConfigResponseByLevel_notFound_throwsException() {
            // given
            when(guildLevelConfigRepository.findByLevel(999)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildLevelConfigCacheService.getLevelConfigResponseByLevel(999))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 길드 레벨 설정을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("createLevelConfig 테스트")
    class CreateLevelConfigTest {

        @Test
        @DisplayName("길드 레벨 설정을 생성하고 requiredExp와 cumulativeExp를 자동 계산한다")
        void createLevelConfig_success() {
            // given
            GuildLevelConfigRequest request = GuildLevelConfigRequest.builder()
                .level(1)
                .maxMembers(10)
                .title("초급 길드")
                .description("초급 길드 설명")
                .build();

            UserLevelConfig userLevelConfig = createUserLevelConfig(1, 100);
            GuildLevelConfig saved = createGuildLevelConfig(1L, 1, 1000, 1000, 10);

            when(guildLevelConfigRepository.existsByLevel(1)).thenReturn(false);
            when(userLevelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(userLevelConfig);
            when(guildLevelConfigRepository.save(any(GuildLevelConfig.class))).thenReturn(saved);

            // when
            GuildLevelConfigResponse result = guildLevelConfigCacheService.createLevelConfig(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getLevel()).isEqualTo(1);
            verify(guildLevelConfigRepository).save(any(GuildLevelConfig.class));
        }

        @Test
        @DisplayName("레벨 2 이상 생성 시 이전 레벨 누적 경험치를 반영한다")
        void createLevelConfig_level2_usesPrevCumulativeExp() {
            // given
            GuildLevelConfigRequest request = GuildLevelConfigRequest.builder()
                .level(2)
                .maxMembers(15)
                .title("중급 길드")
                .description("중급 길드 설명")
                .build();

            UserLevelConfig userLevelConfig = createUserLevelConfig(2, 200);
            GuildLevelConfig prevLevelConfig = createGuildLevelConfig(1L, 1, 1000, 1000, 10);
            GuildLevelConfig saved = createGuildLevelConfig(2L, 2, 3000, 4000, 15);

            when(guildLevelConfigRepository.existsByLevel(2)).thenReturn(false);
            when(userLevelConfigCacheService.getLevelConfigByLevel(2)).thenReturn(userLevelConfig);
            when(guildLevelConfigRepository.findByLevel(1)).thenReturn(Optional.of(prevLevelConfig));
            when(guildLevelConfigRepository.save(any(GuildLevelConfig.class))).thenReturn(saved);

            // when
            GuildLevelConfigResponse result = guildLevelConfigCacheService.createLevelConfig(request);

            // then
            assertThat(result).isNotNull();
            verify(guildLevelConfigRepository).save(any(GuildLevelConfig.class));
        }

        @Test
        @DisplayName("유저 레벨 설정이 없으면 기본값 500을 사용하여 requiredExp를 계산한다")
        void createLevelConfig_noUserLevelConfig_usesDefaultExp() {
            // given
            GuildLevelConfigRequest request = GuildLevelConfigRequest.builder()
                .level(1)
                .maxMembers(10)
                .title("초급 길드")
                .description("초급 길드 설명")
                .build();

            GuildLevelConfig saved = createGuildLevelConfig(1L, 1, 5000, 5000, 10);

            when(guildLevelConfigRepository.existsByLevel(1)).thenReturn(false);
            when(userLevelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(null);
            when(guildLevelConfigRepository.save(any(GuildLevelConfig.class))).thenReturn(saved);

            // when
            GuildLevelConfigResponse result = guildLevelConfigCacheService.createLevelConfig(request);

            // then
            assertThat(result).isNotNull();
            verify(guildLevelConfigRepository).save(any(GuildLevelConfig.class));
        }

        @Test
        @DisplayName("이미 존재하는 레벨로 생성 시 CustomException을 던진다")
        void createLevelConfig_duplicateLevel_throwsException() {
            // given
            GuildLevelConfigRequest request = GuildLevelConfigRequest.builder()
                .level(1)
                .maxMembers(10)
                .build();

            when(guildLevelConfigRepository.existsByLevel(1)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildLevelConfigCacheService.createLevelConfig(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("이미 존재하는 길드 레벨입니다");

            verify(guildLevelConfigRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateLevelConfig 테스트")
    class UpdateLevelConfigTest {

        @Test
        @DisplayName("길드 레벨 설정을 수정한다")
        void updateLevelConfig_success() {
            // given
            GuildLevelConfig existing = createGuildLevelConfig(1L, 1, 1000, 1000, 10);
            GuildLevelConfigRequest request = GuildLevelConfigRequest.builder()
                .level(1)
                .maxMembers(12)
                .title("수정된 레벨 1")
                .description("수정된 설명")
                .build();

            UserLevelConfig userLevelConfig = createUserLevelConfig(1, 100);
            GuildLevelConfig saved = createGuildLevelConfig(1L, 1, 1200, 1200, 12);

            when(guildLevelConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(userLevelConfigCacheService.getLevelConfigByLevel(1)).thenReturn(userLevelConfig);
            when(guildLevelConfigRepository.save(existing)).thenReturn(saved);

            // when
            GuildLevelConfigResponse result = guildLevelConfigCacheService.updateLevelConfig(1L, request);

            // then
            assertThat(result).isNotNull();
            verify(guildLevelConfigRepository).save(existing);
        }

        @Test
        @DisplayName("레벨 번호 변경 시 중복 레벨이 존재하면 CustomException을 던진다")
        void updateLevelConfig_duplicateLevel_throwsException() {
            // given
            GuildLevelConfig existing = createGuildLevelConfig(1L, 1, 1000, 1000, 10);
            GuildLevelConfigRequest request = GuildLevelConfigRequest.builder()
                .level(2)
                .maxMembers(15)
                .build();

            when(guildLevelConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(guildLevelConfigRepository.existsByLevel(2)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> guildLevelConfigCacheService.updateLevelConfig(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("이미 존재하는 길드 레벨입니다");

            verify(guildLevelConfigRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 ID 수정 시 CustomException을 던진다")
        void updateLevelConfig_notFound_throwsException() {
            // given
            GuildLevelConfigRequest request = GuildLevelConfigRequest.builder()
                .level(1)
                .maxMembers(10)
                .build();

            when(guildLevelConfigRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildLevelConfigCacheService.updateLevelConfig(999L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("길드 레벨 설정을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("deleteLevelConfig 테스트")
    class DeleteLevelConfigTest {

        @Test
        @DisplayName("길드 레벨 설정을 삭제한다")
        void deleteLevelConfig_success() {
            // given
            when(guildLevelConfigRepository.existsById(1L)).thenReturn(true);

            // when
            guildLevelConfigCacheService.deleteLevelConfig(1L);

            // then
            verify(guildLevelConfigRepository).deleteById(1L);
        }

        @Test
        @DisplayName("존재하지 않는 ID 삭제 시 CustomException을 던진다")
        void deleteLevelConfig_notFound_throwsException() {
            // given
            when(guildLevelConfigRepository.existsById(999L)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> guildLevelConfigCacheService.deleteLevelConfig(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("길드 레벨 설정을 찾을 수 없습니다");

            verify(guildLevelConfigRepository, never()).deleteById(any());
        }
    }
}
