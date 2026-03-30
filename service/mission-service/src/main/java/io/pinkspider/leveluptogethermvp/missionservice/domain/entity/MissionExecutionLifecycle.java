package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 미션 실행 라이프사이클 공통 인터페이스
 *
 * MissionExecution(일반 미션)과 DailyMissionInstance(고정 미션)의
 * 공통 상태 전이 로직을 통합하여 코드 중복을 제거합니다.
 *
 * Template Method 패턴을 적용하여:
 * - 공통 로직: default 메서드로 제공 (start, complete, skip 등)
 * - 엔티티별 로직: 훅 메서드로 오버라이드 (onComplete, shouldAutoComplete 등)
 * - 필수 구현: abstract 메서드 (calculateExpByDuration)
 */
public interface MissionExecutionLifecycle {

    long MINIMUM_EXECUTION_MINUTES = 1;
    long MAXIMUM_EXECUTION_MINUTES = 120;

    // === Accessors (Lombok @Getter/@Setter로 구현됨) ===

    ExecutionStatus getStatus();
    void setStatus(ExecutionStatus status);

    LocalDateTime getStartedAt();
    void setStartedAt(LocalDateTime startedAt);

    LocalDateTime getCompletedAt();
    void setCompletedAt(LocalDateTime completedAt);

    Integer getExpEarned();
    void setExpEarned(Integer expEarned);

    Boolean getIsSharedToFeed();
    void setIsSharedToFeed(Boolean isSharedToFeed);

    Boolean getIsAutoCompleted();
    void setIsAutoCompleted(Boolean isAutoCompleted);

    // === 상태 전이 (공통 default 구현) ===

    /**
     * 미션 수행 시작
     */
    default void start() {
        if (getStatus() == ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 수행 기록입니다.");
        }
        if (getStatus() == ExecutionStatus.MISSED) {
            throw new IllegalStateException("미실행 처리된 수행 기록은 시작할 수 없습니다.");
        }
        if (getStartedAt() != null) {
            throw new IllegalStateException("이미 시작된 수행 기록입니다.");
        }
        setStatus(ExecutionStatus.IN_PROGRESS);
        setStartedAt(LocalDateTime.now());
    }

    /**
     * 미션 수행 완료 및 경험치 계산
     *
     * Template Method: 공통 완료 로직 수행 후 onComplete() 훅 호출
     * - MissionExecution: onComplete() 기본 no-op
     * - DailyMissionInstance: completionCount, totalExpEarned 갱신
     */
    default void complete() {
        if (getStatus() == ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 수행 기록입니다.");
        }
        if (getStatus() == ExecutionStatus.MISSED) {
            throw new IllegalStateException("미실행 처리된 수행 기록은 완료할 수 없습니다.");
        }
        if (getStartedAt() == null) {
            throw new IllegalStateException("미션을 먼저 시작해야 합니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        long elapsedSeconds = Duration.between(getStartedAt(), now).getSeconds();
        if (elapsedSeconds / 60 < MINIMUM_EXECUTION_MINUTES) {
            throw new IllegalStateException(String.format(
                "최소 1분 이상 수행해야 완료할 수 있습니다. (시작: %s, 현재: %s, 경과: %d초)",
                getStartedAt(), now, elapsedSeconds));
        }

        setStatus(ExecutionStatus.COMPLETED);
        setCompletedAt(now);
        setExpEarned(calculateExpByDuration());
        onComplete();
    }

    /**
     * 진행 취소 (PENDING 상태로 되돌림)
     */
    default void skip() {
        if (getStatus() == ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 수행 기록은 취소할 수 없습니다.");
        }
        if (getStatus() == ExecutionStatus.MISSED) {
            throw new IllegalStateException("미실행 처리된 수행 기록은 취소할 수 없습니다.");
        }
        if (getStatus() == ExecutionStatus.IN_PROGRESS && getStartedAt() != null) {
            long elapsedSeconds = Duration.between(getStartedAt(), LocalDateTime.now()).getSeconds();
            if (elapsedSeconds >= MINIMUM_EXECUTION_MINUTES * 60) {
                throw new IllegalStateException("1분 이상 수행한 미션은 취소할 수 없습니다.");
            }
        }
        setStatus(ExecutionStatus.PENDING);
        setStartedAt(null);
    }

    /**
     * 미실행 처리
     */
    default void markAsMissed() {
        if (getStatus() == ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 수행 기록은 미실행 처리할 수 없습니다.");
        }
        setStatus(ExecutionStatus.MISSED);
    }

    /**
     * 피드에 공유
     */
    default void shareToFeed() {
        setIsSharedToFeed(true);
    }

    /**
     * 피드 공유 취소
     */
    default void unshareFromFeed() {
        setIsSharedToFeed(false);
    }

    /**
     * 수행 시작 후 경과 시간이 최대 수행 시간을 초과했는지 확인
     */
    default boolean isExpired() {
        if (getStatus() != ExecutionStatus.IN_PROGRESS || getStartedAt() == null) {
            return false;
        }
        long elapsedMinutes = Duration.between(getStartedAt(), LocalDateTime.now()).toMinutes();
        return elapsedMinutes >= MAXIMUM_EXECUTION_MINUTES;
    }

    /**
     * 2시간 초과 미션 자동 완료 (어뷰징 방지)
     *
     * Template Method:
     * - shouldAutoComplete(): DailyMissionInstance에서 targetDuration 체크
     * - onAutoComplete(): DailyMissionInstance에서 completionCount 갱신
     *
     * @param baseExp 기본 경험치 (2시간 초과 시 부여)
     * @return 자동 완료 처리 여부
     */
    default boolean autoCompleteIfExpired(int baseExp) {
        if (getStatus() != ExecutionStatus.IN_PROGRESS || getStartedAt() == null) {
            return false;
        }
        if (!shouldAutoComplete()) {
            return false;
        }

        long elapsedMinutes = Duration.between(getStartedAt(), LocalDateTime.now()).toMinutes();
        if (elapsedMinutes < MAXIMUM_EXECUTION_MINUTES) {
            return false;
        }

        setStatus(ExecutionStatus.COMPLETED);
        setCompletedAt(getStartedAt().plusMinutes(MAXIMUM_EXECUTION_MINUTES));
        setExpEarned(baseExp);
        setIsAutoCompleted(true);
        onAutoComplete();
        return true;
    }

    /**
     * 날짜 변경 시 자동 완료 (자정 스케줄러 safety net)
     *
     * @param baseExp 기본 경험치 (2시간 초과 시 부여)
     * @return 자동 완료 처리 여부
     */
    default boolean autoCompleteForDateChange(int baseExp) {
        if (getStatus() != ExecutionStatus.IN_PROGRESS || getStartedAt() == null) {
            return false;
        }

        setStatus(ExecutionStatus.COMPLETED);
        setCompletedAt(LocalDateTime.now());
        long elapsedMinutes = Duration.between(getStartedAt(), getCompletedAt()).toMinutes();
        setExpEarned(elapsedMinutes > MAXIMUM_EXECUTION_MINUTES ? baseExp : calculateExpByDuration());
        setIsAutoCompleted(true);
        onAutoComplete();
        return true;
    }

    // === 훅 메서드 (엔티티별 오버라이드) ===

    /**
     * 완료 후처리 훅
     * DailyMissionInstance: completionCount, totalExpEarned 갱신
     */
    default void onComplete() {}

    /**
     * 자동 완료 후처리 훅
     * DailyMissionInstance: completionCount, totalExpEarned 갱신
     */
    default void onAutoComplete() {}

    /**
     * 자동 완료 가능 여부 훅
     * DailyMissionInstance: targetDurationMinutes 설정 시 false 반환
     */
    default boolean shouldAutoComplete() {
        return true;
    }

    // === 필수 구현 (엔티티별 다른 로직) ===

    /**
     * 경험치 계산
     * - MissionExecution: 분당 1 EXP, 최대 480분
     * - DailyMissionInstance: 목표시간 기반 보너스 포함
     */
    int calculateExpByDuration();
}
