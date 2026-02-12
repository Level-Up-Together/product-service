package io.pinkspider.global.saga;

/**
 * Saga 이벤트 발행 인터페이스
 *
 * MSA 전환 시 Kafka 등의 메시지 브로커로 구현 교체 가능
 */
public interface SagaEventPublisher {

    /**
     * Saga 완료 이벤트 발행
     */
    void publishSagaCompleted(SagaContext context);

    /**
     * Saga 보상 완료 이벤트 발행
     */
    void publishSagaCompensated(SagaContext context);

    /**
     * Saga 실패 이벤트 발행
     */
    void publishSagaFailed(SagaContext context);

    /**
     * 커스텀 Saga 이벤트 발행
     */
    void publishSagaEvent(String eventType, SagaContext context, Object payload);
}
