package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("MissionParticipant 엔티티 테스트")
class MissionParticipantTest {

    private static final String TEST_USER_ID = "test-user-123";

    private Mission mission;

    @BeforeEach
    void setUp() {
        mission =
                Mission.builder()
                        .title("30분 독서")
                        .description("매일 30분 독서하기")
                        .creatorId(TEST_USER_ID)
                        .status(MissionStatus.IN_PROGRESS)
                        .visibility(MissionVisibility.PRIVATE)
                        .type(MissionType.PERSONAL)
                        .build();
        setId(mission, 1L);
    }

    private MissionParticipant participant(ParticipantStatus status) {
        MissionParticipant participant =
                MissionParticipant.builder()
                        .mission(mission)
                        .userId(TEST_USER_ID)
                        .status(status)
                        .progress(100)
                        .completedAt(LocalDateTime.now())
                        .build();
        setId(participant, 1L);
        return participant;
    }

    @Nested
    @DisplayName("reopen 메서드 테스트 (LUT-860 완료한 수행 취소)")
    class ReopenTest {

        @Test
        @DisplayName("완료된 참여를 진행 중으로 되돌리고 완료 일시를 비운다")
        void reopen_fromCompleted() {
            // given
            MissionParticipant participant = participant(ParticipantStatus.COMPLETED);

            // when
            participant.reopen();

            // then
            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.IN_PROGRESS);
            assertThat(participant.getCompletedAt()).isNull();
            // 진행도는 호출하는 쪽이 정한다
            assertThat(participant.getProgress()).isEqualTo(100);
        }

        @Test
        @DisplayName("되돌린 참여는 진행도를 바꾸고 다시 완료할 수 있다")
        void reopen_thenCompleteAgain() {
            // given
            MissionParticipant participant = participant(ParticipantStatus.COMPLETED);
            participant.reopen();

            // when
            participant.updateProgress(0);
            participant.complete();

            // then
            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.COMPLETED);
            assertThat(participant.getProgress()).isEqualTo(100);
            assertThat(participant.getCompletedAt()).isNotNull();
        }

        @ParameterizedTest(name = "{0} 상태는 그대로 둔다")
        @EnumSource(
                value = ParticipantStatus.class,
                names = {"COMPLETED"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("완료 상태가 아니면 아무것도 바꾸지 않는다 (예외도 없다)")
        void reopen_notCompleted_noop(ParticipantStatus status) {
            // given
            MissionParticipant participant = participant(status);
            LocalDateTime completedAt = participant.getCompletedAt();

            // when
            participant.reopen();

            // then
            assertThat(participant.getStatus()).isEqualTo(status);
            assertThat(participant.getCompletedAt()).isEqualTo(completedAt);
            assertThat(participant.getProgress()).isEqualTo(100);
        }
    }
}
