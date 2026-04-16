# 미션 타입 계층 구조

## 분류 체계 개요

```
Mission
├── type (MissionType)
│   ├── PERSONAL ─── 개인 미션
│   └── GUILD ────── 길드 미션 (guildId 필수, 길드원 자동 참가)
│
├── isPinned (Boolean)
│   ├── false ─── 일반 미션 (1회성, MissionExecution 사용)
│   └── true ──── 고정 미션 (반복, DailyMissionInstance 사용)
│
├── source (MissionSource)
│   ├── SYSTEM ─── 시스템 제공
│   ├── USER ───── 사용자 생성 (기본값)
│   └── GUILD ──── 길드 생성
│
├── visibility (MissionVisibility)
│   ├── PUBLIC ──────── 전체 공개
│   ├── FRIENDS_ONLY ── 친구 공개
│   ├── PRIVATE ─────── 비공개 (기본값)
│   └── GUILD_ONLY ──── 길드 전용
│
├── missionInterval (MissionInterval)
│   ├── DAILY ──── 매일 (1일, 기본값)
│   ├── WEEKLY ─── 매주 (7일)
│   └── MONTHLY ── 매월 (30일)
│
└── participationType (MissionParticipationType)
    ├── DIRECT ──────── 직접 참여 가능
    └── TEMPLATE_ONLY ─ 템플릿 전용 (복제 후 사용)
```

## 실행 전략 분기 (Strategy Pattern)

```
MissionExecutionStrategyResolver.resolve(missionId, userId)
│
├── mission.isPinned == true
│   │
│   └── PinnedMissionExecutionStrategy
│       └── DailyMissionInstanceService 위임
│           ├── 실행 엔티티: DailyMissionInstance
│           ├── 하루 복수 완료 가능 (sequenceNumber 증가)
│           ├── 미션 스냅샷 저장 (title, description, category 등)
│           ├── completionCount / totalExpEarned 추적
│           ├── dailyExecutionLimit 지원 (null=무제한)
│           └── 자동완료: targetDurationMinutes 없을 때만
│
└── mission.isPinned == false
    │
    └── RegularMissionExecutionStrategy
        └── MissionCompletionSaga 실행
            ├── 실행 엔티티: MissionExecution
            ├── 날짜당 1건 (participant_id + execution_date 유니크)
            ├── 길드 미션이면 길드 경험치도 지급
            └── 자동완료: 2시간 타임아웃 시 항상
```

## 미션 조합별 동작 비교

| 조합 | type | isPinned | 실행 엔티티 | 날짜당 실행 | 자동완료 | 길드 EXP |
|------|------|----------|------------|-----------|---------|---------|
| 일반 개인 | PERSONAL | false | MissionExecution | 1회 | 2시간 후 항상 | ✗ |
| 고정 개인 | PERSONAL | true | DailyMissionInstance | 복수 가능 | targetDuration 없을 때만 | ✗ |
| 일반 길드 | GUILD | false | MissionExecution | 1회 | 2시간 후 항상 | ✓ |
| 고정 길드 | GUILD | true | DailyMissionInstance | 복수 가능 | targetDuration 없을 때만 | ✗ (pinned는 길드EXP 미지급) |

## 경험치 계산

```
MissionExecutionLifecycle.calculateExpByDuration()
│
├── MissionExecution (일반 미션)
│   └── 1분 = 1 EXP (최대 480분/8시간)
│
└── DailyMissionInstance (고정 미션)
    ├── targetDurationMinutes 설정됨
    │   └── 목표 달성 시: targetDurationMinutes + expPerCompletion (보너스)
    └── targetDurationMinutes 미설정
        └── 1분 = 1 EXP (최대 480분/8시간)
```

## 상태 전이 (ExecutionStatus)

```
PENDING ──────→ IN_PROGRESS ──────→ COMPLETED
   │                │
   │                └─── skip() ──→ PENDING (재시작 가능)
   │
   └──── markAsMissed() ──→ MISSED

자동완료 조건:
- isExpired(): startedAt으로부터 120분 이상 경과
- autoCompleteForDateChange(): 자정 넘김 처리
```

## 길드 미션 자동 참가

```
GuildJoinedEvent 발행
    │
    └── GuildMissionEventListener.handleGuildJoined()
        ├── 길드의 활성 미션 조회 (status = OPEN or IN_PROGRESS)
        └── 각 미션에 MissionParticipantService.addGuildMemberAsParticipant()

GuildMemberRemovedEvent 발행
    │
    └── GuildMissionEventListener.handleGuildMemberRemoved()
        └── 참가자 상태를 WITHDRAWN으로 변경
```

## 미션 완료 Saga (MissionCompletionSaga)

```
일반 미션 실행 순서:                    고정 미션 실행 순서:
1. LoadMissionData                    1. LoadPinnedMissionData
2. CompleteExecution                  2. CompletePinnedInstance
3. GrantUserExperience                3. GrantUserExperience
4. GrantGuildExperience (길드 미션 시)  4. UpdateUserStats
5. UpdateParticipantProgress          5. CreateFeedFromMission
6. UpdateUserStats                    6. CreateNextPinnedInstance
7. CreateFeedFromMission
```

## 프론트엔드 표시 타입 (MissionDisplayType)

```
getMissionDisplayType(mission)
│
├── type == 'GUILD' ──────→ 'GUILD' (길드 미션)
├── isPinned == true ─────→ 'FIXED' (고정 미션)
├── interval == 'WEEKLY' ─→ 'FIXED' (주간 미션도 고정 표시)
└── 그 외 ────────────────→ 'GENERAL' (일반 미션)
```
