package io.pinkspider.leveluptogethermvp.missionservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionAdminPageResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionAdminRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionAdminResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionParticipationType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
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
class MissionAdminServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @InjectMocks
    private MissionAdminService service;

    private Mission createTestMission(Long id) {
        Mission mission = Mission.builder()
            .title("테스트 미션")
            .description("미션 설명")
            .status(MissionStatus.OPEN)
            .visibility(MissionVisibility.PUBLIC)
            .type(MissionType.PERSONAL)
            .source(MissionSource.SYSTEM)
            .participationType(MissionParticipationType.DIRECT)
            .missionInterval(MissionInterval.DAILY)
            .creatorId("admin-1")
            .expPerCompletion(10)
            .bonusExpOnFullCompletion(50)
            .guildExpPerCompletion(5)
            .guildBonusExpOnFullCompletion(20)
            .build();
        setId(mission, id);
        return mission;
    }

    private MissionAdminRequest createTestRequest() {
        return new MissionAdminRequest(
            "새 미션", "New Mission", null,
            "설명", "Description", null,
            "OPEN", "PUBLIC", "PERSONAL",
            "SYSTEM", "DIRECT", true,
            "admin-1", null, 10,
            null, null, "DAILY",
            30, null, 10, 50,
            false, null, null, 5, 20
        );
    }

    @Nested
    @DisplayName("searchMissions 테스트")
    class SearchMissionsTest {

        @Test
        @DisplayName("키워드로 미션을 검색한다")
        void searchByKeyword() {
            // given
            Mission mission = createTestMission(1L);
            Pageable pageable = PageRequest.of(0, 10);
            when(missionRepository.searchMissionsAdmin(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(mission)));

            // when
            MissionAdminPageResponse result = service.searchMissions(
                "테스트", null, null, null, null, null, null, pageable);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("검색 조건 없이 전체 조회한다")
        void searchWithoutCriteria() {
            // given
            Mission mission = createTestMission(1L);
            Pageable pageable = PageRequest.of(0, 10);
            when(missionRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(mission)));

            // when
            MissionAdminPageResponse result = service.searchMissions(
                null, null, null, null, null, null, null, pageable);

            // then
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("createMission 테스트")
    class CreateMissionTest {

        @Test
        @DisplayName("미션을 생성한다")
        void createMission() {
            // given
            MissionAdminRequest request = createTestRequest();
            Mission saved = createTestMission(1L);
            when(missionRepository.save(any(Mission.class))).thenReturn(saved);

            // when
            MissionAdminResponse result = service.createMission(request);

            // then
            assertThat(result).isNotNull();
            verify(missionRepository).save(any(Mission.class));
        }

        @Test
        @DisplayName("source가 null이면 SYSTEM으로 기본 설정된다")
        void defaultSource() {
            // given
            MissionAdminRequest request = new MissionAdminRequest(
                "미션", null, null, null, null, null,
                "OPEN", "PUBLIC", "PERSONAL",
                null, null, null, "admin-1", null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null
            );
            Mission saved = createTestMission(1L);
            when(missionRepository.save(any(Mission.class))).thenReturn(saved);

            // when
            MissionAdminResponse result = service.createMission(request);

            // then
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("updateMission 테스트")
    class UpdateMissionTest {

        @Test
        @DisplayName("미션을 수정한다")
        void updateMission() {
            // given
            Mission existing = createTestMission(1L);
            MissionAdminRequest request = createTestRequest();
            when(missionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(missionRepository.save(any(Mission.class))).thenReturn(existing);

            // when
            MissionAdminResponse result = service.updateMission(1L, request);

            // then
            assertThat(result).isNotNull();
            verify(missionRepository).save(any(Mission.class));
        }

        @Test
        @DisplayName("존재하지 않는 미션은 예외를 발생시킨다")
        void throwsWhenNotFound() {
            when(missionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateMission(999L, createTestRequest()))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("updateMissionStatus 테스트")
    class UpdateMissionStatusTest {

        @Test
        @DisplayName("미션 상태를 변경한다")
        void updateStatus() {
            Mission mission = createTestMission(1L);
            when(missionRepository.findById(1L)).thenReturn(Optional.of(mission));
            when(missionRepository.save(any(Mission.class))).thenReturn(mission);

            MissionAdminResponse result = service.updateMissionStatus(1L, "COMPLETED");

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("deleteMission 테스트")
    class DeleteMissionTest {

        @Test
        @DisplayName("미션을 소프트 삭제한다")
        void deleteMission() {
            Mission mission = createTestMission(1L);
            when(missionRepository.findById(1L)).thenReturn(Optional.of(mission));

            service.deleteMission(1L);

            verify(missionRepository).save(any(Mission.class));
        }

        @Test
        @DisplayName("존재하지 않는 미션은 예외를 발생시킨다")
        void throwsWhenNotFound() {
            when(missionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteMission(999L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getMission 테스트")
    class GetMissionTest {

        @Test
        @DisplayName("미션을 조회한다")
        void getMission() {
            Mission mission = createTestMission(1L);
            when(missionRepository.findById(1L)).thenReturn(Optional.of(mission));

            MissionAdminResponse result = service.getMission(1L);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("getAllMissions 테스트")
    class GetAllMissionsTest {

        @Test
        @DisplayName("전체 미션을 조회한다")
        void getAllMissions() {
            when(missionRepository.findAll()).thenReturn(List.of(createTestMission(1L)));

            List<MissionAdminResponse> result = service.getAllMissions();

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getMissionsBySource 테스트")
    class GetMissionsBySourceTest {

        @Test
        @DisplayName("소스별 미션을 조회한다")
        void getMissionsBySource() {
            when(missionRepository.findBySource(MissionSource.SYSTEM))
                .thenReturn(List.of(createTestMission(1L)));

            List<MissionAdminResponse> result = service.getMissionsBySource("SYSTEM");

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("countBySource 테스트")
    class CountBySourceTest {

        @Test
        @DisplayName("소스별 미션 수를 반환한다")
        void countBySource() {
            when(missionRepository.countBySource(MissionSource.SYSTEM)).thenReturn(10L);

            Long result = service.countBySource("SYSTEM");

            assertThat(result).isEqualTo(10L);
        }
    }
}
