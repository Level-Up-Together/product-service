package io.pinkspider.global.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 기본 Saga 이벤트 발행자
 *
 * Spring ApplicationEventPublisher를 사용한 동기 이벤트 발행
 * MSA 전환 시 Kafka 기반 구현체로 교체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSagaEventPublisher implements SagaEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishSagaCompleted(SagaContext context) {
        log.debug("[Saga:{}] Publishing saga completed event", context.getSagaId());
        applicationEventPublisher.publishEvent(
            new SagaCompletedEvent(this, context));
    }

    @Override
    public void publishSagaCompensated(SagaContext context) {
        log.debug("[Saga:{}] Publishing saga compensated event", context.getSagaId());
        applicationEventPublisher.publishEvent(
            new SagaCompensatedEvent(this, context));
    }

    @Override
    public void publishSagaFailed(SagaContext context) {
        log.debug("[Saga:{}] Publishing saga failed event", context.getSagaId());
        applicationEventPublisher.publishEvent(
            new SagaFailedEvent(this, context));
    }

    @Override
    public void publishSagaEvent(String eventType, SagaContext context, Object payload) {
        log.debug("[Saga:{}] Publishing custom event: {}", context.getSagaId(), eventType);
        applicationEventPublisher.publishEvent(
            new SagaCustomEvent(this, eventType, context, payload));
    }

    /**
     * Saga 완료 이벤트
     */
    public static class SagaCompletedEvent extends SagaEvent {
        public SagaCompletedEvent(Object source, SagaContext context) {
            super(source, "SAGA_COMPLETED", context, null);
        }
    }

    /**
     * Saga 보상 완료 이벤트
     */
    public static class SagaCompensatedEvent extends SagaEvent {
        public SagaCompensatedEvent(Object source, SagaContext context) {
            super(source, "SAGA_COMPENSATED", context, null);
        }
    }

    /**
     * Saga 실패 이벤트
     */
    public static class SagaFailedEvent extends SagaEvent {
        public SagaFailedEvent(Object source, SagaContext context) {
            super(source, "SAGA_FAILED", context, null);
        }
    }

    /**
     * 커스텀 Saga 이벤트
     */
    public static class SagaCustomEvent extends SagaEvent {
        public SagaCustomEvent(Object source, String eventType, SagaContext context, Object payload) {
            super(source, eventType, context, payload);
        }
    }
}
