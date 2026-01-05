package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 모든 도메인 이벤트의 기본 인터페이스
 */
public interface DomainEvent {
    /**
     * 이벤트 발생 시간
     */
    LocalDateTime occurredAt();

    /**
     * 이벤트를 발생시킨 사용자 ID
     */
    String userId();
}
