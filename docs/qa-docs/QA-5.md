# QA-5: 고정미션 미션상세등록 실패 — 근본 원인 분석 및 미션 라이프사이클 개선안

> Jira: https://pink-spider.atlassian.net/browse/QA-5
> 작성일: 2026-03-12
> 상태: 분석 완료, 수정 대기

---

## 1. 이슈 요약

**증상**: 고정미션 완료 후 같은 미션을 다시 수행하면, 이전 완료된 미션의 상세(노트/이미지) 등록이 실패한다.

**재현 순서**:
1. 고정미션 수행 시작
2. 26분 수행 후 종료 (seq=1, COMPLETED)
3. 같은 고정미션 다시 수행 시작 (seq=2, IN_PROGRESS)
4. 이전 26분짜리 미션의 상세(노트)를 등록하려 하면 오류 발생

**오류 메시지**:
- "완료된 미션만 기록을 추가할 수 있습니다" (IN_PROGRESS 인스턴스를 잘못 조회)
- `{"code":"000710","message":"해당 날짜의 수행 기록을 찾을 수 없습니다: 2026-02-28"}` (날짜 불일치)

---

## 2. 근본 원인

### 2.1 버그 위치

`DailyMissionInstanceService.findActiveInstanceByParticipant()` (라인 460-470)

```java
private DailyMissionInstance findActiveInstanceByParticipant(Long participantId, LocalDate date) {
    return instanceRepository.findInProgressByParticipantIdAndDate(participantId, date)
        .orElseGet(() -> {
            List<DailyMissionInstance> instances = instanceRepository
                .findByParticipantIdAndInstanceDateOrderBySequenceDesc(participantId, date);
            if (instances.isEmpty()) {
                throw new IllegalArgumentException("해당 날짜의 인스턴스를 찾을 수 없습니다: " + date);
            }
            return instances.get(0); // ← 항상 최신 시퀀스 반환
        });
}
```

### 2.2 버그 메커니즘

이 메서드의 조회 우선순위:
1. **IN_PROGRESS** 인스턴스가 있으면 → 그것을 반환
2. 없으면 → **시퀀스 역순 정렬의 첫 번째** (= 가장 최신 인스턴스) 반환

**문제**: 노트 업데이트, 이미지 업로드, 피드 공유 등 **후처리 작업**은 COMPLETED 인스턴스를 대상으로 해야 하는데,
이 메서드는 항상 IN_PROGRESS 또는 최신 시퀀스(PENDING일 수 있음)를 반환한다.

### 2.3 버그 재현 데이터 흐름

```
시간순서:
┌─────────────────────────────────────────────────────────────────┐
│ T1: 미션 완료                                                    │
│     seq=1 → COMPLETED (26분 수행)                                │
│     Saga: CreateNextPinnedInstanceStep → seq=2 PENDING 생성      │
│                                                                  │
│ T2: 같은 미션 다시 시작                                            │
│     seq=2 → IN_PROGRESS                                         │
│                                                                  │
│ T3: 이전 완료 미션(seq=1)에 노트 등록 시도                          │
│     → findActiveInstanceByParticipant() 호출                     │
│     → IN_PROGRESS 우선 → seq=2 반환 (IN_PROGRESS)                │
│     → updateNoteByMission: status != COMPLETED 체크 실패!         │
│     → "완료된 미션만 기록을 추가할 수 있습니다" 오류                    │
└─────────────────────────────────────────────────────────────────┘
```

### 2.4 영향 범위

`findActiveInstanceByParticipant()`를 사용하는 **모든 메서드**가 동일한 버그에 노출:

| 메서드 | 기능 | 영향 |
|--------|------|------|
| `updateNoteByMission()` | 노트 업데이트 | COMPLETED 대상이어야 하나 IN_PROGRESS 반환 |
| `shareToFeedByMission()` | 피드 공유 | COMPLETED 대상이어야 하나 IN_PROGRESS 반환 |
| `unshareFromFeedByMission()` | 피드 공유 취소 | COMPLETED 대상이어야 하나 IN_PROGRESS 반환 |
| `uploadImageByMission()` | 이미지 업로드 | COMPLETED 대상이어야 하나 IN_PROGRESS 반환 |
| `deleteImageByMission()` | 이미지 삭제 | COMPLETED 대상이어야 하나 IN_PROGRESS 반환 |
| `getInstanceByMission()` | 인스턴스 조회 | 상황에 따라 다른 인스턴스가 필요함 |

---

## 3. 구조적 문제 (반복 버그의 근본 원인)

QA-5와 같은 유형의 버그가 반복 발생하는 이유는 미션 시스템에 구조적 문제가 있기 때문이다.

### 3.1 API 식별키 설계 결함

현재 API: `PATCH /{missionId}/executions/{executionDate}/note`

| 미션 타입 | (participantId, date) 관계 | 식별 가능? |
|-----------|--------------------------|-----------|
| 일반 미션 | 1:1 (UniqueConstraint) | O |
| **고정 미션** | **1:N** (같은 날 여러 인스턴스) | **X** |

고정 미션은 같은 날 여러 인스턴스(seq=1 COMPLETED, seq=2 IN_PROGRESS, ...)가 존재할 수 있으므로,
`(missionId, date)` 조합으로는 **어떤 인스턴스를 대상으로 하는지 특정할 수 없다**.

### 3.2 단일 조회 메서드로 모든 작업 처리

`findActiveInstanceByParticipant()` 하나로 성격이 다른 6가지 작업을 처리한다.
각 작업이 필요로 하는 대상 인스턴스가 다름:

| 작업 유형 | 대상 인스턴스 |
|-----------|-------------|
| start | PENDING (없으면 새로 생성) |
| skip / complete | IN_PROGRESS |
| note / image / share / unshare | **가장 최근 COMPLETED** |

### 3.3 MissionExecution ↔ DailyMissionInstance 비즈니스 로직 중복

두 엔티티에 거의 동일한 메서드가 복사되어 있어 한쪽만 수정하면 다른쪽에 버그가 잔존한다.

| 중복 메서드 | MissionExecution | DailyMissionInstance |
|------------|------------------|---------------------|
| `start()` | O | O |
| `complete()` | O | O |
| `skip()` | O | O |
| `calculateExpByDuration()` | O (단순 분당 1EXP) | O (목표시간 기반 보너스 포함) |
| `autoCompleteIfExpired()` | O | O |
| `autoCompleteForDateChange()` | O | O |
| `markAsMissed()` | O | O |
| `shareToFeed()` / `unshareFromFeed()` | O | O |

**차이점**: DailyMissionInstance만 `targetDurationMinutes` 기반 EXP 계산, `completionCount`/`totalExpEarned` 누적,
`resetToPending()` 지원.

### 3.4 잠재적 동시성 이슈

- 완료 버튼 빠른 이중 클릭 → Saga 중복 실행 → 경험치 이중 지급 가능
- 자동 종료 스케줄러 + 사용자 수동 완료 동시 발생 → 충돌 가능
- `@Version` 낙관적 락은 있으나 retry 로직 없음 → `OptimisticLockException` 사용자 노출

---

## 4. 해결 방안

### Phase 1: 즉시 수정 — 작업별 인스턴스 조회 분리 [HIGH]

**목표**: `findActiveInstanceByParticipant()` 단일 메서드를 작업 목적에 맞게 분리

**변경 파일**: `DailyMissionInstanceService.java`

```java
// AS-IS: 하나의 메서드로 모든 작업 처리
private DailyMissionInstance findActiveInstanceByParticipant(Long participantId, LocalDate date)

// TO-BE: 작업별 조회 메서드 분리

/**
 * 후처리 작업용 (note, image, share): 가장 최근 COMPLETED 인스턴스
 */
private DailyMissionInstance findLatestCompletedInstance(Long participantId, LocalDate date) {
    List<DailyMissionInstance> instances = instanceRepository
        .findByParticipantIdAndInstanceDateOrderBySequenceDesc(participantId, date);
    return instances.stream()
        .filter(i -> i.getStatus() == ExecutionStatus.COMPLETED)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            "해당 날짜의 완료된 인스턴스를 찾을 수 없습니다: " + date));
}

/**
 * 조회용: 우선순위 = IN_PROGRESS > COMPLETED(최신) > PENDING(최신)
 */
private DailyMissionInstance findBestMatchInstance(Long participantId, LocalDate date) {
    return instanceRepository.findInProgressByParticipantIdAndDate(participantId, date)
        .orElseGet(() -> {
            List<DailyMissionInstance> instances = instanceRepository
                .findByParticipantIdAndInstanceDateOrderBySequenceDesc(participantId, date);
            if (instances.isEmpty()) {
                throw new IllegalArgumentException("해당 날짜의 인스턴스를 찾을 수 없습니다: " + date);
            }
            // COMPLETED 우선, 없으면 최신 시퀀스
            return instances.stream()
                .filter(i -> i.getStatus() == ExecutionStatus.COMPLETED)
                .findFirst()
                .orElse(instances.get(0));
        });
}
```

**적용 매핑**:

| 메서드 | 변경 전 | 변경 후 |
|--------|---------|---------|
| `updateNoteByMission()` | `findActiveInstanceByParticipant` | `findLatestCompletedInstance` |
| `uploadImageByMission()` | `findActiveInstanceByParticipant` | `findLatestCompletedInstance` |
| `deleteImageByMission()` | `findActiveInstanceByParticipant` | `findLatestCompletedInstance` |
| `shareToFeedByMission()` | `findActiveInstanceByParticipant` | `findLatestCompletedInstance` |
| `unshareFromFeedByMission()` | `findActiveInstanceByParticipant` | `findLatestCompletedInstance` |
| `getInstanceByMission()` | `findActiveInstanceByParticipant` | `findBestMatchInstance` |

**예상 효과**: QA-5 및 동일 유형 버그 6건 일괄 해결

---

### Phase 2: instanceId 기반 API 지원 [MEDIUM]

**목표**: 프론트엔드가 특정 인스턴스를 정확히 지목할 수 있는 API 추가

**변경 범위**: Controller, Strategy, Service

```
# 기존 API (하위 호환 유지)
PATCH /{missionId}/executions/{date}/note?note=...

# 개선 API (instanceId로 정확한 타겟팅)
PATCH /{missionId}/executions/{date}/note?note=...&instanceId=123
```

**작동 방식**:
- `instanceId` 있으면 → `findInstanceById(instanceId)` 직접 조회 (정확)
- `instanceId` 없으면 → Phase 1의 smart lookup (하위 호환)

**영향받는 엔드포인트** (6개):
- `PATCH /{missionId}/executions/{date}/note`
- `POST /{missionId}/executions/{date}/image`
- `DELETE /{missionId}/executions/{date}/image`
- `POST /{missionId}/executions/{date}/share`
- `DELETE /{missionId}/executions/{date}/share`
- `GET /{missionId}/executions/{date}`

---

### Phase 3: 비즈니스 로직 통합 (중복 제거) [LOW]

**목표**: MissionExecution과 DailyMissionInstance의 중복 비즈니스 로직 통합

**방안 A — `@Embeddable` 추출**:

```java
@Embeddable
public class ExecutionLifecycle {
    private ExecutionStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer expEarned;
    private Boolean isSharedToFeed;
    private Boolean isAutoCompleted;

    public void start() { ... }
    public void complete(ExpCalculator calculator) { ... }
    public void skip() { ... }
    public int calculateExpByDuration(ExpCalculator calculator) { ... }
    public boolean autoCompleteIfExpired(int baseExp) { ... }
}
```

**방안 B — 유틸리티 클래스 추출** (엔티티 구조 변경 최소화):

```java
public class ExecutionLifecycleHelper {
    public static void start(ExecutionStatus currentStatus, LocalDateTime startedAt) { ... }
    public static CompletionResult complete(LocalDateTime startedAt, Integer targetMinutes, ...) { ... }
    // ...
}
```

**방안 A 권장**: DB 스키마 변경 없이 JPA `@Embeddable`로 코드 레벨에서만 통합 가능.
단, 양쪽 엔티티의 컬럼명이 동일해야 하므로 `@AttributeOverride` 필요할 수 있음.

---

### Phase 4: 방어적 프로그래밍 강화 [LOW]

| 항목 | 현재 | 개선 |
|------|------|------|
| 낙관적 락 실패 | 예외 그대로 노출 | Retry + 사용자 친화적 메시지 |
| 상태 전이 검증 | 각 메서드에 if문 분산 | State Machine 패턴 또는 `canTransitTo()` 메서드 |
| 중복 완료 방지 | `@Version`만 의존 | 멱등성 키 도입 (requestId) |
| 에러 메시지 | `IllegalStateException` 직접 throw | `CustomException` + 에러코드 표준화 |

---

## 5. 구현 우선순위

| 순서 | Phase | 영향도 | 작업량 | 비고 |
|------|-------|--------|--------|------|
| 1 | Phase 1 | QA-5 + 동일 버그 6건 해결 | 소 (1개 파일) | 즉시 적용 가능 |
| 2 | Phase 2 | 향후 유사 버그 원천 차단 | 중 (Controller + Strategy + Service) | 프론트 배포와 병행 |
| 3 | Phase 3 | 유지보수성 향상 | 대 (엔티티 2개 + 서비스 리팩터링) | 안정화 후 진행 |
| 4 | Phase 4 | 안정성 강화 | 중 | 안정화 후 진행 |

---

## 6. 관련 파일

| 파일 | 역할 |
|------|------|
| `mission-service/.../application/DailyMissionInstanceService.java` | **버그 위치** — findActiveInstanceByParticipant |
| `mission-service/.../application/strategy/PinnedMissionExecutionStrategy.java` | Strategy 구현 (고정 미션) |
| `mission-service/.../application/strategy/RegularMissionExecutionStrategy.java` | Strategy 구현 (일반 미션) |
| `mission-service/.../application/strategy/MissionExecutionStrategy.java` | Strategy 인터페이스 (9개 메서드) |
| `mission-service/.../application/strategy/MissionExecutionStrategyResolver.java` | Strategy 선택 (isPinned 기반) |
| `mission-service/.../application/MissionExecutionService.java` | 라우터 (Strategy로 위임) |
| `mission-service/.../domain/entity/DailyMissionInstance.java` | 고정 미션 인스턴스 엔티티 |
| `mission-service/.../domain/entity/MissionExecution.java` | 일반 미션 실행 엔티티 |
| `mission-service/.../infrastructure/DailyMissionInstanceRepository.java` | 고정 미션 Repository |
| `mission-service/.../api/MissionExecutionController.java` | REST API 컨트롤러 |

## 7. 관련 QA 이슈 / 커밋 이력

| 커밋 | 내용 |
|------|------|
| `b057dbf` | (feature/QA-5) updateExecutionNote, unshareExecutionFromFeed를 Strategy 패턴으로 라우팅 |
| `d80ef54` | NonUniqueResultException 해결 (고정미션 인스턴스 조회) |
| `643bddf` | 자정 스케줄러에서 IN_PROGRESS 고정미션 보존 |
| `2d46a06` | 자정에 IN_PROGRESS 미션 자동 완료 (MISSED 대신) |
| `4953310` | (feature/QA-12) 완료된 일반 미션이 다음날 다시 나타나는 버그 수정 |
| `9447600` | (feature/QA-8) 미션 종료 로직 개선 — 2시간 기준 EXP 차등 지급 |