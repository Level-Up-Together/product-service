package io.pinkspider.global.saga;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Saga 이벤트 기본 클래스
 */
@Getter
public abstract class SagaEvent extends ApplicationEvent {

    private final String eventType;
    private final SagaContext context;
    private final Object payload;

    protected SagaEvent(Object source, String eventType, SagaContext context, Object payload) {
        super(source);
        this.eventType = eventType;
        this.context = context;
        this.payload = payload;
    }

    public String getSagaId() {
        return context != null ? context.getSagaId() : null;
    }

    public String getSagaType() {
        return context != null ? context.getSagaType() : null;
    }

    public SagaStatus getSagaStatus() {
        return context != null ? context.getStatus() : null;
    }
}
