# ADR-003: Saga Pattern for Distributed Transactions

**Date:** 2026-01-30
**Status:** Accepted — 2026-10-29 Persistence(`saga_db`) 부분 폐기, 맨 아래 "갱신 (2026-10-29)" 참고
**Deciders:** Backend Team

## Context

Multi-Service Monolith 아키텍처(ADR-001)에서 여러 서비스에 걸친 비즈니스 로직을 처리할 때, 각 서비스가 독립된 데이터베이스를 사용하므로 단일 트랜잭션으로 묶을 수 없습니다. 특히 미션 완료 시 여러 서비스의 데이터가 일관되게 변경되어야 합니다.

### 미션 완료 시 필요한 작업들
| Service | Database | Operation |
|---------|----------|-----------|
| MissionService | mission_db | 수행 기록 완료 처리 |
| UserService | user_db | 사용자 경험치 지급 |
| GuildService | guild_db | 길드 경험치 지급 |
| GamificationService | gamification_db | 통계/업적 업데이트 |
| FeedService | feed_db | 피드 생성 |

### 주요 요구사항
- 여러 서비스 간 데이터 일관성 보장
- 실패 시 자동 롤백 (보상 트랜잭션)
- 단계별 재시도 지원
- 실행 로그 및 모니터링
- 향후 MSA 전환 시 재사용 가능

## Decision

**Orchestration 기반 Saga Pattern**을 채택합니다.

중앙 오케스트레이터가 각 Step을 순차적으로 실행하고, 실패 시 완료된 Step들의 보상 트랜잭션을 역순으로 실행합니다.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Saga Orchestration Pattern                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                        SagaOrchestrator                                │ │
│  │                                                                        │ │
│  │  execute() ────▶ Step1 ──▶ Step2 ──▶ Step3 ──▶ Step4 ──▶ Complete    │ │
│  │                   │         │         │ ✗                             │ │
│  │                   ▼         ▼         │                               │ │
│  │  compensate() ◀─ C(1) ◀── C(2) ◀────┘  (역순 보상)                   │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │  Step 1  │  │  Step 2  │  │  Step 3  │  │  Step 4  │  │  Step N  │      │
│  │ (mission)│  │  (user)  │  │ (guild)  │  │ (gamifi) │  │  (feed)  │      │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘      │
│       │             │             │             │             │             │
│  ┌────▼─────┐  ┌────▼─────┐  ┌────▼─────┐  ┌────▼─────┐  ┌────▼─────┐      │
│  │mission_db│  │ user_db  │  │ guild_db │  │gamifi_db │  │ feed_db  │      │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Core Components

#### 1. SagaContext
Saga 실행 중 공유되는 상태와 데이터를 담는 컨텍스트

```java
public abstract class SagaContext {
    private final String sagaId;
    private final String sagaType;
    private SagaStatus status;
    private Map<String, SagaStepResult> stepResults;
    private Map<String, Object> compensationData;  // 보상 트랜잭션용 데이터

    // Status: CREATED → PROCESSING → COMPLETED | FAILED | COMPENSATED
}
```

#### 2. SagaStep Interface
각 단계의 실행과 보상을 정의하는 인터페이스

```java
public interface SagaStep<T extends SagaContext> {
    String getName();
    boolean isMandatory();           // 필수 단계 여부
    int getMaxRetries();             // 최대 재시도 횟수
    long getRetryDelayMs();          // 재시도 지연 시간
    Predicate<T> shouldExecute();    // 실행 조건

    SagaStepResult execute(T context);     // 실행 로직
    SagaStepResult compensate(T context);  // 보상 로직
}
```

#### 3. SagaOrchestrator
Saga 실행을 조율하는 오케스트레이터

```java
public class SagaOrchestrator<T extends SagaContext> {

    public SagaResult<T> execute(T context) {
        List<SagaStep<T>> completedSteps = new ArrayList<>();

        for (SagaStep<T> step : steps) {
            // 실행 조건 확인
            if (!step.shouldExecute().test(context)) {
                continue;  // 스킵
            }

            // Step 실행 (재시도 포함)
            SagaStepResult result = executeStepWithRetry(context, step);

            if (result.isSuccess()) {
                completedSteps.add(step);
            } else if (step.isMandatory()) {
                // 필수 단계 실패 → 보상 트랜잭션
                compensate(context, completedSteps);
                return SagaResult.failure(context, result.getMessage());
            }
            // 선택적 단계는 실패해도 계속 진행
        }

        return SagaResult.success(context);
    }

    private void compensate(T context, List<SagaStep<T>> completedSteps) {
        // 완료된 Step들을 역순으로 보상
        Collections.reverse(completedSteps);
        for (SagaStep<T> step : completedSteps) {
            step.compensate(context);
        }
    }
}
```

### Implementation Example: MissionCompletionSaga

#### Saga Definition
```java
@Component
@RequiredArgsConstructor
public class MissionCompletionSaga {

    private final LoadMissionDataStep loadMissionDataStep;
    private final CompleteExecutionStep completeExecutionStep;
    private final GrantUserExperienceStep grantUserExperienceStep;
    private final GrantGuildExperienceStep grantGuildExperienceStep;
    private final UpdateParticipantProgressStep updateParticipantProgressStep;
    private final UpdateUserStatsStep updateUserStatsStep;
    private final CreateFeedFromMissionStep createFeedFromMissionStep;

    public SagaResult<MissionCompletionContext> execute(
            Long executionId, String userId, String note, boolean shareToFeed) {

        MissionCompletionContext context =
            new MissionCompletionContext(executionId, userId, note, shareToFeed);

        SagaOrchestrator<MissionCompletionContext> orchestrator = new SagaOrchestrator<>();

        orchestrator
            .addStep(loadMissionDataStep)           // 1. 데이터 로드 (필수)
            .addStep(completeExecutionStep)         // 2. 수행 완료 (필수)
            .addStep(grantUserExperienceStep)       // 3. 사용자 EXP (필수)
            .addStep(grantGuildExperienceStep)      // 4. 길드 EXP (조건부)
            .addStep(updateParticipantProgressStep) // 5. 참가자 진행도 (필수)
            .addStep(updateUserStatsStep)           // 6. 통계/업적 (선택적)
            .addStep(createFeedFromMissionStep);    // 7. 피드 생성 (선택적)

        return orchestrator.execute(context);
    }
}
```

#### Step Implementation with Compensation
```java
@Component
@RequiredArgsConstructor
public class GrantUserExperienceStep implements SagaStep<MissionCompletionContext> {

    private final UserExperienceService userExperienceService;

    @Override
    public String getName() { return "GrantUserExperience"; }

    @Override
    public int getMaxRetries() { return 2; }  // 2회 재시도

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SagaStepResult execute(MissionCompletionContext context) {
        String userId = context.getUserId();
        int expToGrant = context.getUserExpEarned();

        // 보상용 현재 상태 저장
        UserExperience current = userExperienceService.getOrCreateUserExperience(userId);
        context.addCompensationData("USER_EXP_BEFORE", current.getCurrentExp());
        context.addCompensationData("USER_LEVEL_BEFORE", current.getCurrentLevel());

        // 경험치 지급
        userExperienceService.addExperience(userId, expToGrant, ExpSourceType.MISSION_EXECUTION, ...);

        return SagaStepResult.success("사용자 경험치 지급 완료", expToGrant);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SagaStepResult compensate(MissionCompletionContext context) {
        String userId = context.getUserId();
        int expGranted = context.getUserExpEarned();

        // 지급한 경험치 차감 (롤백)
        userExperienceService.subtractExperience(userId, expGranted, ExpSourceType.MISSION_EXECUTION, ...);

        return SagaStepResult.success("사용자 경험치 환수 완료");
    }
}
```

#### Conditional Step Execution
```java
@Component
public class GrantGuildExperienceStep implements SagaStep<MissionCompletionContext> {

    @Override
    public Predicate<MissionCompletionContext> shouldExecute() {
        // 길드 미션인 경우에만 실행
        return context -> context.getMission().getGuildId() != null;
    }

    @Override
    public boolean isMandatory() {
        return true;  // 실행되면 필수
    }
    // ...
}
```

### Saga Execution Flow

```
MissionCompletionSaga.execute()
│
├─▶ [1] LoadMissionDataStep
│       ├─ execute(): 미션/수행 데이터 로드
│       └─ compensate(): (데이터만 읽으므로 보상 불필요)
│
├─▶ [2] CompleteExecutionStep
│       ├─ execute(): status → COMPLETED
│       └─ compensate(): status → IN_PROGRESS (롤백)
│
├─▶ [3] GrantUserExperienceStep
│       ├─ execute(): exp += earned
│       └─ compensate(): exp -= earned
│
├─▶ [4] GrantGuildExperienceStep (조건: guildId != null)
│       ├─ execute(): guildExp += earned
│       └─ compensate(): guildExp -= earned
│
├─▶ [5] UpdateParticipantProgressStep
│       ├─ execute(): completedCount++
│       └─ compensate(): completedCount--
│
├─▶ [6] UpdateUserStatsStep (선택적, mandatory=false)
│       ├─ execute(): 통계/업적 업데이트
│       └─ compensate(): 통계/업적 롤백
│
└─▶ [7] CreateFeedFromMissionStep (조건: shareToFeed=true, 선택적)
        ├─ execute(): 피드 생성
        └─ compensate(): 피드 삭제
```

### Saga Status Flow

```
       ┌──────────────────────────────────────────────────────────────┐
       │                                                              │
       ▼                                                              │
   ┌────────┐     ┌────────────┐     ┌───────────┐                   │
   │CREATED │────▶│ PROCESSING │────▶│ COMPLETED │                   │
   └────────┘     └─────┬──────┘     └───────────┘                   │
                        │                                             │
                        │ (Step 실패)                                 │
                        ▼                                             │
                  ┌───────────┐     ┌─────────────┐                  │
                  │  FAILED   │────▶│ COMPENSATING│──────────────────┘
                  └───────────┘     └──────┬──────┘
                                           │
                                           ▼
                                    ┌─────────────┐
                                    │ COMPENSATED │
                                    └─────────────┘
```

### Step Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `mandatory` | `true` | 필수 단계 여부. false면 실패해도 계속 진행 |
| `maxRetries` | `0` | 최대 재시도 횟수 |
| `retryDelayMs` | `1000` | 재시도 간 지연 시간 (ms) |
| `shouldExecute` | `always` | 실행 조건 (Predicate) |

### Persistence (Optional)

saga_db에 실행 이력을 저장하여 모니터링 및 수동 복구 지원

```java
@Entity
@Table(name = "saga_instance")
public class SagaInstance {
    @Id private String sagaId;
    private String sagaType;
    private SagaStatus status;
    private String contextJson;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}

@Entity
@Table(name = "saga_step_log")
public class SagaStepLog {
    @Id @GeneratedValue private Long id;
    private String sagaId;
    private String stepName;
    private SagaStepStatus status;
    private Long durationMs;
    private String resultMessage;
    private LocalDateTime executedAt;
}
```

## Consequences

### Positive
- **데이터 일관성**: 보상 트랜잭션으로 실패 시 일관성 유지
- **가시성**: 중앙 오케스트레이터로 흐름 추적 용이
- **유연성**: Step별 재시도, 조건부 실행, 선택적 실행 지원
- **테스트 용이**: Step별 독립 테스트 가능
- **MSA 대비**: Kafka 기반 분산 Saga로 전환 용이

### Negative
- **복잡도**: 보상 로직 구현 필요
- **보상 실패**: 보상 트랜잭션 실패 시 수동 개입 필요
- **성능**: 순차 실행으로 지연 발생 가능
- **디버깅**: 여러 DB에 걸친 상태 추적 어려움

### Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| 보상 실패 | 로깅 + 알림 + 관리자 수동 처리 |
| 중복 실행 | 멱등성 보장 (idempotency key) |
| 긴 실행 시간 | 타임아웃 설정, 비동기 처리 검토 |
| 데이터 불일치 | 주기적 일관성 체크 배치 |

## Alternatives Considered

### 1. Choreography-based Saga
```
Step1 ──event──▶ Step2 ──event──▶ Step3
```
- **Pros**: 분산, 독립적
- **Cons**: 흐름 추적 어려움, 순환 의존 위험
- **Rejected**: 복잡한 흐름 관리 어려움

### 2. Two-Phase Commit (2PC)
- **Pros**: 강한 일관성
- **Cons**: 단일 장애점, 잠금 오버헤드
- **Rejected**: 분산 DB 환경에서 비현실적

### 3. Eventual Consistency without Saga
- **Pros**: 단순함
- **Cons**: 수동 롤백, 일관성 보장 어려움
- **Rejected**: 비즈니스 요구사항 충족 불가

## Future Considerations

### Async Saga Execution
긴 실행 시간 Saga를 비동기로 처리:
```java
@Async
public CompletableFuture<SagaResult<T>> executeAsync(T context) {
    return CompletableFuture.completedFuture(execute(context));
}
```

### Kafka-based Distributed Saga
MSA 전환 시:
```java
// Before: In-process Orchestrator
orchestrator.addStep(grantUserExperienceStep);

// After: Kafka Command/Reply
kafkaTemplate.send("user-service.grant-exp.command", command);
// Listen for "user-service.grant-exp.reply"
```

## References

- [Saga Pattern](https://microservices.io/patterns/data/saga.html)
- [Implementing Microservices: Saga Pattern](https://www.baeldung.com/cs/saga-pattern-microservices)
- [Managing data consistency in a microservice architecture using Sagas](https://chrisrichardson.net/post/microservices/2019/07/09/developing-sagas-part-1.html)

## Related ADRs

- [ADR-001: Multi-Service Monolith Architecture](./ADR-001-20260130.md)
- [ADR-002: Event-Driven Cross-Service Communication](./ADR-002-20260130.md)
- (Future) ADR-004: BFF API Aggregation Strategy

## 갱신 (2026-10-29) — Persistence(`saga_db`) 폐기

**결정:** Saga 실행 이력 저장(`saga_db`, `saga_instance`·`saga_step_log`)은 구현하지 않고 정리한다. Orchestration Saga 자체는 유지한다.

**확인한 사실**
- 저장 코드는 처음부터 없었다. 2025-12-07(`2811e5ef`·`1dbaebcc`)에 표·DataSource·Repository 만 만들었고, `SagaOrchestrator` 는 실행 로그를 메모리(`List<SagaExecutionLog>`)에만 쌓았다. Repository 를 부르는 코드는 한 번도 없었다.
- 2026-02-13 platform 이전(`e1ddd403`)에서 `SagaDataSourceConfig` 가 빠졌다. 그 뒤 서버는 `saga_db` 에 접속하지 않는다.
- prod `saga_db`: 표 2개 0행, `n_tup_ins` 0, 다른 접속 0. dev `saga_db`: 표 9개(saga 2개 + 다른 서비스의 빈 표 7개) 모두 0행.

**지금 동작 (위 본문과 다른 점)**
- `MissionCompletionSaga` 는 같은 프로세스 안에서 Facade 메서드를 순서대로 호출한다. 각 step 은 자기 서비스 트랜잭션 매니저로 따로 커밋하고, 필수 step 실패 시 앞 step 을 역순 보상한다.
- 필수가 아닌 step(미션북 다이아·통계/업적·피드)은 응답 뒤 별도 실행기(`sagaTailExecutor`)에서 돈다. 실패는 로그만 남는다.
- 실행 상태를 저장하지 않으므로 **요청 처리 중 서버가 종료되면 일부 step 만 반영된 채 남고, 재개·보상할 기록이 없다.** 보상 실패도 로그만 남는다(알림·수동 처리 경로 없음).
- "Kafka 기반 분산 Saga"(Future Considerations)는 구현하지 않았다. Kafka 도 쓰지 않는다.

**이유**
- 단일 배포(Multi-Service Monolith)에서는 미션 완료 요청이 짧아 중간 종료 위험이 작다.
- 실제 MSA 전환 때는 실행 이력 표보다 서비스 사이 메시지 전달(outbox·브로커)과 재시도 설계가 먼저다. 그때 Saga 상태 저장을 함께 다시 설계한다.

**정리 범위:** product 테스트 설정·문서, platform `saga/persistence`·`SagaDataSourceProperties`, config-repository `spring.datasource.saga`, dev·prod `saga_db`(백업 후 삭제).
