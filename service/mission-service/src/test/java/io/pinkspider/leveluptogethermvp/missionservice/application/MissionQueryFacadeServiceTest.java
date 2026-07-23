package io.pinkspider.leveluptogethermvp.missionservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.dto.InProgressMissionDto;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionTemplate;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionTemplateRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionQueryFacadeService 테스트 (LUT-257)")
class MissionQueryFacadeServiceTest {

    @Mock
    private MissionExecutionRepository missionExecutionRepository;

    @Mock
    private DailyMissionInstanceRepository dailyMissionInstanceRepository;

    @Mock
    private MissionTemplateRepository missionTemplateRepository;

    @InjectMocks
    private MissionQueryFacadeService facadeService;

    private static final String USER_ID = "test-user-123";

    private Mission buildMission(Long missionId, Long categoryId, String categoryName, String title,
                                   MissionVisibility visibility, String guildId) {
        Mission mission = Mission.builder()
            .title(title)
            .description("설명")
            .status(io.pinkspider.global.enums.MissionStatus.IN_PROGRESS)
            .visibility(visibility)
            .type(guildId != null ? MissionType.GUILD : MissionType.PERSONAL)
            .creatorId(USER_ID)
            .guildId(guildId)
            .categoryId(categoryId)
            .categoryName(categoryName)
            .build();
        setId(mission, missionId);
        return mission;
    }

    private MissionExecution buildExecution(Mission mission, LocalDateTime startedAt) {
        MissionParticipant participant = MissionParticipant.builder()
            .mission(mission)
            .userId(USER_ID)
            .build();
        return MissionExecution.builder()
            .participant(participant)
            .startedAt(startedAt)
            .build();
    }

    private DailyMissionInstance buildInstance(Mission mission, LocalDateTime startedAt) {
        MissionParticipant participant = MissionParticipant.builder()
            .mission(mission)
            .userId(USER_ID)
            .build();
        return DailyMissionInstance.builder()
            .participant(participant)
            .missionTitle(mission.getTitle())
            .startedAt(startedAt)
            .build();
    }

    @Nested
    @DisplayName("findInProgressMission")
    class FindInProgressMissionTest {

        @Test
        @DisplayName("실행중인 execution과 instance가 모두 없으면 empty를 반환한다")
        void returnsEmptyWhenNeitherExists() {
            when(missionExecutionRepository.findInProgressByUserId(USER_ID)).thenReturn(Optional.empty());
            when(dailyMissionInstanceRepository.findInProgressByUserId(USER_ID)).thenReturn(Optional.empty());

            Optional<InProgressMissionDto> result = facadeService.findInProgressMission(USER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("execution만 존재하면 execution 기반 DTO를 반환한다")
        void returnsExecutionDtoWhenOnlyExecutionExists() {
            Mission mission = buildMission(1L, 10L, "운동", "달리기", MissionVisibility.PUBLIC, null);
            LocalDateTime startedAt = LocalDateTime.now();
            MissionExecution execution = buildExecution(mission, startedAt);

            when(missionExecutionRepository.findInProgressByUserId(USER_ID)).thenReturn(Optional.of(execution));
            when(dailyMissionInstanceRepository.findInProgressByUserId(USER_ID)).thenReturn(Optional.empty());

            Optional<InProgressMissionDto> result = facadeService.findInProgressMission(USER_ID);

            assertThat(result).isPresent();
            InProgressMissionDto dto = result.get();
            assertThat(dto.missionId()).isEqualTo(1L);
            assertThat(dto.categoryId()).isEqualTo(10L);
            assertThat(dto.categoryName()).isEqualTo("운동");
            assertThat(dto.title()).isEqualTo("달리기");
            assertThat(dto.visibility()).isEqualTo("PUBLIC");
            assertThat(dto.guildId()).isNull();
            assertThat(dto.startedAt()).isEqualTo(startedAt);
        }

        @Test
        @DisplayName("instance만 존재하면 instance 기반 DTO를 반환한다")
        void returnsInstanceDtoWhenOnlyInstanceExists() {
            Mission mission = buildMission(2L, 20L, "독서", "고정 미션", MissionVisibility.GUILD_ONLY, "100");
            LocalDateTime startedAt = LocalDateTime.now();
            DailyMissionInstance instance = buildInstance(mission, startedAt);

            when(missionExecutionRepository.findInProgressByUserId(USER_ID)).thenReturn(Optional.empty());
            when(dailyMissionInstanceRepository.findInProgressByUserId(USER_ID)).thenReturn(Optional.of(instance));

            Optional<InProgressMissionDto> result = facadeService.findInProgressMission(USER_ID);

            assertThat(result).isPresent();
            InProgressMissionDto dto = result.get();
            assertThat(dto.missionId()).isEqualTo(2L);
            assertThat(dto.visibility()).isEqualTo("GUILD_ONLY");
            assertThat(dto.guildId()).isEqualTo("100");
            assertThat(dto.startedAt()).isEqualTo(startedAt);
        }

        @Test
        @DisplayName("둘 다 존재하면 더 최근에 시작한 execution 쪽을 반환한다")
        void returnsMoreRecentExecutionWhenBothExist() {
            Mission executionMission = buildMission(1L, 10L, "운동", "달리기", MissionVisibility.PUBLIC, null);
            Mission instanceMission = buildMission(2L, 20L, "독서", "고정 미션", MissionVisibility.PRIVATE, null);

            LocalDateTime older = LocalDateTime.now().minusHours(1);
            LocalDateTime newer = LocalDateTime.now();

            MissionExecution execution = buildExecution(executionMission, newer);
            DailyMissionInstance instance = buildInstance(instanceMission, older);

            when(missionExecutionRepository.findInProgressByUserId(USER_ID)).thenReturn(Optional.of(execution));
            when(dailyMissionInstanceRepository.findInProgressByUserId(USER_ID)).thenReturn(Optional.of(instance));

            Optional<InProgressMissionDto> result = facadeService.findInProgressMission(USER_ID);

            assertThat(result).isPresent();
            assertThat(result.get().missionId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("둘 다 존재하면 더 최근에 시작한 instance 쪽을 반환한다")
        void returnsMoreRecentInstanceWhenBothExist() {
            Mission executionMission = buildMission(1L, 10L, "운동", "달리기", MissionVisibility.PUBLIC, null);
            Mission instanceMission = buildMission(2L, 20L, "독서", "고정 미션", MissionVisibility.PRIVATE, null);

            LocalDateTime older = LocalDateTime.now().minusHours(1);
            LocalDateTime newer = LocalDateTime.now();

            MissionExecution execution = buildExecution(executionMission, older);
            DailyMissionInstance instance = buildInstance(instanceMission, newer);

            when(missionExecutionRepository.findInProgressByUserId(USER_ID)).thenReturn(Optional.of(execution));
            when(dailyMissionInstanceRepository.findInProgressByUserId(USER_ID)).thenReturn(Optional.of(instance));

            Optional<InProgressMissionDto> result = facadeService.findInProgressMission(USER_ID);

            assertThat(result).isPresent();
            assertThat(result.get().missionId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("execution의 startedAt이 null이면 instance 쪽을 반환한다")
        void returnsInstanceWhenExecutionStartedAtIsNull() {
            Mission executionMission = buildMission(1L, 10L, "운동", "달리기", MissionVisibility.PUBLIC, null);
            Mission instanceMission = buildMission(2L, 20L, "독서", "고정 미션", MissionVisibility.PRIVATE, null);

            MissionExecution execution = buildExecution(executionMission, null);
            DailyMissionInstance instance = buildInstance(instanceMission, LocalDateTime.now());

            when(missionExecutionRepository.findInProgressByUserId(USER_ID)).thenReturn(Optional.of(execution));
            when(dailyMissionInstanceRepository.findInProgressByUserId(USER_ID)).thenReturn(Optional.of(instance));

            Optional<InProgressMissionDto> result = facadeService.findInProgressMission(USER_ID);

            assertThat(result).isPresent();
            assertThat(result.get().missionId()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("countClearedMissionBookTemplates")
    class CountClearedMissionBookTemplatesTest {

        @Test
        @DisplayName("execution과 instance의 클리어 템플릿을 합집합으로 카운트한다")
        void countsUnionOfClearedTemplateIds() {
            when(missionExecutionRepository.findAchievedTargetTemplateIdsByUserId(USER_ID))
                .thenReturn(List.of(1L, 2L));
            when(dailyMissionInstanceRepository.findAchievedTargetTemplateIdsByUserId(USER_ID))
                .thenReturn(List.of(2L, 3L));

            int result = facadeService.countClearedMissionBookTemplates(USER_ID);

            assertThat(result).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("findClearedMissionBookTemplateIds")
    class FindClearedMissionBookTemplateIdsTest {

        @Test
        @DisplayName("execution과 instance의 클리어 템플릿 ID 집합을 합쳐서 반환한다")
        void returnsUnionOfClearedTemplateIds() {
            when(missionExecutionRepository.findAchievedTargetTemplateIdsByUserId(USER_ID))
                .thenReturn(List.of(1L));
            when(dailyMissionInstanceRepository.findAchievedTargetTemplateIdsByUserId(USER_ID))
                .thenReturn(List.of(2L));

            Set<Long> result = facadeService.findClearedMissionBookTemplateIds(USER_ID);

            assertThat(result).containsExactlyInAnyOrder(1L, 2L);
        }
    }

    @Nested
    @DisplayName("getMissionBookTemplateTitles")
    class GetMissionBookTemplateTitlesTest {

        @Test
        @DisplayName("템플릿 ID로 제목 맵을 반환한다")
        void returnsTemplateTitleMap() {
            MissionTemplate template = MissionTemplate.builder()
                .title("30분 독서")
                .visibility(MissionVisibility.PUBLIC)
                .source(MissionSource.SYSTEM)
                .build();
            setId(template, 1L);

            when(missionTemplateRepository.findAllById(Set.of(1L))).thenReturn(List.of(template));

            var result = facadeService.getMissionBookTemplateTitles(Set.of(1L));

            assertThat(result).containsEntry(1L, "30분 독서");
        }

        @Test
        @DisplayName("templateIds가 비어있으면 빈 맵을 반환한다")
        void returnsEmptyMapForEmptyInput() {
            assertThat(facadeService.getMissionBookTemplateTitles(Set.of())).isEmpty();
            assertThat(facadeService.getMissionBookTemplateTitles(null)).isEmpty();
        }
    }
}
