package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.component.LmObjectMapper;
import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.missionservice.api.exception.MissionIdempotencyException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCreateRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionIdempotencyKey;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.IdempotencyStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionIdempotencyService (LUT-577 미션 생성 멱등성)")
class MissionIdempotencyServiceTest {

    private static final String USER = "user-1";
    private static final String KEY = "11111111-2222-4333-8444-555555555555";

    @Mock private MissionService missionService;
    @Mock private MissionIdempotencyTxService txService;
    private final ObjectMapper objectMapper = new LmObjectMapper();

    private MissionIdempotencyService service;
    private MissionCreateRequest request;

    @BeforeEach
    void setUp() {
        service = new MissionIdempotencyService(missionService, txService, objectMapper);
        request =
                MissionCreateRequest.builder()
                        .title("아침 운동")
                        .type(MissionType.PERSONAL)
                        .maxParticipants(1)
                        .build();
    }

    private static MissionResponse response(long id) {
        return MissionResponse.builder().id(id).title("아침 운동").build();
    }

    private MissionIdempotencyKey row(
            IdempotencyStatus status, LocalDateTime createdAt, Long missionId, String body) {
        MissionIdempotencyKey row =
                MissionIdempotencyKey.builder()
                        .userId(USER)
                        .idempotencyKey(KEY)
                        .status(status)
                        .missionId(missionId)
                        .responseBody(body)
                        .build();
        TestReflectionUtils.setId(row, 99L);
        row.setCreatedAt(createdAt);
        return row;
    }

    private String body(long missionId) throws Exception {
        return objectMapper.writeValueAsString(response(missionId));
    }

    @Nested
    @DisplayName("헤더 없음 / 형식")
    class HeaderAndFormat {

        @ParameterizedTest(name = "키 \"{0}\" → 기존 경로(매번 생성), 멱등 저장소 미사용")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void noKey_delegatesToPlainCreate(String key) {
            when(missionService.createMission(USER, request)).thenReturn(response(1));

            MissionResponse result = service.createMission(USER, key, request);

            assertThat(result.getId()).isEqualTo(1L);
            verifyNoInteractions(txService);
        }

        @ParameterizedTest(name = "키 \"{0}\" → 400 (050401)")
        @ValueSource(
                strings = {
                    "abc",
                    "11111111-2222-4333-8444-55555555555", // 35자
                    "11111111222243338444555555555555", // 하이픈 없음
                    "g1111111-2222-4333-8444-555555555555" // 16진수 아님
                })
        void invalidKey_400(String key) {
            assertThatThrownBy(() -> service.createMission(USER, key, request))
                    .isInstanceOf(MissionIdempotencyException.class)
                    .satisfies(
                            e -> {
                                MissionIdempotencyException ex = (MissionIdempotencyException) e;
                                assertThat(ex.getCode())
                                        .isEqualTo(MissionIdempotencyException.CODE_INVALID_KEY);
                                assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                                assertThat(ex.getMessage())
                                        .isEqualTo("error.mission.idempotency_key.invalid");
                            });
            verifyNoInteractions(txService, missionService);
        }

        @Test
        @DisplayName("대문자·공백 포함 키는 trim + 소문자로 정규화해 선점한다")
        void key_isNormalized() {
            when(txService.claim(USER, KEY)).thenReturn(10L);
            when(txService.createAndComplete(10L, USER, request)).thenReturn(response(1));

            service.createMission(USER, "  " + KEY.toUpperCase() + " ", request);

            verify(txService).claim(USER, KEY);
        }
    }

    @Nested
    @DisplayName("처음 보는 키")
    class FreshKey {

        @Test
        @DisplayName("선점 후 생성·완료 트랜잭션 결과를 그대로 돌려준다")
        void claimThenCreate() {
            when(txService.claim(USER, KEY)).thenReturn(10L);
            when(txService.createAndComplete(10L, USER, request)).thenReturn(response(7));

            MissionResponse result = service.createMission(USER, KEY, request);

            assertThat(result.getId()).isEqualTo(7L);
            verify(txService, never()).release(anyLong());
            verify(missionService, never()).createMission(any(), any());
        }

        @Test
        @DisplayName("생성 실패 시 선점 행을 해제하고 예외를 그대로 던진다 (같은 키 재시도가 다시 생성할 수 있게)")
        void createFails_releasesClaim() {
            when(txService.claim(USER, KEY)).thenReturn(10L);
            IllegalArgumentException failure = new IllegalArgumentException("길드 ID 필요");
            when(txService.createAndComplete(10L, USER, request)).thenThrow(failure);

            assertThatThrownBy(() -> service.createMission(USER, KEY, request)).isSameAs(failure);

            verify(txService).release(10L);
        }

        @Test
        @DisplayName("해제까지 실패해도 원래 생성 예외가 전달된다")
        void createFails_releaseAlsoFails_originalPropagates() {
            when(txService.claim(USER, KEY)).thenReturn(10L);
            IllegalStateException failure = new IllegalStateException("boom");
            when(txService.createAndComplete(10L, USER, request)).thenThrow(failure);
            doThrow(new RuntimeException("db down")).when(txService).release(10L);

            assertThatThrownBy(() -> service.createMission(USER, KEY, request)).isSameAs(failure);
        }
    }

    @Nested
    @DisplayName("같은 유저의 같은 키 (유니크 충돌)")
    class DuplicateKey {

        @Test
        @DisplayName("완료된 키 → 저장된 응답을 그대로 재생 (같은 mission_id), 새로 만들지 않는다")
        void completed_replaysStoredBody() throws Exception {
            when(txService.claim(USER, KEY)).thenThrow(new DataIntegrityViolationException("dup"));
            when(txService.find(USER, KEY))
                    .thenReturn(
                            Optional.of(
                                    row(
                                            IdempotencyStatus.COMPLETED,
                                            LocalDateTime.now().minusMinutes(1),
                                            7L,
                                            body(7))));

            MissionResponse result = service.createMission(USER, KEY, request);

            assertThat(result.getId()).isEqualTo(7L);
            assertThat(result.getTitle()).isEqualTo("아침 운동");
            verify(txService, never()).createAndComplete(anyLong(), any(), any());
            verify(missionService, never()).createMission(any(), any());
            verify(missionService, never()).getMission(anyLong(), any());
        }

        @Test
        @DisplayName("완료됐지만 본문이 없으면 mission_id 로 재조회해 응답한다")
        void completed_withoutBody_refetchesMission() {
            when(txService.claim(USER, KEY)).thenThrow(new DataIntegrityViolationException("dup"));
            when(txService.find(USER, KEY))
                    .thenReturn(
                            Optional.of(
                                    row(
                                            IdempotencyStatus.COMPLETED,
                                            LocalDateTime.now().minusMinutes(1),
                                            7L,
                                            null)));
            when(missionService.getMission(eq(7L), isNull())).thenReturn(response(7));

            MissionResponse result = service.createMission(USER, KEY, request);

            assertThat(result.getId()).isEqualTo(7L);
        }

        @Test
        @DisplayName("완료됐지만 본문이 깨져 있으면 mission_id 로 재조회한다")
        void completed_corruptBody_refetchesMission() {
            when(txService.claim(USER, KEY)).thenThrow(new DataIntegrityViolationException("dup"));
            when(txService.find(USER, KEY))
                    .thenReturn(
                            Optional.of(
                                    row(
                                            IdempotencyStatus.COMPLETED,
                                            LocalDateTime.now().minusMinutes(1),
                                            7L,
                                            "{not json")));
            when(missionService.getMission(eq(7L), isNull())).thenReturn(response(7));

            assertThat(service.createMission(USER, KEY, request).getId()).isEqualTo(7L);
        }

        @Test
        @DisplayName("완료 행에 본문도 mission_id 도 없으면(비정상) IllegalStateException")
        void completed_withoutBodyAndMission_fails() {
            when(txService.claim(USER, KEY)).thenThrow(new DataIntegrityViolationException("dup"));
            when(txService.find(USER, KEY))
                    .thenReturn(
                            Optional.of(
                                    row(
                                            IdempotencyStatus.COMPLETED,
                                            LocalDateTime.now().minusMinutes(1),
                                            null,
                                            null)));

            assertThatThrownBy(() -> service.createMission(USER, KEY, request))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("처리 중(최근 선점) → 409 (050402), 생성·해제 없음")
        void processing_recent_409() {
            when(txService.claim(USER, KEY)).thenThrow(new DataIntegrityViolationException("dup"));
            when(txService.find(USER, KEY))
                    .thenReturn(
                            Optional.of(
                                    row(
                                            IdempotencyStatus.PROCESSING,
                                            LocalDateTime.now().minusSeconds(10),
                                            null,
                                            null)));

            assertThatThrownBy(() -> service.createMission(USER, KEY, request))
                    .isInstanceOf(MissionIdempotencyException.class)
                    .satisfies(
                            e -> {
                                MissionIdempotencyException ex = (MissionIdempotencyException) e;
                                assertThat(ex.getCode())
                                        .isEqualTo(MissionIdempotencyException.CODE_IN_PROGRESS);
                                assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                            });
            verify(txService, never()).release(anyLong());
            verify(txService, never()).createAndComplete(anyLong(), any(), any());
        }

        @Test
        @DisplayName("created_at 이 아직 없는(미flush) PROCESSING 행도 처리 중으로 본다")
        void processing_withoutCreatedAt_409() {
            when(txService.claim(USER, KEY)).thenThrow(new DataIntegrityViolationException("dup"));
            when(txService.find(USER, KEY))
                    .thenReturn(Optional.of(row(IdempotencyStatus.PROCESSING, null, null, null)));

            assertThatThrownBy(() -> service.createMission(USER, KEY, request))
                    .isInstanceOf(MissionIdempotencyException.class);
        }

        @Test
        @DisplayName("stale PROCESSING(5분 초과, JVM 중단 등) → 행을 지우고 다시 선점해 생성한다")
        void processing_stale_releasesAndRetries() {
            when(txService.claim(USER, KEY))
                    .thenThrow(new DataIntegrityViolationException("dup"))
                    .thenReturn(11L);
            when(txService.find(USER, KEY))
                    .thenReturn(
                            Optional.of(
                                    row(
                                            IdempotencyStatus.PROCESSING,
                                            LocalDateTime.now()
                                                    .minus(
                                                            MissionIdempotencyService
                                                                    .STALE_PROCESSING)
                                                    .minusSeconds(1),
                                            null,
                                            null)));
            when(txService.createAndComplete(11L, USER, request)).thenReturn(response(8));

            MissionResponse result = service.createMission(USER, KEY, request);

            assertThat(result.getId()).isEqualTo(8L);
            verify(txService).release(99L);
        }

        @Test
        @DisplayName("보관 기간(24시간) 지난 완료 키 → 재생하지 않고 지운 뒤 새 요청으로 생성한다")
        void expired_treatedAsNew() throws Exception {
            when(txService.claim(USER, KEY))
                    .thenThrow(new DataIntegrityViolationException("dup"))
                    .thenReturn(11L);
            when(txService.find(USER, KEY))
                    .thenReturn(
                            Optional.of(
                                    row(
                                            IdempotencyStatus.COMPLETED,
                                            LocalDateTime.now()
                                                    .minus(MissionIdempotencyService.RETENTION)
                                                    .minusSeconds(1),
                                            7L,
                                            body(7))));
            when(txService.createAndComplete(11L, USER, request)).thenReturn(response(8));

            MissionResponse result = service.createMission(USER, KEY, request);

            assertThat(result.getId()).isEqualTo(8L);
            verify(txService).release(99L);
        }

        @Test
        @DisplayName("충돌 직후 행이 사라졌으면(그 사이 해제) 다시 선점한다")
        void rowGone_retriesClaim() {
            when(txService.claim(USER, KEY))
                    .thenThrow(new DataIntegrityViolationException("dup"))
                    .thenReturn(11L);
            when(txService.find(USER, KEY)).thenReturn(Optional.empty());
            when(txService.createAndComplete(11L, USER, request)).thenReturn(response(8));

            assertThat(service.createMission(USER, KEY, request).getId()).isEqualTo(8L);
        }

        @Test
        @DisplayName("재선점까지 연속 충돌하고 행도 없으면(재시도 소진) 409")
        void repeatedConflict_409() {
            when(txService.claim(USER, KEY)).thenThrow(new DataIntegrityViolationException("dup"));
            when(txService.find(USER, KEY)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createMission(USER, KEY, request))
                    .isInstanceOf(MissionIdempotencyException.class)
                    .extracting("code")
                    .isEqualTo(MissionIdempotencyException.CODE_IN_PROGRESS);
            verify(txService, never()).createAndComplete(anyLong(), any(), any());
        }
    }

    @Nested
    @DisplayName("만료 정리")
    class Cleanup {

        @Test
        @DisplayName("24시간 전 시각을 기준으로 삭제하고 건수를 돌려준다")
        void cleanupExpired_deletesBeforeRetention() {
            ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
            when(txService.deleteExpired(any())).thenReturn(3);

            int deleted = service.cleanupExpired();

            assertThat(deleted).isEqualTo(3);
            verify(txService).deleteExpired(threshold.capture());
            LocalDateTime expected = LocalDateTime.now().minus(MissionIdempotencyService.RETENTION);
            assertThat(threshold.getValue())
                    .isBetween(expected.minusSeconds(5), expected.plusSeconds(5));
        }

        @Test
        @DisplayName("지울 것이 없으면 0")
        void cleanupExpired_nothing() {
            when(txService.deleteExpired(any())).thenReturn(0);

            assertThat(service.cleanupExpired()).isZero();
        }
    }
}
