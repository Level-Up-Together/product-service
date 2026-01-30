# ADR-002: Event-Driven Cross-Service Communication

**Date:** 2026-01-30
**Status:** Accepted
**Deciders:** Backend Team

## Context

Multi-Service Monolith 아키텍처(ADR-001)에서 서비스 간 통신 방식을 결정해야 합니다. 각 서비스는 독립된 데이터베이스를 사용하므로, 서비스 간 데이터 동기화와 비즈니스 로직 연계가
필요합니다.

### 주요 요구사항

- 서비스 간 느슨한 결합 (Loose Coupling)
- 트랜잭션 경계 명확화
- 데이터 일관성 보장
- 확장성 (새로운 리스너 추가 용이)
- 향후 MSA 전환 시 Kafka 기반으로 쉽게 전환

### 서비스 간 통신이 필요한 케이스

| Source Service      | Target Service      | Use Case          |
|---------------------|---------------------|-------------------|
| MissionService      | GamificationService | 미션 완료 시 경험치/업적 지급 |
| MissionService      | FeedService         | 미션 완료 시 피드 생성     |
| GuildService        | NotificationService | 길드 가입/초대 시 알림 발송  |
| GuildService        | GamificationService | 길드 가입 시 업적 체크     |
| FriendService       | NotificationService | 친구 요청/수락 시 알림 발송  |
| GamificationService | NotificationService | 칭호/업적 획득 시 알림 발송  |

## Decision

**Spring Application Events**를 사용한 이벤트 드리븐 통신을 채택합니다.

### Event Flow Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Event-Driven Communication                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────┐    publishEvent()    ┌──────────────────────────────┐ │
│  │              │ ──────────────────▶  │   ApplicationEventPublisher  │ │
│  │   Service    │                      │   (Spring Event Bus)         │ │
│  │  (Producer)  │                      └──────────────────────────────┘ │
│  │              │                                    │                   │
│  └──────────────┘                                    │                   │
│                                                      │                   │
│         ┌────────────────────────────────────────────┼──────────────┐   │
│         │                                            │              │   │
│         ▼                                            ▼              ▼   │
│  ┌──────────────┐                           ┌──────────────┐ ┌─────────┐│
│  │ Achievement  │                           │ Notification │ │ History ││
│  │   Listener   │                           │   Listener   │ │ Listener││
│  └──────────────┘                           └──────────────┘ └─────────┘│
│         │                                            │              │   │
│         ▼                                            ▼              ▼   │
│  ┌──────────────┐                           ┌──────────────┐ ┌─────────┐│
│  │ gamification │                           │ notification │ │ mission ││
│  │     _db      │                           │     _db      │ │   _db   ││
│  └──────────────┘                           └──────────────┘ └─────────┘│
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Event Implementation

#### 1. Event Record Definition

이벤트는 불변 객체로 정의합니다.

```java
// io.pinkspider.global.event
public record MissionCompletedEvent(
        String userId,
        Long missionId,
        Long executionId,
        Integer expEarned,
        LocalDateTime completedAt
    ) {

}

public record GuildJoinedEvent(
    String userId,
    Long guildId,
    String guildName,
    LocalDateTime joinedAt
) {

}

public record TitleAcquiredEvent(
    String userId,
    Long titleId,
    String titleName,
    String rarity
) {

}
```

#### 2. Event Publishing

서비스 레이어에서 트랜잭션 내 이벤트를 발행합니다.

```java

@Service
@RequiredArgsConstructor
public class MissionExecutionService {

    private final ApplicationEventPublisher eventPublisher;

    @Transactional(transactionManager = "missionTransactionManager")
    public MissionExecutionResponse completeMission(Long missionId, String userId) {
        // 1. 비즈니스 로직 수행
        MissionExecution execution = completeExecution(missionId, userId);

        // 2. 이벤트 발행 (트랜잭션 내)
        eventPublisher.publishEvent(new MissionCompletedEvent(
            userId,
            missionId,
            execution.getId(),
            execution.getExpEarned(),
            LocalDateTime.now()
        ));

        return MissionExecutionResponse.from(execution);
    }
}
```

#### 3. Event Listening

`@TransactionalEventListener`로 트랜잭션 커밋 후 이벤트를 처리합니다.

```java

@Component
@RequiredArgsConstructor
@Slf4j
public class AchievementEventListener {

    private final AchievementService achievementService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGuildJoined(GuildJoinedEvent event) {
        log.info("Processing GuildJoinedEvent: userId={}, guildId={}",
            event.userId(), event.guildId());

        // 길드 가입 관련 업적 체크
        achievementService.checkGuildAchievements(event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMissionCompleted(MissionCompletedEvent event) {
        log.info("Processing MissionCompletedEvent: userId={}, missionId={}",
            event.userId(), event.missionId());

        // 미션 완료 관련 업적 체크
        achievementService.checkMissionAchievements(event.userId(), event.missionId());
    }
}
```

```java

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGuildInvitation(GuildInvitationEvent event) {
        notificationService.sendPushNotification(
            event.inviteeId(),
            "길드 초대",
            event.guildName() + " 길드에 초대되었습니다."
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTitleAcquired(TitleAcquiredEvent event) {
        notificationService.sendPushNotification(
            event.userId(),
            "칭호 획득",
            "'" + event.titleName() + "' 칭호를 획득했습니다!"
        );
    }
}
```

### Event Catalog

| Event                        | Producer            | Consumers                                 | Purpose     |
|------------------------------|---------------------|-------------------------------------------|-------------|
| `MissionCompletedEvent`      | MissionService      | AchievementListener, FeedListener         | 미션 완료 후처리   |
| `MissionStateChangedEvent`   | MissionService      | HistoryListener                           | 미션 상태 이력 저장 |
| `GuildJoinedEvent`           | GuildService        | AchievementListener, NotificationListener | 길드 가입 후처리   |
| `GuildMasterAssignedEvent`   | GuildService        | NotificationListener                      | 마스터 위임 알림   |
| `GuildInvitationEvent`       | GuildService        | NotificationListener                      | 초대 알림 발송    |
| `FriendRequestEvent`         | FriendService       | NotificationListener                      | 친구 요청 알림    |
| `FriendRequestAcceptedEvent` | FriendService       | NotificationListener                      | 친구 수락 알림    |
| `TitleAcquiredEvent`         | GamificationService | NotificationListener                      | 칭호 획득 알림    |
| `AchievementCompletedEvent`  | GamificationService | NotificationListener                      | 업적 달성 알림    |
| `LevelUpEvent`               | GamificationService | NotificationListener, AchievementListener | 레벨업 후처리     |

### Transaction Phase Selection

| Phase              | Use Case                 | Example       |
|--------------------|--------------------------|---------------|
| `AFTER_COMMIT`     | 외부 시스템 호출, 알림 발송, 캐시 무효화 | 푸시 알림, 이메일 발송 |
| `AFTER_ROLLBACK`   | 롤백 시 보상 처리, 로깅           | 실패 로그 기록      |
| `AFTER_COMPLETION` | 커밋/롤백 관계없이 항상 실행         | 리소스 정리        |
| `BEFORE_COMMIT`    | 트랜잭션 내 추가 검증             | 거의 사용하지 않음    |

## Consequences

### Positive

- **느슨한 결합**: Producer는 Consumer를 알 필요 없음
- **확장성**: 새 리스너 추가로 기능 확장 용이
- **트랜잭션 안전**: AFTER_COMMIT으로 데이터 일관성 보장
- **테스트 용이**: 이벤트 발행/수신 독립 테스트 가능
- **MSA 전환 대비**: Kafka로 쉽게 전환 가능

### Negative

- **디버깅 복잡도**: 이벤트 흐름 추적이 어려울 수 있음
- **순서 보장 없음**: 동일 이벤트의 리스너 실행 순서 미보장
- **동기 처리**: 현재 구조는 동기식 (리스너 처리 시간만큼 지연)
- **재시도 없음**: 리스너 실패 시 자동 재시도 메커니즘 없음

### Risks & Mitigations

| Risk             | Mitigation            |
|------------------|-----------------------|
| 리스너 실패 시 데이터 불일치 | 로깅 + 모니터링, 수동 보상 처리   |
| 이벤트 누락           | AFTER_COMMIT 사용으로 최소화 |
| 디버깅 어려움          | 구조화된 로깅, 이벤트 ID 추적    |
| 성능 저하            | 필요시 @Async 적용         |

## Alternatives Considered

### 1. Direct Service Calls

```java
// 직접 호출 방식
missionService.completeMission(missionId);
gamificationService.

grantExp(userId, exp);  // 직접 호출
notificationService.

sendNotification(userId, message);  // 직접 호출
```

- **Pros**: 단순함, 디버깅 용이
- **Cons**: 강한 결합, 순환 의존성 위험
- **Rejected**: 서비스 간 결합도 높음

### 2. Kafka Event Streaming

- **Pros**: 비동기, 재시도, 순서 보장
- **Cons**: 인프라 복잡도, MVP에서 과도함
- **Rejected**: MVP 단계에서 오버엔지니어링
- **Future**: MSA 전환 시 도입 예정

### 3. Database Polling (Outbox Pattern)

- **Pros**: 트랜잭션 보장, 재시도 용이
- **Cons**: 구현 복잡도, 폴링 지연
- **Rejected**: 현재 규모에서 불필요한 복잡도

## Future Considerations

### Async Event Processing

성능 이슈 발생 시 `@Async` 적용:

```java

@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleMissionCompleted(MissionCompletedEvent event) {
    // 비동기 처리
}
```

### Kafka Migration Path

MSA 전환 시 Kafka로 마이그레이션:

```java
// Before (Spring Events)
eventPublisher.publishEvent(new MissionCompletedEvent(...));

// After (Kafka)
    kafkaTemplate.

send("mission-events",new MissionCompletedEvent(...));
```

## References

- [Spring Application Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)
- [TransactionalEventListener](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
- [Event-Driven Architecture](https://martinfowler.com/articles/201701-event-driven.html)

## Related ADRs

- [ADR-001: Multi-Service Monolith Architecture](ADR-001-Multi-Service%20Monolith%20Architecture-20260130.md)
- (Future) ADR-003: Saga Pattern Implementation
- (Future) ADR-004: BFF API Aggregation Strategy
