# Plan: QA-31 날짜가 바뀌면 수행중 미션이 증발하는 현상

## 이슈 요약

- **Jira**: QA-31
- **제목**: [App] 날짜가 바뀌면 수행중이었던 미션이 증발하는 현상
- **우선순위**: High
- **보고자**: rumi

### 재현 시나리오
1. 오후 11:10에 미션 시작 (executionDate=03-28, status=IN_PROGRESS)
2. 자정 넘긴 후 00:04에 앱 확인
3. 미션이 목록에서 사라짐 — 캘린더/시간표에서도 확인 불가

### 요청사항
- 수행중인 미션을 나의미션 목록 최상단에 항상 표시
- 날짜가 바뀌어도 IN_PROGRESS 미션은 종료 전까지 보이도록
- 수행중 화면으로 진입할 수 있도록 UI 변경

## 근본 원인

**2단계 문제**:

```
23:10 미션 시작 → executionDate=03-28, status=IN_PROGRESS
     ↓
00:00 날짜 변경
  ├─ getTodayExecutions() → executionDate=03-29만 조회 → 03-28 미션 안 보임
  └─ DailyMissionInstanceScheduler 실행 → 03-28 IN_PROGRESS 자동 완료
     ↓
유저에게는 미션이 "증발"한 것처럼 보임
```

1. `getTodayExecutions()`가 `executionDate = 오늘`로만 필터링
2. 자정 스케줄러(00:00)가 전날 IN_PROGRESS를 즉시 자동 완료

## 현재 코드 상태

| 구성요소 | 파일 | 현재 동작 |
|---------|------|---------|
| 쿼리 필터 | `MissionExecutionQueryService:101-112` | `executionDate = today` 엄격 필터 |
| 자정 스케줄러 | `DailyMissionInstanceScheduler:50-84` | 00:00에 전날 IN_PROGRESS 즉시 자동 완료 |
| IN_PROGRESS 조회 | `MissionExecutionController:159-165` | `GET /executions/in-progress` — 날짜 무관, 상태만 조회 (이미 존재) |
| BFF 응답 | `BffMissionService:49` | `getTodayExecutions()` 위임만 |
| 프론트 | `MyMission.tsx:114-145` | `today_executions`만으로 상태 결정 |

## Phase 1: Backend — getTodayExecutions 쿼리 확장

현재 `executionDate = today`만 조회하는 로직을 **전날 IN_PROGRESS도 포함**하도록 수정.

### 1-1. MissionExecutionRepository — 쿼리 수정
- **파일**: `service/mission-service/.../infrastructure/MissionExecutionRepository.java`
- **변경**: `findByUserIdAndExecutionDate()` 외에 별도 쿼리 추가 또는 기존 쿼리 확장
- **새 쿼리**: 전날 + 오늘의 IN_PROGRESS 실행 기록도 함께 조회
  ```sql
  WHERE p.userId = :userId AND (
    me.executionDate = :today
    OR (me.executionDate = :yesterday AND me.status = 'IN_PROGRESS')
  )
  ```

### 1-2. DailyMissionInstanceRepository — 동일 패턴 적용
- 고정 미션도 동일하게 전날 IN_PROGRESS 인스턴스 포함

### 1-3. MissionExecutionQueryService — getTodayExecutions 수정
- **파일**: `service/mission-service/.../application/MissionExecutionQueryService.java`
- **변경**: `yesterday` 변수 추가, 수정된 쿼리 사용

## Phase 2: Backend — 자정 스케줄러 조정

현재 00:00에 전날 IN_PROGRESS를 즉시 자동 완료하는 로직을 조정.

### 2-1. DailyMissionInstanceScheduler 수정
- **파일**: `service/mission-service/.../scheduler/DailyMissionInstanceScheduler.java`
- **변경**: `autoCompletePastDayInProgressInstances()`와 `autoCompletePastDayInProgressExecutions()`에서 **2시간 미경과 미션은 제외**
- **로직**:
  ```java
  // 시작 후 2시간 이상 경과한 미션만 자동 완료
  if (Duration.between(execution.getStartedAt(), now).toMinutes() >= 120) {
      // 자동 완료
  }
  // 2시간 미만은 사용자가 직접 종료하도록 유지
  ```
- **효과**: 23:10에 시작한 미션은 01:10까지 사용자가 직접 종료 가능

### 2-2. MissionAutoCompleteScheduler는 유지
- 5분 간격 스케줄러가 2시간 초과 미션을 자동 완료하므로, 자정 스케줄러에서 제외해도 안전망 존재

## Phase 3: Frontend — IN_PROGRESS 미션 최상단 고정

### 3-1. BFF 응답 활용
- BFF 응답의 `in_progress_count`와 `today_executions`에서 IN_PROGRESS를 감지
- Phase 1에서 전날 IN_PROGRESS도 포함되므로 별도 API 불필요

### 3-2. MyMission 컴포넌트 수정
- **파일**: `level-up-together-frontend/src/app/(afterLogin)/mission/components/MyMission.tsx`
- **변경**: IN_PROGRESS 미션을 섹션 구분 없이 최상단에 고정 표시
- **UI**: 수행중 미션 카드에 경과 시간 + "수행중" 배지 + 탭하면 타이머 화면으로 이동

### 3-3. MissionList 컴포넌트 수정
- **파일**: `level-up-together-frontend/src/app/(afterLogin)/mission/components/MissionList.tsx`
- **변경**: `isInProgress` 미션을 목록 최상단으로 분리 표시

## 실행 순서

```
Phase 1 (쿼리 확장) — 전날 IN_PROGRESS 포함
  ↓
Phase 2 (스케줄러 조정) — 2시간 미경과 미션 자동완료 제외
  ↓
Phase 3 (프론트 UI) — IN_PROGRESS 최상단 고정
```

## 리스크

| 리스크 | 대응 |
|--------|------|
| 전날 IN_PROGRESS가 무한히 남는 경우 | MissionAutoCompleteScheduler가 5분마다 2시간 초과 미션 자동 완료 (안전망) |
| 전날 데이터 포함으로 BFF 응답 크기 증가 | IN_PROGRESS는 최대 1건이므로 영향 미미 |
| 고정 미션(DailyMissionInstance)의 날짜 전환 로직 | 고정 미션은 매일 새 인스턴스 생성, 전날 인스턴스가 IN_PROGRESS면 동일 로직 적용 |
