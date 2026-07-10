package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GrantMissionBookDiamondStep 단위 테스트")
class GrantMissionBookDiamondStepTest {

    @Mock
    private GamificationQueryFacade gamificationQueryFacadeService;

    @InjectMocks
    private GrantMissionBookDiamondStep step;

    private static final String USER_ID = "test-user-123";

    private Mission missionBookMission(Long templateId, Integer targetDurationMinutes) {
        return Mission.builder()
            .title("아침 스트레칭")
            .creatorId(USER_ID)
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PRIVATE)
            .type(MissionType.PERSONAL)
            .source(MissionSource.SYSTEM)
            .baseMissionId(templateId)
            .targetDurationMinutes(targetDurationMinutes)
            .build();
    }

    private MissionCompletionContext context(Mission mission, int expEarned) {
        MissionCompletionContext context = new MissionCompletionContext(1L, USER_ID, null, false);
        context.setMission(mission);
        context.setUserExpEarned(expEarned);
        return context;
    }

    @Test
    @DisplayName("미션북 미션의 목표달성(exp >= target) 시 다이아 지급을 호출한다")
    void awardsOnTargetAchievement() {
        Mission mission = missionBookMission(77L, 30);
        when(gamificationQueryFacadeService.awardMissionBookDiamond(USER_ID, 77L, "아침 스트레칭"))
            .thenReturn(true);

        SagaStepResult result = step.execute(context(mission, 30));

        assertThat(result.isSuccess()).isTrue();
        verify(gamificationQueryFacadeService).awardMissionBookDiamond(USER_ID, 77L, "아침 스트레칭");
    }

    @Test
    @DisplayName("이미 지급된 템플릿이면 지급 없이 성공 처리한다")
    void succeedsWhenAlreadyAwarded() {
        Mission mission = missionBookMission(77L, 30);
        when(gamificationQueryFacadeService.awardMissionBookDiamond(USER_ID, 77L, "아침 스트레칭"))
            .thenReturn(false);

        SagaStepResult result = step.execute(context(mission, 45));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("목표시간 미달이면 지급하지 않는다")
    void skipsWhenTargetNotReached() {
        Mission mission = missionBookMission(77L, 30);

        SagaStepResult result = step.execute(context(mission, 29));

        assertThat(result.isSuccess()).isTrue();
        verify(gamificationQueryFacadeService, never())
            .awardMissionBookDiamond(anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("미션북 출처(source=SYSTEM)가 아니면 지급하지 않는다")
    void skipsNonMissionBookMission() {
        Mission mission = missionBookMission(77L, 30);
        mission.setSource(MissionSource.USER);

        SagaStepResult result = step.execute(context(mission, 60));

        assertThat(result.isSuccess()).isTrue();
        verify(gamificationQueryFacadeService, never())
            .awardMissionBookDiamond(anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("목표시간이 없는 미션은 지급하지 않는다")
    void skipsMissionWithoutTarget() {
        Mission mission = missionBookMission(77L, null);

        SagaStepResult result = step.execute(context(mission, 60));

        assertThat(result.isSuccess()).isTrue();
        verify(gamificationQueryFacadeService, never())
            .awardMissionBookDiamond(anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("다이아 지급 실패는 실패로 반환하되 필수 스텝이 아니다")
    void failureIsNonMandatory() {
        Mission mission = missionBookMission(77L, 30);
        when(gamificationQueryFacadeService.awardMissionBookDiamond(any(), any(), any()))
            .thenThrow(new RuntimeException("boom"));

        SagaStepResult result = step.execute(context(mission, 30));

        assertThat(result.isSuccess()).isFalse();
        assertThat(step.isMandatory()).isFalse();
    }

    @Test
    @DisplayName("compensate 는 다이아를 회수하지 않고 성공을 반환한다")
    void compensateDoesNotRevoke() {
        SagaStepResult result = step.compensate(context(missionBookMission(77L, 30), 30));

        assertThat(result.isSuccess()).isTrue();
    }
}
