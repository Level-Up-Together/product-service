package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.component.LmObjectMapper;
import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCreateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionIdempotencyKey;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.IdempotencyStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionIdempotencyKeyRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionIdempotencyTxService (LUT-577 트랜잭션 경계)")
class MissionIdempotencyTxServiceTest {

    private static final String USER = "user-1";
    private static final String KEY = "11111111-2222-4333-8444-555555555555";

    @Mock private MissionIdempotencyKeyRepository repository;
    @Mock private MissionService missionService;

    private final MissionCreateRequest request =
            MissionCreateRequest.builder().title("t").type(MissionType.PERSONAL).build();

    private MissionIdempotencyTxService service(ObjectMapper mapper) {
        return new MissionIdempotencyTxService(repository, missionService, mapper);
    }

    private static MissionIdempotencyKey processingRow(long id) {
        MissionIdempotencyKey row = MissionIdempotencyKey.claim(USER, KEY);
        TestReflectionUtils.setId(row, id);
        return row;
    }

    @Test
    @DisplayName("claim: PROCESSING 행을 saveAndFlush 하고 id 를 돌려준다")
    void claim_insertsProcessingRow() {
        when(repository.saveAndFlush(any()))
                .thenAnswer(
                        inv -> {
                            MissionIdempotencyKey row = inv.getArgument(0);
                            TestReflectionUtils.setId(row, 5L);
                            return row;
                        });

        Long id = service(new LmObjectMapper()).claim(USER, KEY);

        assertThat(id).isEqualTo(5L);
        ArgumentCaptor<MissionIdempotencyKey> captor =
                ArgumentCaptor.forClass(MissionIdempotencyKey.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER);
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(KEY);
        assertThat(captor.getValue().getStatus()).isEqualTo(IdempotencyStatus.PROCESSING);
        assertThat(captor.getValue().isCompleted()).isFalse();
    }

    @Test
    @DisplayName("release: 선점 행을 삭제한다")
    void release_deletes() {
        service(new LmObjectMapper()).release(5L);

        verify(repository).deleteById(5L);
    }

    @Test
    @DisplayName("find: 유저+키로 조회한다")
    void find_delegates() {
        MissionIdempotencyKey row = processingRow(5L);
        when(repository.findByUserIdAndIdempotencyKey(USER, KEY)).thenReturn(Optional.of(row));

        assertThat(service(new LmObjectMapper()).find(USER, KEY)).contains(row);
    }

    @Test
    @DisplayName("createAndComplete: 미션을 만들고 같은 트랜잭션에서 키를 완료(mission_id + 응답 JSON)한다")
    void createAndComplete_marksRowCompleted() {
        MissionResponse response = MissionResponse.builder().id(7L).title("t").build();
        when(missionService.createMission(USER, request)).thenReturn(response);
        MissionIdempotencyKey row = processingRow(5L);
        when(repository.findById(5L)).thenReturn(Optional.of(row));

        MissionResponse result = service(new LmObjectMapper()).createAndComplete(5L, USER, request);

        assertThat(result).isSameAs(response);
        assertThat(row.isCompleted()).isTrue();
        assertThat(row.getMissionId()).isEqualTo(7L);
        assertThat(row.getResponseBody()).contains("\"id\":7").contains("\"title\":\"t\"");
        assertThat(row.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("createAndComplete: 응답 직렬화에 실패해도 생성은 유지되고 본문 없이 완료된다 (재요청은 mission_id 재조회)")
    void createAndComplete_serializationFailure_completesWithoutBody() throws Exception {
        ObjectMapper broken = org.mockito.Mockito.mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(JsonMappingException.class);
        MissionResponse response = MissionResponse.builder().id(7L).build();
        when(missionService.createMission(USER, request)).thenReturn(response);
        MissionIdempotencyKey row = processingRow(5L);
        when(repository.findById(5L)).thenReturn(Optional.of(row));

        service(broken).createAndComplete(5L, USER, request);

        assertThat(row.isCompleted()).isTrue();
        assertThat(row.getMissionId()).isEqualTo(7L);
        assertThat(row.getResponseBody()).isNull();
    }

    @Test
    @DisplayName("createAndComplete: 선점 행이 없으면 IllegalStateException (트랜잭션 롤백 → 미션도 취소)")
    void createAndComplete_rowMissing_fails() {
        when(missionService.createMission(USER, request))
                .thenReturn(MissionResponse.builder().id(7L).build());
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(new LmObjectMapper()).createAndComplete(5L, USER, request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("deleteExpired: 기준 시각 이전 행을 일괄 삭제한다")
    void deleteExpired_delegates() {
        LocalDateTime threshold = LocalDateTime.of(2026, 10, 18, 13, 0);
        when(repository.deleteByCreatedAtBefore(threshold)).thenReturn(4);

        assertThat(service(new LmObjectMapper()).deleteExpired(threshold)).isEqualTo(4);
    }

    @Test
    @DisplayName("엔티티 isOlderThan: created_at 이 없으면 false, 있으면 경과 여부")
    void entity_isOlderThan() {
        MissionIdempotencyKey row = processingRow(1L);
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 12, 0);
        assertThat(row.isOlderThan(java.time.Duration.ofMinutes(5), now)).isFalse();

        row.setCreatedAt(now.minusMinutes(5));
        assertThat(row.isOlderThan(java.time.Duration.ofMinutes(5), now)).isFalse(); // 정확히 5분은 미경과

        row.setCreatedAt(now.minusMinutes(5).minusSeconds(1));
        assertThat(row.isOlderThan(java.time.Duration.ofMinutes(5), now)).isTrue();
    }
}
