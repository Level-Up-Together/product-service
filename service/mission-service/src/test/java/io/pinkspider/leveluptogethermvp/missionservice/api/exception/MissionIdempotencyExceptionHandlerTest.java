package io.pinkspider.leveluptogethermvp.missionservice.api.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.global.api.ApiResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("MissionIdempotencyExceptionHandler (LUT-577 400/409 응답)")
class MissionIdempotencyExceptionHandlerTest {

    @Test
    @DisplayName("UUID 형식 오류 → 400 + 050401 + 현지화 메시지")
    void invalidKey_400_localized() {
        MissionIdempotencyExceptionHandler handler = new MissionIdempotencyExceptionHandler();
        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(
                        eq("error.mission.idempotency_key.invalid"),
                        isNull(),
                        eq("error.mission.idempotency_key.invalid"),
                        any()))
                .thenReturn("Idempotency-Key 헤더는 UUID 형식이어야 합니다.");
        ReflectionTestUtils.setField(handler, "messageSource", messageSource);

        ResponseEntity<Object> res = handler.handle(MissionIdempotencyException.invalidKey());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResult<?> body = (ApiResult<?>) res.getBody();
        assertThat(body.getCode()).isEqualTo("050401");
        assertThat(body.getMessage()).isEqualTo("Idempotency-Key 헤더는 UUID 형식이어야 합니다.");
    }

    @Test
    @DisplayName("처리 중 → 409 + 050402. MessageSource 가 없으면 키를 그대로 돌려준다")
    void inProgress_409_withoutMessageSource() {
        MissionIdempotencyExceptionHandler handler = new MissionIdempotencyExceptionHandler();

        ResponseEntity<Object> res = handler.handle(MissionIdempotencyException.inProgress());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiResult<?> body = (ApiResult<?>) res.getBody();
        assertThat(body.getCode()).isEqualTo("050402");
        assertThat(body.getMessage()).isEqualTo("error.mission.idempotency_key.in_progress");
    }

    @Test
    @DisplayName("LUT-782 추천 미션 없음 → 404 + 050501, 입력 오류 → 400 + 050502")
    void recommended_statuses() {
        MissionIdempotencyExceptionHandler handler = new MissionIdempotencyExceptionHandler();

        ResponseEntity<Object> notFound =
                handler.handleRecommended(RecommendedMissionException.notFound());
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(((ApiResult<?>) notFound.getBody()).getCode()).isEqualTo("050501");

        // LUT-803: 체크 미션이 아닌데 원탭 완료 → 400 050503
        ResponseEntity<Object> notSimple =
                handler.handleRecommended(RecommendedMissionException.notSimple());
        assertThat(notSimple.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((ApiResult<?>) notSimple.getBody()).getCode()).isEqualTo("050503");
        assertThat(((ApiResult<?>) notSimple.getBody()).getMessage())
                .isEqualTo("error.mission.recommended.not_simple");

        for (RecommendedMissionException e :
                java.util.List.of(
                        RecommendedMissionException.targetMinutesRequired(),
                        RecommendedMissionException.invalidCategory(),
                        RecommendedMissionException.invalidReorder())) {
            ResponseEntity<Object> res = handler.handleRecommended(e);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ApiResult<?> body = (ApiResult<?>) res.getBody();
            assertThat(body.getCode()).isEqualTo("050502");
            assertThat(body.getMessage()).startsWith("error.mission.recommended.");
        }
    }

    @Test
    @DisplayName("LUT-849 길드 미션 다시하기 — 길드원 아님 403 050603, 나머지는 400")
    void guildMissionRejoin_statuses() {
        MissionIdempotencyExceptionHandler handler = new MissionIdempotencyExceptionHandler();

        ResponseEntity<Object> notMember =
                handler.handleGuildMissionRejoin(GuildMissionRejoinException.notGuildMember());
        assertThat(notMember.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(((ApiResult<?>) notMember.getBody()).getCode()).isEqualTo("050603");
        assertThat(((ApiResult<?>) notMember.getBody()).getMessage())
                .isEqualTo("error.mission.rejoin.not_guild_member");

        java.util.Map<String, GuildMissionRejoinException> badRequests =
                java.util.Map.of(
                        "050601", GuildMissionRejoinException.notGuildMission(),
                        "050602", GuildMissionRejoinException.unavailable(),
                        "050604", GuildMissionRejoinException.alreadyCompleted());
        badRequests.forEach(
                (code, e) -> {
                    ResponseEntity<Object> res = handler.handleGuildMissionRejoin(e);
                    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    ApiResult<?> body = (ApiResult<?>) res.getBody();
                    assertThat(body.getCode()).isEqualTo(code);
                    assertThat(body.getMessage()).startsWith("error.mission.rejoin.");
                });
    }

    @Test
    @DisplayName("LUT-860 완료한 수행 취소 — 없음 404 050701, 완료 아님 400 050702, 오늘 아님 400 050703")
    void executionCancel_statuses() {
        MissionIdempotencyExceptionHandler handler = new MissionIdempotencyExceptionHandler();

        ResponseEntity<Object> notFound =
                handler.handleExecutionCancel(MissionExecutionCancelException.notFound());
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(((ApiResult<?>) notFound.getBody()).getCode()).isEqualTo("050701");
        assertThat(((ApiResult<?>) notFound.getBody()).getMessage())
                .isEqualTo("error.mission.cancel.not_found");

        ResponseEntity<Object> notCompleted =
                handler.handleExecutionCancel(MissionExecutionCancelException.notCompleted());
        assertThat(notCompleted.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((ApiResult<?>) notCompleted.getBody()).getCode()).isEqualTo("050702");
        assertThat(((ApiResult<?>) notCompleted.getBody()).getMessage())
                .isEqualTo("error.mission.cancel.not_completed");

        ResponseEntity<Object> notToday =
                handler.handleExecutionCancel(MissionExecutionCancelException.notToday());
        assertThat(notToday.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((ApiResult<?>) notToday.getBody()).getCode()).isEqualTo("050703");
        assertThat(((ApiResult<?>) notToday.getBody()).getMessage())
                .isEqualTo("error.mission.cancel.not_today");
    }

    @Test
    @DisplayName("LUT-860 완료한 수행 취소 — 메시지 키를 현지화해 돌려준다")
    void executionCancel_localized() {
        MissionIdempotencyExceptionHandler handler = new MissionIdempotencyExceptionHandler();
        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(
                        eq("error.mission.cancel.not_today"),
                        isNull(),
                        eq("error.mission.cancel.not_today"),
                        any()))
                .thenReturn("오늘 완료한 미션만 취소할 수 있어요.");
        ReflectionTestUtils.setField(handler, "messageSource", messageSource);

        ResponseEntity<Object> res =
                handler.handleExecutionCancel(MissionExecutionCancelException.notToday());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResult<?> body = (ApiResult<?>) res.getBody();
        assertThat(body.getCode()).isEqualTo("050703");
        assertThat(body.getMessage()).isEqualTo("오늘 완료한 미션만 취소할 수 있어요.");
    }

    @Test
    @DisplayName("LUT-860 MissionExecutionCancelException — 코드·상태·메시지 키")
    void executionCancelException_factories() {
        MissionExecutionCancelException notFound = MissionExecutionCancelException.notFound();
        assertThat(notFound.getCode()).isEqualTo(MissionExecutionCancelException.CODE_NOT_FOUND);
        assertThat(notFound.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.getMessage()).isEqualTo("error.mission.cancel.not_found");

        MissionExecutionCancelException notCompleted =
                MissionExecutionCancelException.notCompleted();
        assertThat(notCompleted.getCode())
                .isEqualTo(MissionExecutionCancelException.CODE_NOT_COMPLETED);
        assertThat(notCompleted.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        MissionExecutionCancelException notToday = MissionExecutionCancelException.notToday();
        assertThat(notToday.getCode()).isEqualTo(MissionExecutionCancelException.CODE_NOT_TODAY);
        assertThat(notToday.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
