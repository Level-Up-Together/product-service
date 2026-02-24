package io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.dto.AttendanceRewardConfigRequest;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.dto.AttendanceRewardConfigResponse;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.entity.AttendanceRewardConfig;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.enums.AttendanceRewardType;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.infrastructure.AttendanceRewardConfigRepository;
import java.time.LocalDate;
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
class AttendanceRewardConfigCacheServiceTest {

    @Mock
    private AttendanceRewardConfigRepository rewardConfigRepository;

    @InjectMocks
    private AttendanceRewardConfigCacheService attendanceRewardConfigCacheService;

    private AttendanceRewardConfig createRewardConfig(Long id, AttendanceRewardType type,
                                                       int requiredDays, int rewardExp, boolean isActive) {
        AttendanceRewardConfig config = AttendanceRewardConfig.builder()
            .rewardType(type)
            .requiredDays(requiredDays)
            .rewardExp(rewardExp)
            .rewardPoints(0)
            .description(type.getDisplayName())
            .isActive(isActive)
            .build();
        setId(config, id);
        return config;
    }

    @Nested
    @DisplayName("getAllActiveConfigs 테스트")
    class GetAllActiveConfigsTest {

        @Test
        @DisplayName("활성화된 모든 출석 보상 설정을 requiredDays 오름차순으로 조회한다")
        void getAllActiveConfigs_success() {
            // given
            AttendanceRewardConfig daily = createRewardConfig(1L, AttendanceRewardType.DAILY, 1, 10, true);
            AttendanceRewardConfig consecutive3 = createRewardConfig(2L, AttendanceRewardType.CONSECUTIVE_3, 3, 20, true);
            when(rewardConfigRepository.findByIsActiveTrueOrderByRequiredDaysAsc())
                .thenReturn(List.of(daily, consecutive3));

            // when
            List<AttendanceRewardConfig> result = attendanceRewardConfigCacheService.getAllActiveConfigs();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getRewardType()).isEqualTo(AttendanceRewardType.DAILY);
            assertThat(result.get(1).getRewardType()).isEqualTo(AttendanceRewardType.CONSECUTIVE_3);
            verify(rewardConfigRepository).findByIsActiveTrueOrderByRequiredDaysAsc();
        }

        @Test
        @DisplayName("활성화된 출석 보상 설정이 없으면 빈 목록을 반환한다")
        void getAllActiveConfigs_empty() {
            // given
            when(rewardConfigRepository.findByIsActiveTrueOrderByRequiredDaysAsc())
                .thenReturn(Collections.emptyList());

            // when
            List<AttendanceRewardConfig> result = attendanceRewardConfigCacheService.getAllActiveConfigs();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getConfigByRewardType 테스트")
    class GetConfigByRewardTypeTest {

        @Test
        @DisplayName("보상 타입으로 출석 보상 설정을 조회한다")
        void getConfigByRewardType_success() {
            // given
            AttendanceRewardConfig config = createRewardConfig(1L, AttendanceRewardType.DAILY, 1, 10, true);
            when(rewardConfigRepository.findByRewardTypeAndIsActiveTrue(AttendanceRewardType.DAILY))
                .thenReturn(Optional.of(config));

            // when
            AttendanceRewardConfig result = attendanceRewardConfigCacheService
                .getConfigByRewardType(AttendanceRewardType.DAILY);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRewardType()).isEqualTo(AttendanceRewardType.DAILY);
            assertThat(result.getRewardExp()).isEqualTo(10);
        }

        @Test
        @DisplayName("존재하지 않는 보상 타입 조회 시 null을 반환한다")
        void getConfigByRewardType_notFound_returnsNull() {
            // given
            when(rewardConfigRepository.findByRewardTypeAndIsActiveTrue(AttendanceRewardType.SPECIAL_DAY))
                .thenReturn(Optional.empty());

            // when
            AttendanceRewardConfig result = attendanceRewardConfigCacheService
                .getConfigByRewardType(AttendanceRewardType.SPECIAL_DAY);

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("initializeDefaultRewardConfigs 테스트")
    class InitializeDefaultRewardConfigsTest {

        @Test
        @DisplayName("데이터가 없을 때 기본 보상 설정을 초기화한다")
        void initializeDefaultRewardConfigs_success() {
            // given
            when(rewardConfigRepository.count()).thenReturn(0L);
            when(rewardConfigRepository.save(any(AttendanceRewardConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            attendanceRewardConfigCacheService.initializeDefaultRewardConfigs();

            // then
            verify(rewardConfigRepository, times(AttendanceRewardType.values().length))
                .save(any(AttendanceRewardConfig.class));
        }

        @Test
        @DisplayName("데이터가 이미 존재하면 초기화를 건너뛴다")
        void initializeDefaultRewardConfigs_alreadyExists_skips() {
            // given
            when(rewardConfigRepository.count()).thenReturn(7L);

            // when
            attendanceRewardConfigCacheService.initializeDefaultRewardConfigs();

            // then
            verify(rewardConfigRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getAllConfigResponses 테스트")
    class GetAllConfigResponsesTest {

        @Test
        @DisplayName("모든 출석 보상 설정 Response를 requiredDays 오름차순으로 조회한다")
        void getAllConfigResponses_success() {
            // given
            AttendanceRewardConfig daily = createRewardConfig(1L, AttendanceRewardType.DAILY, 1, 10, true);
            AttendanceRewardConfig inactive = createRewardConfig(2L, AttendanceRewardType.SPECIAL_DAY, 1, 100, false);
            when(rewardConfigRepository.findAllByOrderByRequiredDaysAsc())
                .thenReturn(List.of(daily, inactive));

            // when
            List<AttendanceRewardConfigResponse> result = attendanceRewardConfigCacheService.getAllConfigResponses();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getRewardType()).isEqualTo(AttendanceRewardType.DAILY);
            assertThat(result.get(1).getRewardType()).isEqualTo(AttendanceRewardType.SPECIAL_DAY);
        }

        @Test
        @DisplayName("출석 보상 설정이 없으면 빈 목록을 반환한다")
        void getAllConfigResponses_empty() {
            // given
            when(rewardConfigRepository.findAllByOrderByRequiredDaysAsc()).thenReturn(Collections.emptyList());

            // when
            List<AttendanceRewardConfigResponse> result = attendanceRewardConfigCacheService.getAllConfigResponses();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getActiveConfigResponses 테스트")
    class GetActiveConfigResponsesTest {

        @Test
        @DisplayName("활성화된 출석 보상 설정 Response 목록을 조회한다")
        void getActiveConfigResponses_success() {
            // given
            AttendanceRewardConfig daily = createRewardConfig(1L, AttendanceRewardType.DAILY, 1, 10, true);
            when(rewardConfigRepository.findByIsActiveTrueOrderByRequiredDaysAsc())
                .thenReturn(List.of(daily));

            // when
            List<AttendanceRewardConfigResponse> result = attendanceRewardConfigCacheService.getActiveConfigResponses();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getRewardType()).isEqualTo(AttendanceRewardType.DAILY);
            assertThat(result.get(0).getIsActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("getActiveConsecutiveRewardResponses 테스트")
    class GetActiveConsecutiveRewardResponsesTest {

        @Test
        @DisplayName("활성화된 연속 출석 보상 설정 목록을 조회한다")
        void getActiveConsecutiveRewardResponses_success() {
            // given
            AttendanceRewardConfig consecutive3 = createRewardConfig(1L, AttendanceRewardType.CONSECUTIVE_3, 3, 20, true);
            AttendanceRewardConfig consecutive7 = createRewardConfig(2L, AttendanceRewardType.CONSECUTIVE_7, 7, 50, true);
            when(rewardConfigRepository.findActiveConsecutiveRewards())
                .thenReturn(List.of(consecutive3, consecutive7));

            // when
            List<AttendanceRewardConfigResponse> result =
                attendanceRewardConfigCacheService.getActiveConsecutiveRewardResponses();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getRewardType()).isEqualTo(AttendanceRewardType.CONSECUTIVE_3);
            assertThat(result.get(1).getRewardType()).isEqualTo(AttendanceRewardType.CONSECUTIVE_7);
        }

        @Test
        @DisplayName("활성화된 연속 출석 보상 설정이 없으면 빈 목록을 반환한다")
        void getActiveConsecutiveRewardResponses_empty() {
            // given
            when(rewardConfigRepository.findActiveConsecutiveRewards()).thenReturn(Collections.emptyList());

            // when
            List<AttendanceRewardConfigResponse> result =
                attendanceRewardConfigCacheService.getActiveConsecutiveRewardResponses();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("searchConfigs 테스트")
    class SearchConfigsTest {

        @Test
        @DisplayName("키워드로 출석 보상 설정을 페이징 조회한다")
        void searchConfigs_success() {
            // given
            AttendanceRewardConfig config = createRewardConfig(1L, AttendanceRewardType.DAILY, 1, 10, true);
            Pageable pageable = PageRequest.of(0, 10);
            Page<AttendanceRewardConfig> page = new PageImpl<>(List.of(config), pageable, 1);
            when(rewardConfigRepository.searchByKeyword("DAILY", pageable)).thenReturn(page);

            // when
            var result = attendanceRewardConfigCacheService.searchConfigs("DAILY", pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("키워드 없이 전체 출석 보상 설정을 페이징 조회한다")
        void searchConfigs_noKeyword() {
            // given
            AttendanceRewardConfig config1 = createRewardConfig(1L, AttendanceRewardType.DAILY, 1, 10, true);
            AttendanceRewardConfig config2 = createRewardConfig(2L, AttendanceRewardType.CONSECUTIVE_3, 3, 20, true);
            Pageable pageable = PageRequest.of(0, 10);
            Page<AttendanceRewardConfig> page = new PageImpl<>(List.of(config1, config2), pageable, 2);
            when(rewardConfigRepository.searchByKeyword(null, pageable)).thenReturn(page);

            // when
            var result = attendanceRewardConfigCacheService.searchConfigs(null, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getConfigById 테스트")
    class GetConfigByIdTest {

        @Test
        @DisplayName("ID로 출석 보상 설정을 조회한다")
        void getConfigById_success() {
            // given
            AttendanceRewardConfig config = createRewardConfig(1L, AttendanceRewardType.DAILY, 1, 10, true);
            when(rewardConfigRepository.findById(1L)).thenReturn(Optional.of(config));

            // when
            AttendanceRewardConfigResponse result = attendanceRewardConfigCacheService.getConfigById(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getRewardType()).isEqualTo(AttendanceRewardType.DAILY);
        }

        @Test
        @DisplayName("존재하지 않는 ID 조회 시 CustomException을 던진다")
        void getConfigById_notFound_throwsException() {
            // given
            when(rewardConfigRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> attendanceRewardConfigCacheService.getConfigById(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("출석 보상 설정을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("createConfig 테스트")
    class CreateConfigTest {

        @Test
        @DisplayName("출석 보상 설정을 생성한다")
        void createConfig_success() {
            // given
            AttendanceRewardConfigRequest request = AttendanceRewardConfigRequest.builder()
                .rewardType(AttendanceRewardType.CONSECUTIVE_7)
                .requiredDays(7)
                .rewardExp(50)
                .rewardPoints(10)
                .description("7일 연속 출석 보상")
                .isActive(true)
                .build();

            AttendanceRewardConfig saved = createRewardConfig(1L, AttendanceRewardType.CONSECUTIVE_7, 7, 50, true);

            when(rewardConfigRepository.existsByRewardType(AttendanceRewardType.CONSECUTIVE_7)).thenReturn(false);
            when(rewardConfigRepository.save(any(AttendanceRewardConfig.class))).thenReturn(saved);

            // when
            AttendanceRewardConfigResponse result = attendanceRewardConfigCacheService.createConfig(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRewardType()).isEqualTo(AttendanceRewardType.CONSECUTIVE_7);
            verify(rewardConfigRepository).save(any(AttendanceRewardConfig.class));
        }

        @Test
        @DisplayName("rewardExp와 rewardPoints가 null이면 0으로 설정된다")
        void createConfig_nullRewardExpAndPoints_usesZero() {
            // given
            AttendanceRewardConfigRequest request = AttendanceRewardConfigRequest.builder()
                .rewardType(AttendanceRewardType.DAILY)
                .requiredDays(1)
                .rewardExp(null)
                .rewardPoints(null)
                .build();

            AttendanceRewardConfig saved = createRewardConfig(1L, AttendanceRewardType.DAILY, 1, 0, true);

            when(rewardConfigRepository.existsByRewardType(AttendanceRewardType.DAILY)).thenReturn(false);
            when(rewardConfigRepository.save(any(AttendanceRewardConfig.class))).thenReturn(saved);

            // when
            AttendanceRewardConfigResponse result = attendanceRewardConfigCacheService.createConfig(request);

            // then
            assertThat(result).isNotNull();
            verify(rewardConfigRepository).save(any(AttendanceRewardConfig.class));
        }

        @Test
        @DisplayName("이미 존재하는 보상 타입으로 생성 시 CustomException을 던진다")
        void createConfig_duplicateRewardType_throwsException() {
            // given
            AttendanceRewardConfigRequest request = AttendanceRewardConfigRequest.builder()
                .rewardType(AttendanceRewardType.DAILY)
                .requiredDays(1)
                .build();

            when(rewardConfigRepository.existsByRewardType(AttendanceRewardType.DAILY)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> attendanceRewardConfigCacheService.createConfig(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("이미 존재하는 보상 타입입니다");

            verify(rewardConfigRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateConfig 테스트")
    class UpdateConfigTest {

        @Test
        @DisplayName("출석 보상 설정을 수정한다")
        void updateConfig_success() {
            // given
            AttendanceRewardConfig existing = createRewardConfig(1L, AttendanceRewardType.DAILY, 1, 10, true);
            AttendanceRewardConfigRequest request = AttendanceRewardConfigRequest.builder()
                .rewardType(AttendanceRewardType.DAILY)
                .requiredDays(1)
                .rewardExp(20)
                .rewardPoints(5)
                .description("수정된 일일 출석")
                .isActive(true)
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 12, 31))
                .build();

            when(rewardConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(rewardConfigRepository.save(existing)).thenReturn(existing);

            // when
            AttendanceRewardConfigResponse result = attendanceRewardConfigCacheService.updateConfig(1L, request);

            // then
            assertThat(result).isNotNull();
            assertThat(existing.getRewardExp()).isEqualTo(20);
            assertThat(existing.getDescription()).isEqualTo("수정된 일일 출석");
            verify(rewardConfigRepository).save(existing);
        }

        @Test
        @DisplayName("보상 타입 변경 시 중복 타입이 존재하면 CustomException을 던진다")
        void updateConfig_duplicateRewardType_throwsException() {
            // given
            AttendanceRewardConfig existing = createRewardConfig(1L, AttendanceRewardType.DAILY, 1, 10, true);
            AttendanceRewardConfigRequest request = AttendanceRewardConfigRequest.builder()
                .rewardType(AttendanceRewardType.CONSECUTIVE_3)
                .requiredDays(3)
                .build();

            when(rewardConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(rewardConfigRepository.existsByRewardType(AttendanceRewardType.CONSECUTIVE_3)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> attendanceRewardConfigCacheService.updateConfig(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("이미 존재하는 보상 타입입니다");

            verify(rewardConfigRepository, never()).save(any());
        }

        @Test
        @DisplayName("동일 보상 타입으로 수정 시 중복 체크를 하지 않는다")
        void updateConfig_sameRewardType_noExistCheck() {
            // given
            AttendanceRewardConfig existing = createRewardConfig(1L, AttendanceRewardType.DAILY, 1, 10, true);
            AttendanceRewardConfigRequest request = AttendanceRewardConfigRequest.builder()
                .rewardType(AttendanceRewardType.DAILY)
                .requiredDays(1)
                .rewardExp(15)
                .build();

            when(rewardConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(rewardConfigRepository.save(existing)).thenReturn(existing);

            // when
            attendanceRewardConfigCacheService.updateConfig(1L, request);

            // then
            verify(rewardConfigRepository, never()).existsByRewardType(any());
            verify(rewardConfigRepository).save(existing);
        }

        @Test
        @DisplayName("존재하지 않는 ID 수정 시 CustomException을 던진다")
        void updateConfig_notFound_throwsException() {
            // given
            AttendanceRewardConfigRequest request = AttendanceRewardConfigRequest.builder()
                .rewardType(AttendanceRewardType.DAILY)
                .requiredDays(1)
                .build();

            when(rewardConfigRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> attendanceRewardConfigCacheService.updateConfig(999L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("출석 보상 설정을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("toggleActiveStatus 테스트")
    class ToggleActiveStatusTest {

        @Test
        @DisplayName("활성화 상태를 비활성화로 변경한다")
        void toggleActiveStatus_activeToinactive() {
            // given
            AttendanceRewardConfig existing = createRewardConfig(1L, AttendanceRewardType.DAILY, 1, 10, true);
            when(rewardConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(rewardConfigRepository.save(existing)).thenReturn(existing);

            // when
            AttendanceRewardConfigResponse result = attendanceRewardConfigCacheService.toggleActiveStatus(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(existing.getIsActive()).isFalse();
            verify(rewardConfigRepository).save(existing);
        }

        @Test
        @DisplayName("비활성화 상태를 활성화로 변경한다")
        void toggleActiveStatus_inactiveToActive() {
            // given
            AttendanceRewardConfig existing = createRewardConfig(1L, AttendanceRewardType.DAILY, 1, 10, false);
            when(rewardConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(rewardConfigRepository.save(existing)).thenReturn(existing);

            // when
            AttendanceRewardConfigResponse result = attendanceRewardConfigCacheService.toggleActiveStatus(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(existing.getIsActive()).isTrue();
            verify(rewardConfigRepository).save(existing);
        }

        @Test
        @DisplayName("존재하지 않는 ID 상태 변경 시 CustomException을 던진다")
        void toggleActiveStatus_notFound_throwsException() {
            // given
            when(rewardConfigRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> attendanceRewardConfigCacheService.toggleActiveStatus(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("출석 보상 설정을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("deleteConfig 테스트")
    class DeleteConfigTest {

        @Test
        @DisplayName("출석 보상 설정을 삭제한다")
        void deleteConfig_success() {
            // given
            when(rewardConfigRepository.existsById(1L)).thenReturn(true);

            // when
            attendanceRewardConfigCacheService.deleteConfig(1L);

            // then
            verify(rewardConfigRepository).deleteById(1L);
        }

        @Test
        @DisplayName("존재하지 않는 ID 삭제 시 CustomException을 던진다")
        void deleteConfig_notFound_throwsException() {
            // given
            when(rewardConfigRepository.existsById(999L)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> attendanceRewardConfigCacheService.deleteConfig(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("출석 보상 설정을 찾을 수 없습니다");

            verify(rewardConfigRepository, never()).deleteById(any());
        }
    }
}
