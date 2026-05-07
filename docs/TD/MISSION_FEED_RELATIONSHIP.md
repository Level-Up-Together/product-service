# 미션 · 미션기록 · 피드 관계도

## 엔티티 관계

```
Mission (미션)
  │
  └── MissionParticipant (미션 참가)
        │
        ├── MissionExecution (일반 미션 수행기록)
        │     └── ActivityFeed (피드) — 0개 또는 1개
        │
        └── DailyMissionInstance (고정 미션 수행기록)
              └── ActivityFeed (피드) — 0개 또는 1개
```

- 미션 1개에 여러 참가자가 존재
- 참가자별로 수행기록이 생성됨 (일반 미션: MissionExecution, 고정 미션: DailyMissionInstance)
- 수행기록 1개에 피드는 **0개 또는 1개** — 사용자가 기록 페이지에서 "저장"해야 생성됨
- 피드가 없는 수행기록은 정상 상태 (미생성 ≠ 미공개)

## 피드 공개범위 (FeedVisibility)

| 값 | 의미 | 누가 볼 수 있나 |
|---|---|---|
| `PUBLIC` | 전체 공개 | 모든 사용자 |
| `FRIENDS` | 친구 공개 | 작성자 본인 + 친구 |
| `GUILD` | 길드 공개 | 작성자 본인 + 같은 길드원 |
| `PRIVATE` | 비공개 | 작성자 본인만 |

## 미션 수행 → 피드 생성 흐름

```
┌─────────────────────────────────────────────────────────┐
│  1. 미션 수행 완료                                        │
│     completeMissionExecution()                           │
│     - feedVisibility 미전송 → null → PRIVATE             │
│     - Saga: 경험치/통계 처리                               │
│     - CreateFeedFromMissionStep: PRIVATE → 피드 생성 스킵  │
│     → ★ 수행기록만 존재, 피드 없음                          │
└──────────────────────┬──────────────────────────────────┘
                       │ 완료 후 자동 이동
                       ▼
┌─────────────────────────────────────────────────────────┐
│  2. 기록 페이지 (mission-record)                          │
│     - 노트 입력, 이미지 첨부                               │
│     - 공개범위 선택: PUBLIC / FRIENDS / GUILD / PRIVATE    │
│       (기본값: 유저의 preferredFeedVisibility)              │
└──────┬─────────────────────────────┬────────────────────┘
       │ "저장" 클릭                  │ 뒤로가기 (저장 안 함)
       ▼                             ▼
┌──────────────────────┐  ┌──────────────────────────────┐
│  3a. 저장 처리         │  │  3b. 아무것도 안 함             │
│  - 노트/이미지 저장     │  │  - 수행기록만 존재              │
│  - shareMission...()  │  │  - 피드 없음                   │
│    ├ 피드 없음 → 생성   │  └──────────────────────────────┘
│    └ 피드 있음 → 업데이트│
│  → ★ 피드 존재          │
└──────────────────────┘
```

## 피드 공개범위 변경

```
┌──────────────────────────────────────────────────┐
│  기록 페이지 재진입                                 │
│  - 기존 피드의 공개범위를 변경 가능                   │
│  - shareMissionExecutionToFeed(newVisibility)     │
│    → 기존 피드 찾아서 visibility 업데이트             │
│                                                    │
│  피드 미생성 상태에서도 저장 가능                      │
│  - 피드가 없으면 새로 생성                           │
│  - 피드가 있으면 업데이트                             │
└──────────────────────────────────────────────────┘
```

## 피드 상태 정리

| 상태 | 수행기록 | 피드 | 설명 |
|---|---|---|---|
| 미션 완료 직후 | ✅ 존재 | ❌ 없음 | 기록 페이지에서 저장 전 |
| 기록 저장 (PUBLIC) | ✅ 존재 | ✅ PUBLIC | 전체 공개 피드 생성 |
| 기록 저장 (FRIENDS) | ✅ 존재 | ✅ FRIENDS | 친구 공개 피드 생성 |
| 기록 저장 (GUILD) | ✅ 존재 | ✅ GUILD | 길드 공개 피드 생성 |
| 기록 저장 (PRIVATE) | ✅ 존재 | ✅ PRIVATE | 비공개 피드 생성 (본인만 조회) |
| 저장 안 함 | ✅ 존재 | ❌ 없음 | 피드 엔티티 자체가 부재 |

## 홈피드 필터별 조회 범위

| 필터 | 표시되는 피드 |
|---|---|
| **ALL** (전체) | 모든 사용자의 PUBLIC 피드 |
| **FRIENDS** (친구) | 친구의 PUBLIC + FRIENDS 피드 |
| **GUILD** (길드) | 내 길드원의 PUBLIC + GUILD 피드 |
| **MINE** (내 글) | 내 피드 중 PRIVATE 제외 |

## 관련 코드 위치

| 구성요소 | 경로 |
|---|---|
| FeedVisibility enum | `feed-service/domain/enums/FeedVisibility.java` |
| 피드 생성/업데이트 | `feed-service/application/FeedCommandService.java` |
| 피드 조회/필터링 | `feed-service/application/FeedQueryService.java` |
| 피드 Repository | `feed-service/infrastructure/ActivityFeedRepository.java` |
| Saga 피드 생성 Step | `mission-service/saga/steps/CreateFeedFromMissionStep.java` |
| 미션 공유 API | `mission-service/application/MissionExecutionService.shareExecutionToFeed()` |
| 공유 Strategy (일반) | `mission-service/application/strategy/RegularMissionExecutionStrategy.java` |
| 공유 Strategy (고정) | `mission-service/application/strategy/PinnedMissionExecutionStrategy.java` |
| 프론트 기록 페이지 | `level-up-together-frontend/src/app/(afterLogin)/mission-record/page.tsx` |
| 프론트 피드 API | `level-up-together-frontend/src/lib/api/feed/feed-api.ts` |