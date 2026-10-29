# ADR-009: WebSocket Redis Pub/Sub Relay

**Date:** 2026-10-12
**Status:** Accepted (Implemented — 알림 QA-224 2026-07-10, DM LUT-263 2026-07-24, 길드 채팅 LUT-549 2026-10-12 로 전 경로 적용)
**Deciders:** Backend Team
**Branch:** `epic/v1.1.1` (`0feeb3f1`) + main 핫픽스 cherry-pick (`5e0814aa`)

## Context

실시간 기능(알림 레드닷, 길드 DM, 길드 단체채팅)은 STOMP over WebSocket 이다.

### 현재 브로커 구성

`global/config/WebSocketConfig`:

```java
registry.enableSimpleBroker("/topic", "/queue");     // 인스턴스 로컬 메모리 브로커
registry.setApplicationDestinationPrefixes("/app");
registry.setUserDestinationPrefix("/user");
// 엔드포인트 /ws (SockJS + 네이티브)
```

SimpleBroker 는 **그 JVM 에 붙은 세션만** 안다. `SimpMessagingTemplate.convertAndSend*` 는 호출한 인스턴스의 구독자에게만 전달된다.

### 운영 토폴로지

- prod product 는 ALB 타깃 그룹 `lut-product-tg` 에 EC2 2대(`level-up-together-infra/alb.tf`: `product_ec2_1`, `product_ec2_2` attachment). 타깃 그룹에 `stickiness` 설정 없음(= 비활성).
- 발신자와 수신자는 **서로 다른 WebSocket 연결**이라, 각자 어느 인스턴스에 붙을지는 독립적이다. 스티키 세션이 있어도 "한 클라이언트 ↔ 한 인스턴스" 만 고정할 뿐 두 유저를 같은 인스턴스로 모으지 못한다.
- dev 는 1대라 이 문제가 **재현되지 않는다.**

### 드러난 경위

| 티켓 | 날짜 | 경로 | 증상 / 발견 |
|---|---|---|---|
| QA-224 | 2026-07-10 | 알림 `/user/queue/notifications` | 알림 지연·유실 대응으로 실시간 채널을 신설하면서 처음부터 Redis 릴레이로 구현 |
| LUT-263 | 2026-07-24 | DM `/user/queue/dm*` | DM presence 작업 중 발견 — 상대 WS 가 다른 인스턴스면 DM 실시간 전달 자체가 실패하던 잠재 버그 |
| LUT-547 → LUT-549 | 2026-10-12 | 길드 채팅 `/topic/guild/{id}/chat`·`/read` | prod "내 메시지만 보이고 상대 메시지는 재진입 후 노출". 토픽 3곳이 `convertAndSend` 직접 호출로 남아 있었다 |

증상은 항상 같다 — **"내 것은 보이고 상대 것만 안 보이며, 재조회하면 나온다."** 데이터는 DB 에 저장되고 실시간 전달만 빠지기 때문이다.

## Decision

**모든 STOMP 전송은 Redis pub/sub 퍼블리셔 → 인스턴스별 릴레이 경로를 탄다.** 비즈니스 코드(컨트롤러·서비스)는 `SimpMessagingTemplate` 를 주입하지 않고 퍼블리셔만 쓴다. `SimpMessagingTemplate` 은 릴레이만 가진다.

```
 인스턴스 A (발신)                         Redis                      인스턴스 A / B (모두)
┌──────────────────────┐   PUBLISH     ┌───────────┐  SUBSCRIBE   ┌───────────────────────────┐
│ Service / Controller │──▶ Publisher ─▶│  channel  │─────────────▶│ Relay (MessageListener)    │
│  (DB 커밋)            │  (afterCommit) └───────────┘              │  └▶ SimpMessagingTemplate  │
└──────────────────────┘                                            │      └▶ 로컬 SimpleBroker  │
                                                                    │          └▶ 로컬 구독자만   │
                                                                    └───────────────────────────┘
```

### 경로 · 채널

| 경로 | 퍼블리셔 → 릴레이 | Redis 채널 | 봉투(JSON) | 릴레이 전송 |
|---|---|---|---|---|
| 알림 `/user/queue/notifications` | `NotificationRealtimePublisher.publish` → `NotificationRealtimeRelay` | `notification:realtime` | `{user_id, notification}` | `convertAndSendToUser(userId, "/queue/notifications", 봉투)` |
| DM `/user/queue/dm` · `/dm/read` · `/dm/typing` | `DmRealtimePublisher.publishToUser` → `DmRealtimeRelay` | `chat:dm:realtime` | `{user_id, destination, body}` | `convertAndSendToUser(userId, destination, body)` |
| 길드 채팅 `/topic/guild/{id}/chat` · `/read` | `GuildChatRealtimePublisher.publishToTopic` → `GuildChatRealtimeRelay` | `chat:guild:realtime` | `{destination, body}` | `/topic/` 접두 검증 후 `convertAndSend(destination, body)` |

- 리스너 컨테이너: `ChatRealtimeConfig`(chat 2채널, LUT-549 에서 `DmRealtimeConfig` 를 통합) · `NotificationRealtimeConfig`. 릴레이·컨테이너는 `@Profile("!test")`.
- 퍼블리셔는 활성 트랜잭션이 있으면 **`afterCommit` 에 발행**한다 — 수신 클라이언트가 실시간 이벤트를 받고 재조회했을 때 미커밋 데이터를 놓치지 않게.
- 릴레이는 body 를 **JSON 문자열 그대로** 보낸다(프론트는 content-type 무관 `JSON.parse`). 브로드캐스트 DTO 는 `@JsonNaming(SnakeCase)` 필수(LUT-395).
- 대상 세션·구독자가 그 인스턴스에 없으면 SimpleBroker 가 조용히 버린다 — 있는 인스턴스가 전달한다.
- DM 실시간 전달은 WS·REST 어느 경로로 보내도 서비스 계층(`GuildDirectMessageService`)에서 수신자 + 발신자(다중 기기 에코)로 발행한다. 길드 채팅 REST(`GuildChatController`)도 같은 퍼블리셔를 쓴다.

### 유일한 직접 전송 예외

`GuildDirectMessageWebSocketController` 의 **`/queue/dm/errors` — 발신자 본인에게 보내는 에러 에코**. 그 메시지를 보낸 세션은 반드시 현재 인스턴스에 있으므로 릴레이가 필요 없다. 2026-10-29 기준 릴레이 밖의 `convertAndSend*` 호출은 이 한 곳뿐이다.

## Consequences

### Positive

- **인스턴스 수와 무관하게 전달된다.** 스티키 세션·인스턴스 배치에 의존하지 않는다.
- **새 인프라 없음**: 이미 쓰던 Redis(캐시·ShedLock 등)의 pub/sub 만 추가로 쓴다. STOMP 클라이언트·프론트 구독 경로는 그대로다.
- **일관성**: afterCommit 발행으로 "실시간 이벤트는 왔는데 조회하면 없다" 를 막는다.
- **한 가지 패턴**: 새 실시간 경로도 같은 퍼블리셔/릴레이 짝을 따르면 된다.

### Negative

- **at-most-once**: Redis pub/sub 은 저장·재전송이 없다. Redis 장애나 리스너 끊김 구간의 실시간 메시지는 사라진다. 퍼블리셔는 발행 실패를 `warn` 로그로 삼킨다 — 데이터는 DB 에 있으므로 클라이언트 재조회로 복구된다.
- **모든 인스턴스가 모든 메시지를 받는다**: 구독자가 없는 인스턴스는 받아서 버린다. 같은 인스턴스에 두 유저가 있어도 Redis 왕복을 거친다.
- **실시간 경로의 Redis 의존**: Redis 가 멈추면 실시간 전달 전체가 멈춘다(저장·푸시 파이프라인과는 별개).
- **자동 가드 없음**: `SimpMessagingTemplate` 직접 주입을 막는 테스트가 없다. CLAUDE.md 규칙과 리뷰에 의존한다 — LUT-549 는 정확히 이 누락(길드 채팅 3곳)이었다.

### Risks & Mitigations

| Risk | Mitigation |
|---|---|
| 신규 토픽/큐를 직접 `convertAndSend` 로 구현 (dev 에선 정상) | CLAUDE.md "WebSocket 실시간 전파 — Redis 릴레이 필수" 절. 점검: `grep -rn "convertAndSend" --include='*.java' service \| grep -v Relay \| grep -iv redisTemplate` |
| 커밋 전 발행으로 재조회 시 데이터 없음 | 퍼블리셔가 트랜잭션 동기화 활성 시 `afterCommit` 발행 |
| 릴레이가 임의 destination 으로 전송 | 길드 릴레이는 `/topic/` 접두만 허용, DM·알림은 user destination 고정 형식 |
| 프론트 필드명 불일치 | 브로드캐스트 DTO `@JsonNaming(SnakeCase)` (LUT-395) |
| pub/sub 유실 | DB 저장이 먼저, 실시간은 보조. 화면 재진입·재조회로 정합 회복 |

## Alternatives Considered

> 아래 대안은 당시 티켓에 비교 기록이 남아 있지 않아 이 ADR 작성 시점(2026-10-29)에 정리한 것이다 ❓

### 1. ALB 스티키 세션

- **Pros**: 설정 한 줄
- **Cons**: 클라이언트 하나를 인스턴스 하나에 묶을 뿐, 발신자·수신자를 같은 인스턴스로 모으지 못한다
- **Rejected**: 문제를 해결하지 못함

### 2. 외부 STOMP 브로커 릴레이 (`enableStompBrokerRelay` + RabbitMQ/ActiveMQ)

- **Pros**: Spring 표준 방식, 구독 상태를 브로커가 관리
- **Cons**: 브로커 인프라 신설·운영(현재 Kafka·MQ 미사용 — CLAUDE.md "알림/푸시 파이프라인 (Kafka 미사용)")
- **Not chosen**: 현재 규모에서 기존 Redis 로 충분

### 3. 실시간용 단일 인스턴스

- **Cons**: 가용성 상실, 롤링 배포 중 실시간 단절
- **Rejected**

### 4. Redis Streams / Kafka

- **Pros**: 유실 없는 전달·재처리
- **Cons**: 실시간 표시는 재조회로 복구 가능한 보조 채널이라 영속 전달의 이득이 작다. 앱 푸시는 이미 Redis Stream `stream:app-push` 로 별도 처리
- **Not chosen**

## Future Considerations

- 릴레이 밖 `SimpMessagingTemplate` 사용을 막는 아키텍처 테스트(허용: `*Relay`, `GuildDirectMessageWebSocketController` 에러 에코) ❓ 미착수
- MSA 전환 시 채널별 퍼블리셔를 서비스 이벤트 발행으로 바꿀 수 있다(채널 이름이 서비스 경계와 이미 일치: `notification:*`, `chat:*`)

## References

- `service/src/main/java/io/pinkspider/global/config/WebSocketConfig.java`
- `service/chat-service/.../chatservice/realtime/` — `DmRealtimePublisher`, `DmRealtimeRelay`, `GuildChatRealtimePublisher`, `GuildChatRealtimeRelay`, `ChatRealtimeConfig`
- `service/notification-service/.../notificationservice/realtime/` — `NotificationRealtimePublisher`, `NotificationRealtimeRelay`, `NotificationRealtimeConfig`
- 커밋: `9f699549` (QA-224, 2026-07-10) · `58565bc9` (LUT-263, 2026-07-24) · `0feeb3f1` / `5e0814aa` (LUT-549, 2026-10-12)
- CLAUDE.md "WebSocket 실시간 전파 — Redis 릴레이 필수 (LUT-263/QA-224/LUT-549)"

## Related ADRs

- [ADR-001: Multi-Service Monolith Architecture](ADR-001-Multi-Service%20Monolith%20Architecture-20260130.md)
- [ADR-002: Event-Driven Cross-Service Communication](ADR-002-Event-Driven%20Cross-Service%20Communication-20260130.md) — 알림 이벤트 → `NotificationService` → 실시간 발행
