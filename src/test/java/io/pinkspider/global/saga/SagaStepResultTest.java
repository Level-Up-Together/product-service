package io.pinkspider.global.saga;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SagaStepResult 단위 테스트")
class SagaStepResultTest {

    @Nested
    @DisplayName("success 팩토리 메서드 테스트")
    class SuccessTest {

        @Test
        @DisplayName("기본 성공 결과를 생성한다")
        void success_default() {
            // when
            SagaStepResult result = SagaStepResult.success();

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isNull();
            assertThat(result.getData()).isNull();
            assertThat(result.getException()).isNull();
        }

        @Test
        @DisplayName("메시지와 함께 성공 결과를 생성한다")
        void success_withMessage() {
            // given
            String message = "작업이 완료되었습니다";

            // when
            SagaStepResult result = SagaStepResult.success(message);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo(message);
            assertThat(result.getData()).isNull();
        }

        @Test
        @DisplayName("메시지와 데이터와 함께 성공 결과를 생성한다")
        void success_withMessageAndData() {
            // given
            String message = "경험치 지급 완료";
            Integer expAmount = 100;

            // when
            SagaStepResult result = SagaStepResult.success(message, expAmount);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo(message);
            assertThat(result.getData()).isEqualTo(expAmount);
        }
    }

    @Nested
    @DisplayName("failure 팩토리 메서드 테스트")
    class FailureTest {

        @Test
        @DisplayName("메시지와 함께 실패 결과를 생성한다")
        void failure_withMessage() {
            // given
            String message = "DB 연결 실패";

            // when
            SagaStepResult result = SagaStepResult.failure(message);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo(message);
            assertThat(result.getException()).isNull();
        }

        @Test
        @DisplayName("메시지와 예외와 함께 실패 결과를 생성한다")
        void failure_withMessageAndException() {
            // given
            String message = "트랜잭션 실패";
            RuntimeException exception = new RuntimeException("원인 예외");

            // when
            SagaStepResult result = SagaStepResult.failure(message, exception);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo(message);
            assertThat(result.getException()).isEqualTo(exception);
        }

        @Test
        @DisplayName("예외만으로 실패 결과를 생성한다")
        void failure_withException() {
            // given
            RuntimeException exception = new RuntimeException("데이터 처리 오류");

            // when
            SagaStepResult result = SagaStepResult.failure(exception);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("데이터 처리 오류");
            assertThat(result.getException()).isEqualTo(exception);
        }

        @Test
        @DisplayName("예외 메시지가 null이면 null 메시지를 가진다")
        void failure_withNullMessageException() {
            // given
            RuntimeException exception = new RuntimeException((String) null);

            // when
            SagaStepResult result = SagaStepResult.failure(exception);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isNull();
            assertThat(result.getException()).isEqualTo(exception);
        }
    }

    @Nested
    @DisplayName("Builder 테스트")
    class BuilderTest {

        @Test
        @DisplayName("Builder로 모든 필드를 설정할 수 있다")
        void builder_allFields() {
            // given
            String message = "커스텀 메시지";
            Object data = "커스텀 데이터";
            RuntimeException exception = new RuntimeException("커스텀 예외");

            // when
            SagaStepResult result = SagaStepResult.builder()
                .success(true)
                .message(message)
                .data(data)
                .exception(exception)
                .build();

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo(message);
            assertThat(result.getData()).isEqualTo(data);
            assertThat(result.getException()).isEqualTo(exception);
        }
    }
}
