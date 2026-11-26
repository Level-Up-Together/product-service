package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.pinkspider.global.component.LmObjectMapper;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MissionExecutionResponse DTO 테스트")
class MissionExecutionResponseTest {

    private final LmObjectMapper objectMapper = new LmObjectMapper();

    private static DailyMissionInstanceResponse.DailyMissionInstanceResponseBuilder
            instanceResponse() {
        return DailyMissionInstanceResponse.builder()
                .id(2L)
                .participantId(3L)
                .missionId(4L)
                .userId("user-860")
                .missionTitle("아침 스트레칭")
                .instanceDate(LocalDate.of(2026, 11, 26))
                .status(ExecutionStatus.COMPLETED)
                .expEarned(100);
    }

    @Test
    @DisplayName("LUT-860: fromDailyInstance 는 고정 미션 응답의 exp_deducted 를 그대로 옮긴다")
    void fromDailyInstance_copiesExpDeducted() {
        DailyMissionInstanceResponse instance = instanceResponse().expDeducted(10).build();

        MissionExecutionResponse response = MissionExecutionResponse.fromDailyInstance(instance);

        assertThat(response.getExpDeducted()).isEqualTo(10);
        // 받은 양은 exp_earned - exp_deducted — exp_earned 는 차감 전 금액 그대로다
        assertThat(response.getExpEarned()).isEqualTo(100);
        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getExecutionDate()).isEqualTo(LocalDate.of(2026, 11, 26));
    }

    @Test
    @DisplayName("LUT-860: 차감이 없으면(null) 옮긴 값도 null 이다")
    void fromDailyInstance_withoutExpDeducted() {
        MissionExecutionResponse response =
                MissionExecutionResponse.fromDailyInstance(instanceResponse().build());

        assertThat(response.getExpDeducted()).isNull();
    }

    @Test
    @DisplayName("LUT-860: exp_deducted 는 값이 있을 때만 JSON 에 실린다 (구앱 호환 — 추가 필드)")
    void expDeducted_serializedOnlyWhenPresent() throws Exception {
        JsonNode with =
                objectMapper.readTree(
                        objectMapper.writeValueAsString(
                                MissionExecutionResponse.builder().id(1L).expDeducted(10).build()));
        JsonNode without =
                objectMapper.readTree(
                        objectMapper.writeValueAsString(
                                MissionExecutionResponse.builder().id(1L).build()));

        assertThat(with.get("exp_deducted").asInt()).isEqualTo(10);
        assertThat(without.has("exp_deducted")).isFalse();
    }

    @Test
    @DisplayName("LUT-860: 고정 미션 응답도 exp_deducted 는 값이 있을 때만 JSON 에 실린다")
    void pinnedExpDeducted_serializedOnlyWhenPresent() throws Exception {
        JsonNode with =
                objectMapper.readTree(
                        objectMapper.writeValueAsString(
                                instanceResponse().expDeducted(10).build()));
        JsonNode without =
                objectMapper.readTree(objectMapper.writeValueAsString(instanceResponse().build()));

        assertThat(with.get("exp_deducted").asInt()).isEqualTo(10);
        assertThat(without.has("exp_deducted")).isFalse();
    }

    @Test
    @DisplayName("LUT-860: 취소 응답은 snake_case 필드 5개로 직렬화된다")
    void cancelResponse_snakeCase() throws Exception {
        JsonNode json =
                objectMapper.readTree(
                        objectMapper.writeValueAsString(
                                new MissionExecutionCancelResponse(
                                        4L, LocalDate.of(2026, 11, 26), 10, 20, 5)));

        assertThat(json.get("mission_id").asLong()).isEqualTo(4L);
        assertThat(json.get("execution_date").asText()).isEqualTo("2026-11-26");
        assertThat(json.get("exp_cancelled").asInt()).isEqualTo(10);
        assertThat(json.get("milestone_exp_cancelled").asInt()).isEqualTo(20);
        assertThat(json.get("exp_debt").asInt()).isEqualTo(5);
        assertThat(json.size()).isEqualTo(5);
    }
}
