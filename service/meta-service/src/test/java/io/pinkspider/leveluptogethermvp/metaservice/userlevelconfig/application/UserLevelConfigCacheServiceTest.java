package io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.dto.UserLevelConfigRequest;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.dto.UserLevelConfigResponse;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.entity.UserLevelConfig;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.infrastructure.UserLevelConfigRepository;
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
class UserLevelConfigCacheServiceTest {

    @Mock
    private UserLevelConfigRepository userLevelConfigRepository;

    @InjectMocks
    private UserLevelConfigCacheService userLevelConfigCacheService;

    private UserLevelConfig createUserLevelConfig(Long id, int level, int requiredExp, Integer cumulativeExp) {
        UserLevelConfig config = UserLevelConfig.builder()
            .level(level)
            .requiredExp(requiredExp)
            .cumulativeExp(cumulativeExp)
            .build();
        setId(config, id);
        return config;
    }

    @Nested
    @DisplayName("getAllLevelConfigs 테스트")
    class GetAllLevelConfigsTest {

        @Test
        @DisplayName("전체 유저 레벨 설정을 레벨 오름차순으로 조회한다")
        void getAllLevelConfigs_success() {
            // given
            UserLevelConfig level1 = createUserLevelConfig(1L, 1, 100, 100);
            UserLevelConfig level2 = createUserLevelConfig(2L, 2, 200, 300);
            when(userLevelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(level1, level2));

            // when
            List<UserLevelConfig> result = userLevelConfigCacheService.getAllLevelConfigs();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getLevel()).isEqualTo(1);
            assertThat(result.get(1).getLevel()).isEqualTo(2);
            verify(userLevelConfigRepository).findAllByOrderByLevelAsc();
        }

        @Test
        @DisplayName("유저 레벨 설정이 없으면 빈 목록을 반환한다")
        void getAllLevelConfigs_empty() {
            // given
            when(userLevelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(Collections.emptyList());

            // when
            List<UserLevelConfig> result = userLevelConfigCacheService.getAllLevelConfigs();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getLevelConfigByLevel 테스트")
    class GetLevelConfigByLevelTest {

        @Test
        @DisplayName("특정 레벨의 유저 레벨 설정을 조회한다")
        void getLevelConfigByLevel_success() {
            // given
            UserLevelConfig config = createUserLevelConfig(1L, 1, 100, 100);
            when(userLevelConfigRepository.findByLevel(1)).thenReturn(Optional.of(config));

            // when
            UserLevelConfig result = userLevelConfigCacheService.getLevelConfigByLevel(1);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getLevel()).isEqualTo(1);
            assertThat(result.getRequiredExp()).isEqualTo(100);
        }

        @Test
        @DisplayName("존재하지 않는 레벨 조회 시 null을 반환한다")
        void getLevelConfigByLevel_notFound_returnsNull() {
            // given
            when(userLevelConfigRepository.findByLevel(999)).thenReturn(Optional.empty());

            // when
            UserLevelConfig result = userLevelConfigCacheService.getLevelConfigByLevel(999);

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getLevelByTotalExp 테스트")
    class GetLevelByTotalExpTest {

        @Test
        @DisplayName("총 경험치로 현재 레벨을 조회한다")
        void getLevelByTotalExp_success() {
            // given
            UserLevelConfig level1 = createUserLevelConfig(1L, 1, 100, 100);
            UserLevelConfig level2 = createUserLevelConfig(2L, 2, 200, 300);
            UserLevelConfig level3 = createUserLevelConfig(3L, 3, 300, 600);
            when(userLevelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(level1, level2, level3));

            // when
            var result = userLevelConfigCacheService.getLevelByTotalExp(350);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getLevel()).isEqualTo(2);
        }

        @Test
        @DisplayName("총 경험치가 모든 레벨 기준 미달이면 빈 Optional을 반환한다")
        void getLevelByTotalExp_belowAllLevels_returnsEmpty() {
            // given
            UserLevelConfig level1 = createUserLevelConfig(1L, 1, 100, 100);
            when(userLevelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(level1));

            // when
            var result = userLevelConfigCacheService.getLevelByTotalExp(50);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("총 경험치가 최고 레벨 이상이면 최고 레벨을 반환한다")
        void getLevelByTotalExp_aboveMaxLevel_returnsMaxLevel() {
            // given
            UserLevelConfig level1 = createUserLevelConfig(1L, 1, 100, 100);
            UserLevelConfig level2 = createUserLevelConfig(2L, 2, 200, 300);
            when(userLevelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(level1, level2));

            // when
            var result = userLevelConfigCacheService.getLevelByTotalExp(9999);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getLevel()).isEqualTo(2);
        }

        @Test
        @DisplayName("레벨 설정이 없으면 빈 Optional을 반환한다")
        void getLevelByTotalExp_noConfigs_returnsEmpty() {
            // given
            when(userLevelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(Collections.emptyList());

            // when
            var result = userLevelConfigCacheService.getLevelByTotalExp(500);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getMaxLevel 테스트")
    class GetMaxLevelTest {

        @Test
        @DisplayName("최대 레벨을 반환한다")
        void getMaxLevel_success() {
            // given
            UserLevelConfig level1 = createUserLevelConfig(1L, 1, 100, 100);
            UserLevelConfig level2 = createUserLevelConfig(2L, 2, 200, 300);
            UserLevelConfig level3 = createUserLevelConfig(3L, 10, 1000, 5000);
            when(userLevelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(level1, level2, level3));

            // when
            Integer result = userLevelConfigCacheService.getMaxLevel();

            // then
            assertThat(result).isEqualTo(10);
        }

        @Test
        @DisplayName("레벨 설정이 없으면 0을 반환한다")
        void getMaxLevel_noConfigs_returnsZero() {
            // given
            when(userLevelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(Collections.emptyList());

            // when
            Integer result = userLevelConfigCacheService.getMaxLevel();

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
            UserLevelConfig saved = createUserLevelConfig(1L, 5, 500, 2000);
            when(userLevelConfigRepository.findByLevel(5)).thenReturn(Optional.empty());
            when(userLevelConfigRepository.save(any(UserLevelConfig.class))).thenReturn(saved);

            // when
            UserLevelConfig result = userLevelConfigCacheService.createOrUpdateLevelConfig(5, 500, 2000);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getLevel()).isEqualTo(5);
            verify(userLevelConfigRepository).save(any(UserLevelConfig.class));
        }

        @Test
        @DisplayName("이미 존재하는 레벨을 수정한다")
        void createOrUpdateLevelConfig_update() {
            // given
            UserLevelConfig existing = createUserLevelConfig(1L, 3, 300, 600);
            UserLevelConfig saved = createUserLevelConfig(1L, 3, 400, 900);
            when(userLevelConfigRepository.findByLevel(3)).thenReturn(Optional.of(existing));
            when(userLevelConfigRepository.save(existing)).thenReturn(saved);

            // when
            UserLevelConfig result = userLevelConfigCacheService.createOrUpdateLevelConfig(3, 400, 900);

            // then
            assertThat(existing.getRequiredExp()).isEqualTo(400);
            assertThat(existing.getCumulativeExp()).isEqualTo(900);
            verify(userLevelConfigRepository).save(existing);
        }
    }

    @Nested
    @DisplayName("getAllLevelConfigResponses 테스트")
    class GetAllLevelConfigResponsesTest {

        @Test
        @DisplayName("모든 유저 레벨 설정 Response 목록을 조회한다")
        void getAllLevelConfigResponses_success() {
            // given
            UserLevelConfig config1 = createUserLevelConfig(1L, 1, 100, 100);
            UserLevelConfig config2 = createUserLevelConfig(2L, 2, 200, 300);
            when(userLevelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(List.of(config1, config2));

            // when
            List<UserLevelConfigResponse> result = userLevelConfigCacheService.getAllLevelConfigResponses();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getLevel()).isEqualTo(1);
            assertThat(result.get(1).getLevel()).isEqualTo(2);
        }

        @Test
        @DisplayName("레벨 설정이 없으면 빈 목록을 반환한다")
        void getAllLevelConfigResponses_empty() {
            // given
            when(userLevelConfigRepository.findAllByOrderByLevelAsc()).thenReturn(Collections.emptyList());

            // when
            List<UserLevelConfigResponse> result = userLevelConfigCacheService.getAllLevelConfigResponses();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("searchLevelConfigs 테스트")
    class SearchLevelConfigsTest {

        @Test
        @DisplayName("키워드로 유저 레벨 설정을 페이징 조회한다")
        void searchLevelConfigs_success() {
            // given
            UserLevelConfig config = createUserLevelConfig(1L, 1, 100, 100);
            Pageable pageable = PageRequest.of(0, 10);
            Page<UserLevelConfig> page = new PageImpl<>(List.of(config), pageable, 1);
            when(userLevelConfigRepository.searchByKeyword("1", pageable)).thenReturn(page);

            // when
            var result = userLevelConfigCacheService.searchLevelConfigs("1", pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("키워드 없이 전체 유저 레벨 설정을 페이징 조회한다")
        void searchLevelConfigs_noKeyword() {
            // given
            UserLevelConfig config1 = createUserLevelConfig(1L, 1, 100, 100);
            UserLevelConfig config2 = createUserLevelConfig(2L, 2, 200, 300);
            Pageable pageable = PageRequest.of(0, 10);
            Page<UserLevelConfig> page = new PageImpl<>(List.of(config1, config2), pageable, 2);
            when(userLevelConfigRepository.searchByKeyword(null, pageable)).thenReturn(page);

            // when
            var result = userLevelConfigCacheService.searchLevelConfigs(null, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getLevelConfigById 테스트")
    class GetLevelConfigByIdTest {

        @Test
        @DisplayName("ID로 유저 레벨 설정을 조회한다")
        void getLevelConfigById_success() {
            // given
            UserLevelConfig config = createUserLevelConfig(1L, 1, 100, 100);
            when(userLevelConfigRepository.findById(1L)).thenReturn(Optional.of(config));

            // when
            UserLevelConfigResponse result = userLevelConfigCacheService.getLevelConfigById(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("존재하지 않는 ID 조회 시 CustomException을 던진다")
        void getLevelConfigById_notFound_throwsException() {
            // given
            when(userLevelConfigRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userLevelConfigCacheService.getLevelConfigById(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("레벨 설정을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("getLevelConfigResponseByLevel 테스트")
    class GetLevelConfigResponseByLevelTest {

        @Test
        @DisplayName("레벨 번호로 유저 레벨 설정 Response를 조회한다")
        void getLevelConfigResponseByLevel_success() {
            // given
            UserLevelConfig config = createUserLevelConfig(1L, 3, 300, 600);
            when(userLevelConfigRepository.findByLevel(3)).thenReturn(Optional.of(config));

            // when
            UserLevelConfigResponse result = userLevelConfigCacheService.getLevelConfigResponseByLevel(3);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getLevel()).isEqualTo(3);
            assertThat(result.getRequiredExp()).isEqualTo(300);
        }

        @Test
        @DisplayName("존재하지 않는 레벨 번호 조회 시 CustomException을 던진다")
        void getLevelConfigResponseByLevel_notFound_throwsException() {
            // given
            when(userLevelConfigRepository.findByLevel(999)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userLevelConfigCacheService.getLevelConfigResponseByLevel(999))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("해당 레벨 설정을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("createLevelConfig 테스트")
    class CreateLevelConfigTest {

        @Test
        @DisplayName("유저 레벨 설정을 생성한다")
        void createLevelConfig_success() {
            // given
            UserLevelConfigRequest request = UserLevelConfigRequest.builder()
                .level(5)
                .requiredExp(500)
                .cumulativeExp(2000)
                .build();
            UserLevelConfig saved = createUserLevelConfig(5L, 5, 500, 2000);

            when(userLevelConfigRepository.existsByLevel(5)).thenReturn(false);
            when(userLevelConfigRepository.save(any(UserLevelConfig.class))).thenReturn(saved);

            // when
            UserLevelConfigResponse result = userLevelConfigCacheService.createLevelConfig(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getLevel()).isEqualTo(5);
            assertThat(result.getRequiredExp()).isEqualTo(500);
            verify(userLevelConfigRepository).save(any(UserLevelConfig.class));
        }

        @Test
        @DisplayName("이미 존재하는 레벨로 생성 시 CustomException을 던진다")
        void createLevelConfig_duplicateLevel_throwsException() {
            // given
            UserLevelConfigRequest request = UserLevelConfigRequest.builder()
                .level(3)
                .requiredExp(300)
                .cumulativeExp(600)
                .build();

            when(userLevelConfigRepository.existsByLevel(3)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> userLevelConfigCacheService.createLevelConfig(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("이미 존재하는 레벨입니다");

            verify(userLevelConfigRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateLevelConfig 테스트")
    class UpdateLevelConfigTest {

        @Test
        @DisplayName("유저 레벨 설정을 수정한다")
        void updateLevelConfig_success() {
            // given
            UserLevelConfig existing = createUserLevelConfig(1L, 3, 300, 600);
            UserLevelConfigRequest request = UserLevelConfigRequest.builder()
                .level(3)
                .requiredExp(400)
                .cumulativeExp(900)
                .build();
            UserLevelConfig saved = createUserLevelConfig(1L, 3, 400, 900);

            when(userLevelConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(userLevelConfigRepository.save(existing)).thenReturn(saved);

            // when
            UserLevelConfigResponse result = userLevelConfigCacheService.updateLevelConfig(1L, request);

            // then
            assertThat(result).isNotNull();
            assertThat(existing.getRequiredExp()).isEqualTo(400);
            assertThat(existing.getCumulativeExp()).isEqualTo(900);
            verify(userLevelConfigRepository).save(existing);
        }

        @Test
        @DisplayName("레벨 번호 변경 시 중복 레벨이 존재하면 CustomException을 던진다")
        void updateLevelConfig_duplicateLevel_throwsException() {
            // given
            UserLevelConfig existing = createUserLevelConfig(1L, 3, 300, 600);
            UserLevelConfigRequest request = UserLevelConfigRequest.builder()
                .level(4)
                .requiredExp(400)
                .cumulativeExp(1000)
                .build();

            when(userLevelConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(userLevelConfigRepository.existsByLevel(4)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> userLevelConfigCacheService.updateLevelConfig(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("이미 존재하는 레벨입니다");

            verify(userLevelConfigRepository, never()).save(any());
        }

        @Test
        @DisplayName("동일 레벨 번호로 수정 시 중복 체크를 하지 않는다")
        void updateLevelConfig_sameLevel_noExistCheck() {
            // given
            UserLevelConfig existing = createUserLevelConfig(1L, 3, 300, 600);
            UserLevelConfigRequest request = UserLevelConfigRequest.builder()
                .level(3)
                .requiredExp(350)
                .cumulativeExp(700)
                .build();

            when(userLevelConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(userLevelConfigRepository.save(existing)).thenReturn(existing);

            // when
            userLevelConfigCacheService.updateLevelConfig(1L, request);

            // then
            verify(userLevelConfigRepository, never()).existsByLevel(any());
            verify(userLevelConfigRepository).save(existing);
        }

        @Test
        @DisplayName("존재하지 않는 ID 수정 시 CustomException을 던진다")
        void updateLevelConfig_notFound_throwsException() {
            // given
            UserLevelConfigRequest request = UserLevelConfigRequest.builder()
                .level(5)
                .requiredExp(500)
                .cumulativeExp(2000)
                .build();

            when(userLevelConfigRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userLevelConfigCacheService.updateLevelConfig(999L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("레벨 설정을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("deleteLevelConfig 테스트")
    class DeleteLevelConfigTest {

        @Test
        @DisplayName("유저 레벨 설정을 삭제한다")
        void deleteLevelConfig_success() {
            // given
            when(userLevelConfigRepository.existsById(1L)).thenReturn(true);

            // when
            userLevelConfigCacheService.deleteLevelConfig(1L);

            // then
            verify(userLevelConfigRepository).deleteById(1L);
        }

        @Test
        @DisplayName("존재하지 않는 ID 삭제 시 CustomException을 던진다")
        void deleteLevelConfig_notFound_throwsException() {
            // given
            when(userLevelConfigRepository.existsById(999L)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> userLevelConfigCacheService.deleteLevelConfig(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("레벨 설정을 찾을 수 없습니다");

            verify(userLevelConfigRepository, never()).deleteById(any());
        }
    }
}
