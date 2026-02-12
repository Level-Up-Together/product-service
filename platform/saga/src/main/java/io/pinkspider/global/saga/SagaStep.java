package io.pinkspider.global.saga;

import java.util.function.Predicate;

/**
 * Saga의 개별 단계를 정의하는 인터페이스
 *
 * @param <T> Saga Context 타입
 */
public interface SagaStep<T> {

    /**
     * 단계 이름
     */
    String getName();

    /**
     * 순방향 실행 (비즈니스 로직)
     *
     * @param context Saga 컨텍스트
     * @return 실행 결과
     */
    SagaStepResult execute(T context);

    /**
     * 보상 트랜잭션 (롤백 로직)
     *
     * @param context Saga 컨텍스트
     * @return 보상 실행 결과
     */
    SagaStepResult compensate(T context);

    /**
     * 이 단계를 실행할지 여부를 결정하는 조건
     * 기본값은 항상 실행
     *
     * @return 실행 조건 Predicate
     */
    default Predicate<T> shouldExecute() {
        return context -> true;
    }

    /**
     * 실패 시 전체 Saga를 중단할지 여부
     * false인 경우 실패해도 다음 단계 진행
     *
     * @return 필수 단계 여부
     */
    default boolean isMandatory() {
        return true;
    }

    /**
     * 재시도 횟수 (기본 0 = 재시도 없음)
     */
    default int getMaxRetries() {
        return 0;
    }

    /**
     * 재시도 간격 (밀리초)
     */
    default long getRetryDelayMs() {
        return 1000L;
    }
}
