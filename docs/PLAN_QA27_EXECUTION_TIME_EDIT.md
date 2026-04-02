# Plan: QA-27 수행한 미션 데이터 수정 기능

## 이슈 요약

- **Jira**: QA-27
- **제목**: [App] 수행한미션 데이터 수정 기능추가
- **우선순위**: High
- **보고자**: rumi

### 요청사항

1. **monthly API 응답에 `started_at` 필드 추가** — 현재 프론트에서 `completed_at - exp_earned`로 시작시간 역산 중. 미션북/길드미션 추가경험치로 부정확.
2. **완료된 미션의 시작/종료 시간 수정 API** — 경험치는 유지한 채 시간만 수정. 프론트 UI(TimeEditSheet) 구현 완료.

## 현재 상태

| 항목 | 상태 |
|------|------|
| MissionExecution.startedAt | DB 컬럼 있음 |
| DailyMissionInstance.startedAt | DB 컬럼 있음 |
| MonthlyCalendarResponse.DailyMission.startedAt | **없음** (DTO 누락) |
| 시간 수정 백엔드 API | **없음** |
| 프론트 TimeEditSheet UI | 구현 완료 (`// TODO: 백엔드 API 연동`) |

## Phase 1: monthly API 응답에 started_at 추가

### 1-1. DailyMission DTO에 startedAt 필드 추가
- **파일**: `service/mission-service/.../domain/dto/MonthlyCalendarResponse.java`
- **변경**: `DailyMission` 내부 클래스에 `private LocalDateTime startedAt;` 추가

### 1-2. 서비스 매핑 추가
- **파일**: `service/mission-service/.../application/MissionExecutionQueryService.java`
- **변경**: `getMonthlyCalendarData()` 메서드에서 DailyMission 빌드 시 `.startedAt(execution.getStartedAt())` 매핑 추가 (일반 미션 + 고정 미션 모두)

### 1-3. 프론트엔드 DailyMission 타입
- **파일**: `level-up-together-frontend/src/lib/api/mission/mission-api.ts`
- **확인**: `DailyMission` 인터페이스에 `started_at: string | null` 이미 있는지 확인 → 있으면 변경 불필요

### 1-4. 테스트
- 기존 monthly API 컨트롤러 테스트에 started_at 필드 검증 추가

## Phase 2: 미션 시간 수정 API 추가

### 2-1. Request DTO 생성
- **파일**: `service/mission-service/.../domain/dto/ExecutionTimeUpdateRequest.java`
- **필드**:
  - `LocalDateTime startedAt` (필수)
  - `LocalDateTime completedAt` (필수)
- **검증**: startedAt < completedAt

### 2-2. 서비스 메서드 추가
- **파일**: `service/mission-service/.../application/MissionExecutionService.java`
- **메서드**: `updateExecutionTime(Long missionId, String userId, LocalDate executionDate, ExecutionTimeUpdateRequest request)`
- **로직**:
  1. 해당 날짜의 COMPLETED 상태 실행 기록 조회 (일반 미션 또는 고정 미션)
  2. startedAt, completedAt만 업데이트
  3. **expEarned는 변경하지 않음** (기존 값 유지)
  4. durationMinutes 재계산 (표시용)
- **트랜잭션**: `missionTransactionManager`

### 2-3. 컨트롤러 엔드포인트 추가
- **파일**: `service/mission-service/.../api/MissionExecutionController.java`
- **엔드포인트**: `PATCH /api/v1/missions/{missionId}/executions/{executionDate}/time`
- **파라미터**: `@PathVariable Long missionId`, `@PathVariable LocalDate executionDate`, `@RequestBody ExecutionTimeUpdateRequest request`, `@CurrentUser String userId`

### 2-4. 테스트
- 서비스 단위 테스트: 시간 수정 성공, 미존재 실행 에러, startedAt > completedAt 검증 에러
- 컨트롤러 RestDocs 테스트

## Phase 3: 프론트엔드 API 연동

### 3-1. API 함수 추가
- **파일**: `level-up-together-frontend/src/lib/api/mission/mission-api.ts`
- **함수**: `updateExecutionTime(missionId, date, startedAt, completedAt)`
- **엔드포인트**: `PATCH /api/v1/missions/{missionId}/executions/{date}/time`

### 3-2. TimeEditSheet 연동
- **파일**: `level-up-together-frontend/src/app/(afterLogin)/mission/components/TimeEditSheet.tsx`
- **변경**: `handleSave()`의 TODO 주석을 실제 API 호출로 교체
- 시간 선택값(hour/minute) → ISO datetime 변환 → API 호출
- 성공 시 캘린더 데이터 리프레시

## 실행 순서

```
Phase 1 (started_at 응답 추가) — 백엔드 DTO + 매핑
  ↓
Phase 2 (시간 수정 API) — 백엔드 신규 엔드포인트
  ↓
Phase 3 (프론트엔드 연동) — API 함수 + TimeEditSheet
```

## 리스크

| 리스크 | 대응 |
|--------|------|
| 시간 수정 시 duration_minutes와 exp_earned 불일치 | exp_earned 유지, duration은 재계산 (표시용) |
| 고정 미션(DailyMissionInstance)과 일반 미션(MissionExecution) 분기 | Strategy 패턴 기존 구조 활용 |
| 시간 수정 남용 (부정 사용) | 당일 수정만 허용하거나, 수정 횟수 제한 고려 (후순위) |
