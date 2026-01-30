# ADR-001: Multi-Service Monolith Architecture

**Date:** 2026-01-30
**Status:** Accepted
**Deciders:** Backend Team

## Context

Level Up Together는 미션 기반 게이미피케이션 서비스로, 사용자들이 미션을 수행하고 길드를 통해 협력하며 성장하는 플랫폼입니다. 초기 MVP 단계에서 빠른 개발과 운영의 용이성을 확보하면서도, 향후 마이크로서비스 아키텍처(MSA)로의 전환 가능성을 열어두어야 합니다.

### 주요 요구사항
- 빠른 MVP 개발 및 배포
- 서비스 간 명확한 경계 (Bounded Context)
- 데이터 일관성 보장
- 향후 MSA 전환 용이성
- 운영 복잡도 최소화

## Decision

**Multi-Service Monolith** 아키텍처를 채택합니다.

단일 Spring Boot 애플리케이션 내에 여러 서비스 모듈을 구성하되, 각 서비스는 독립된 데이터베이스를 사용합니다.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                          │
├─────────────────────────────────────────────────────────────────────┤
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │  User    │ │ Mission  │ │  Guild   │ │  Feed    │ │Gamifica- │  │
│  │ Service  │ │ Service  │ │ Service  │ │ Service  │ │  tion    │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘  │
│       │            │            │            │            │         │
│  ┌────▼─────┐ ┌────▼─────┐ ┌────▼─────┐ ┌────▼─────┐ ┌────▼─────┐  │
│  │ user_db  │ │mission_db│ │ guild_db │ │ feed_db  │ │gamifi_db │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘  │
├─────────────────────────────────────────────────────────────────────┤
│                      Global Infrastructure                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐               │
│  │  Redis   │ │  Kafka   │ │  Events  │ │   Saga   │               │
│  │  Cache   │ │ Messaging│ │  (Spring)│ │  Pattern │               │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘               │
└─────────────────────────────────────────────────────────────────────┘
```

### Service Modules

| Service | Database | Responsibility |
|---------|----------|----------------|
| `userservice` | user_db | OAuth2 인증, JWT, 프로필, 친구 |
| `missionservice` | mission_db | 미션 정의, 진행 추적, Saga 오케스트레이션 |
| `guildservice` | guild_db | 길드 관리, 멤버, 게시판, 채팅 |
| `feedservice` | feed_db | 활동 피드, 좋아요, 댓글 |
| `gamificationservice` | gamification_db | 칭호, 업적, 경험치, 출석 |
| `notificationservice` | notification_db | 푸시 알림, 알림 관리 |
| `metaservice` | meta_db | 공통 코드, 설정, 캐시 |
| `adminservice` | admin_db | 홈 배너, 추천 콘텐츠 |
| `bffservice` | - | API Aggregation, 통합 검색 |

### Key Design Patterns

#### 1. Transaction Manager per Service
멀티 데이터소스 환경에서 `@Transactional`에 명시적으로 트랜잭션 매니저를 지정합니다.

```java
@Transactional(transactionManager = "missionTransactionManager")
public void updateMission(...) { ... }
```

#### 2. Event-Driven Cross-Service Communication
서비스 간 통신은 Spring Events를 사용하며, `@TransactionalEventListener(phase = AFTER_COMMIT)`로 트랜잭션 커밋 후 이벤트를 처리합니다.

```java
// 발행
eventPublisher.publishEvent(new MissionCompletedEvent(userId, missionId, expEarned));

// 수신
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleMissionCompleted(MissionCompletedEvent event) {
    // 경험치 지급, 업적 체크 등
}
```

#### 3. Saga Pattern for Distributed Transactions
여러 서비스에 걸친 비즈니스 로직은 Saga 패턴으로 관리합니다.

```
MissionCompletionSaga:
  1. LoadMissionDataStep
  2. CompleteExecutionStep
  3. UpdateParticipantProgressStep
  4. GrantUserExperienceStep
  5. GrantGuildExperienceStep
  6. UpdateUserStatsStep
  7. CreateFeedFromMissionStep
```

#### 4. BFF (Backend for Frontend)
화면별 최적화된 API를 제공하는 BFF 레이어를 통해 프론트엔드 요청을 단순화합니다.

```java
@GetMapping("/api/v1/bff/home")
public HomeDataResponse getHomeData() {
    // 피드, 랭킹, 길드, 공지 등 병렬 조회 후 aggregation
}
```

## Consequences

### Positive
- **개발 속도**: 단일 코드베이스로 빠른 개발 및 디버깅
- **운영 용이성**: 하나의 배포 단위로 운영 복잡도 최소화
- **명확한 경계**: 서비스별 독립 DB로 데이터 격리
- **MSA 전환 대비**: Saga, Event 패턴으로 향후 분리 용이
- **트랜잭션 관리**: 로컬 트랜잭션으로 일관성 보장 용이

### Negative
- **스케일링 제한**: 특정 서비스만 개별 스케일 아웃 불가
- **배포 단위**: 작은 변경도 전체 재배포 필요
- **장애 전파**: 하나의 서비스 장애가 전체 영향
- **팀 확장성**: 팀 규모 커지면 코드 충돌 가능성

### Risks & Mitigations
| Risk | Mitigation |
|------|------------|
| 서비스 간 강결합 | Event-Driven 통신, 명시적 의존성 관리 |
| 트랜잭션 복잡도 | Saga 패턴, 보상 트랜잭션 구현 |
| 성능 병목 | Redis 캐싱, BFF 레이어 병렬 처리 |

## Alternatives Considered

### 1. Pure Monolith (Single Database)
- **Pros**: 단순함, 트랜잭션 일관성
- **Cons**: 서비스 경계 불명확, MSA 전환 어려움
- **Rejected**: 향후 확장성 고려

### 2. Microservices from Start
- **Pros**: 독립 배포, 기술 다양성
- **Cons**: 운영 복잡도, 분산 트랜잭션
- **Rejected**: MVP 단계에서 과도한 복잡도

### 3. Modular Monolith (Single Database, Module Separation)
- **Pros**: 단순한 트랜잭션
- **Cons**: 데이터 격리 불가, 스키마 충돌 가능
- **Rejected**: 서비스별 데이터 독립성 필요

## References

- [Modular Monolith](https://www.kamilgrzybek.com/design/modular-monolith-primer/)
- [Saga Pattern](https://microservices.io/patterns/data/saga.html)
- [Event-Driven Architecture](https://martinfowler.com/articles/201701-event-driven.html)
- [BFF Pattern](https://samnewman.io/patterns/architectural/bff/)

## Related ADRs

- (Future) ADR-002: Event-Driven Communication Pattern
- (Future) ADR-003: Saga Pattern Implementation
- (Future) ADR-004: BFF API Aggregation Strategy
