---
name: qa-regression
description: "QA 수정 후 영향 분석과 회귀 테스트를 수행합니다. 변경된 파일을 기반으로 영향받는 서비스, 이벤트 리스너, Saga, 크로스 서비스 호출을 추적하고 관련 테스트를 실행합니다. '회귀 테스트', '영향 분석', 'regression', '수정 영향', 'QA 검증' 요청 시 사용합니다."
argument-hint: "<scope> (e.g., 'QA-7', 'last commit', 'staged changes')"
allowed-tools: Agent, Read, Grep, Glob, Bash
---

# QA Regression Analysis

대상: $ARGUMENTS

## 1단계: 변경 범위 수집

### 변경 파일 목록 추출

대상에 따라 다른 방식으로 변경 파일을 수집:

```bash
# QA-XX 브랜치인 경우: 분기한 브랜치 대비 전체 변경 (feature 는 진행 중인 epic/vX.Y.Z, hotfix 는 main — CLAUDE.md "브랜치 흐름")
git diff epic/vX.Y.Z...HEAD --name-only

# 마지막 커밋 기준
git diff HEAD~1 --name-only

# staged 변경
git diff --cached --name-only

# unstaged + staged
git diff HEAD --name-only
```

## 2단계: 변경 분류

변경된 파일을 서비스/레이어별로 분류:

### 2-1. 서비스 매핑

| 경로 패턴 | 서비스 | DB |
|----------|--------|-----|
| `user-service/` | userservice | user_db |
| `mission-service/` | missionservice | mission_db |
| `guild-service/` | guildservice | guild_db |
| `gamification-service/` | gamificationservice | gamification_db |
| `feed-service/` | feedservice | feed_db |
| `notification-service/` | notificationservice | notification_db |
| `meta-service/` | metaservice | meta_db |
| `chat-service/` | chatservice | chat_db |
| `stats-service/` | statsservice | stats_db |
| `bff-service/` | bffservice | - |
| `notice-service/`, `support-service/` | noticeservice, supportservice | - (Admin Feign) |

`adminservice`(admin_db)는 별도 레포 `admin-service` 다.

### 2-2. 레이어 분류

| 경로 패턴 | 레이어 | 영향 범위 |
|----------|--------|---------|
| `api/` | Controller | API 스펙 변경 → 프론트엔드 영향 가능 |
| `application/` | Service | 비즈니스 로직 변경 → 이벤트/Saga 영향 가능 |
| `domain/entity/` | Entity | DB 스키마 변경 가능 → DDL 확인 필요 |
| `domain/dto/` | DTO | API 응답 변경 → 프론트엔드 영향 가능 |
| `infrastructure/` | Repository | 쿼리 변경 → 성능 영향 가능 |
| `saga/` | Saga | 분산 트랜잭션 영향 → 보상 로직 확인 필요 |
| `scheduler/` | Scheduler | 배치 영향 → 스케줄 타이밍 확인 |

## 3단계: 영향 파급 분석

### 3-1. 이벤트 영향 분석

변경된 Service에서 발행하는 이벤트 추적:

```
Grep: "eventPublisher.publishEvent" in 변경된 서비스 파일
→ 이벤트 클래스명 추출
→ @TransactionalEventListener 검색으로 수신 리스너 파악
→ 리스너가 속한 서비스 확인
```

**주요 이벤트 흐름 매핑:**

| 발행 서비스 | 이벤트 | 수신 서비스 |
|-----------|--------|----------|
| GuildService | GuildJoinedEvent | Achievement, Feed, UserStats |
| FriendService | FriendRequestAcceptedEvent | Notification, Feed, UserStats |
| GamificationService | TitleAcquiredEvent | Notification, Feed |
| GamificationService | AchievementCompletedEvent | Notification, Feed |
| UserExperienceService | UserLevelUpEvent | Feed, UserProfile |
| AttendanceService | AttendanceStreakEvent | Feed |
| MissionService | MissionStateChangedEvent | MissionStateHistory |
| UserService | UserProfileChangedEvent | Chat, Feed, Guild, Mission (닉네임 동기화) |
| FeedCommandService | FeedLikedEvent / FeedUnlikedEvent | UserStats |
| MissionCompletionSaga | MissionCompletedCountEvent | UserStats |

### 3-2. Facade 의존 분석

변경된 서비스가 Facade를 통해 다른 서비스에 노출하는 경우:

```
Grep: "변경된 메서드명" in *FacadeService.java
→ Facade를 호출하는 다른 서비스 추적
```

| Facade | 구현체 | 의존하는 서비스들 |
|--------|--------|---------------|
| UserQueryFacade | UserQueryFacadeService | guild, mission, gamification, bff |
| GuildQueryFacade | GuildQueryFacadeService | mission, gamification, bff |
| GamificationQueryFacade | GamificationQueryFacadeService | user, bff, mission |

### 3-3. Saga 영향 분석

변경된 코드가 Saga Step에 포함된 경우:

```
Grep: "변경된 서비스/메서드" in saga/steps/*.java
→ 해당 Saga의 전체 Step 흐름 확인
→ 보상(compensate) 로직 영향 확인
```

### 3-4. 프론트엔드 영향 분석

Controller/DTO 변경 시:
- API URI 변경 → 프론트엔드 API 호출 코드 검색
- Response 필드 변경 → 프론트엔드 타입 정의 검색
- Request 필드 변경 → 프론트엔드 폼/요청 코드 검색

```bash
# Web Frontend에서 관련 API 호출 검색
grep -r "변경된API경로" /Users/pink-spider/Code/github/Level-Up-Together/level-up-together-frontend/src/
# Admin Frontend
grep -r "변경된API경로" /Users/pink-spider/Code/github/Level-Up-Together/level-up-together-admin-frontend/src/
```

## 4단계: 테스트 대상 식별

### 4-1. 직접 영향 테스트 (필수)

변경된 파일의 직접 테스트:

| 변경 파일 | 테스트 파일 | 실행 명령 |
|----------|-----------|---------|
| `{Service}.java` | `{Service}Test.java` | `--tests "*{Service}Test"` |
| `{Controller}.java` | `{Controller}Test.java` | `--tests "*{Controller}Test"` |

### 4-2. 간접 영향 테스트 (권장)

이벤트/Facade로 연결된 서비스의 테스트:

| 영향 경로 | 테스트 대상 |
|----------|-----------|
| 이벤트 발행 변경 | 수신 EventListener 테스트 |
| Facade 메서드 변경 | Facade 호출하는 서비스 테스트 |
| Saga Step 변경 | Saga 오케스트레이터 테스트 |
| Entity 변경 | Repository 테스트 + 관련 Service 테스트 |

### 4-3. 전체 빌드 테스트 (대규모 변경 시)

```bash
./gradlew clean build
```

## 5단계: 테스트 실행

### 5-1. 직접 영향 테스트 실행
```bash
./gradlew :service:test --tests "*{Test1}" --tests "*{Test2}"
```

### 5-2. 간접 영향 테스트 실행
```bash
./gradlew :service:test --tests "*{IndirectTest1}" --tests "*{IndirectTest2}"
```

### 5-3. 결과 분석
- 통과: 회귀 없음 확인
- 실패: 원인 분석 및 보고

## 6단계: 결과 보고

```markdown
## 회귀 분석 보고서: QA-{번호}

### 변경 범위
| 서비스 | 변경 파일 수 | 레이어 |
|--------|-----------|--------|
| missionservice | 3 | Service, Controller, DTO |

### 영향 파급
#### 이벤트 영향
- MissionStateChangedEvent → MissionStateHistoryEventListener ⚠️ 확인 필요
- (영향 없음이면 "이벤트 영향 없음" 표시)

#### Facade 영향
- 영향 없음 (Facade 메서드 미변경)

#### 프론트엔드 영향
- ⚠️ API 응답 필드 변경: `mission_status` 추가
  - level-up-together-frontend: `src/lib/api/mission.ts` 업데이트 필요

#### Saga 영향
- 영향 없음

### 테스트 결과
| 구분 | 테스트 | 결과 | 소요 시간 |
|------|-------|------|---------|
| 직접 | MissionServiceTest | ✅ 통과 (15/15) | 2.3s |
| 직접 | MissionControllerTest | ✅ 통과 (8/8) | 1.1s |
| 간접 | FeedProjectionEventListenerTest | ✅ 통과 (5/5) | 0.8s |

### 위험도 평가
- **전체 위험도**: LOW
- **회귀 가능성**: 없음 (모든 테스트 통과)
- **프론트엔드 영향**: ⚠️ 타입 업데이트 필요 (breaking change 아님, 필드 추가)

### 권장 사항
1. 프론트엔드 `mission.ts` 타입 업데이트
2. staging 배포 후 미션 완료 플로우 수동 확인
```
