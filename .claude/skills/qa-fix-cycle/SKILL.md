---
name: qa-fix-cycle
description: "QA 이슈 수정 후 워크플로우를 자동화합니다. 브랜치 생성, 커밋 메시지 규칙 적용, 테스트 실행, Jira 상태 변경, 크로스 프로젝트 체크리스트를 처리합니다. 'QA 수정 완료', 'QA 커밋', 'QA 마무리', 'QA-XX 완료' 요청 시 사용합니다."
argument-hint: "<QA issue key> (e.g., QA-7)"
allowed-tools: Agent, Read, Edit, Write, Grep, Glob, Bash, mcp__plugin_atlassian_atlassian__getJiraIssue, mcp__plugin_atlassian_atlassian__addCommentToJiraIssue, mcp__plugin_atlassian_atlassian__transitionJiraIssue, mcp__plugin_atlassian_atlassian__getTransitionsForJiraIssue
---

# QA Fix Cycle

대상 이슈: $ARGUMENTS

## 1단계: 현재 상태 파악

### 1-1. Jira 이슈 확인
- `getJiraIssue`로 이슈 정보 조회 (제목, 상태, 담당자)
- 이슈 상태가 "해야 할 일"이면 "진행 중"으로 전환 제안

### 1-2. Git 상태 확인
```bash
git status
git branch --show-current
git log --oneline -5
```

확인 사항:
- 현재 브랜치가 `feature/QA-{번호}`인지
- 커밋되지 않은 변경사항이 있는지
- 이미 QA 관련 커밋이 있는지

### 1-3. 변경 파일 분석
```bash
git diff --name-only          # unstaged
git diff --cached --name-only # staged
git diff develop --name-only  # develop 대비 전체 변경
```

## 2단계: 브랜치 관리

### 브랜치 네이밍 규칙
- 패턴: `feature/QA-{번호}` (예: `feature/QA-7`)
- develop 브랜치에서 분기

### 브랜치가 없는 경우
```bash
git checkout develop
git pull origin develop
git checkout -b feature/QA-{번호}
```

### 이미 브랜치가 있는 경우
- 현재 브랜치 확인 후 진행

## 3단계: 변경 범위 분석

### 3-1. 영향받는 프로젝트 식별

변경된 파일 경로를 기반으로 영향받는 프로젝트를 판단:

| 경로 패턴 | 프로젝트 |
|----------|---------|
| `product-service/` | Product Backend |
| `level-up-together-frontend/` | Web Frontend |
| `level-up-together-admin-frontend/` | Admin Frontend |
| `LevelUpTogetherReactNative/` | React Native App |
| `admin-service/` | Admin Backend |
| `level-up-together-platform/` | Platform Library |
| `level-up-together-sql/` | SQL Scripts |

### 3-2. 크로스 프로젝트 체크리스트

다중 프로젝트 수정인 경우 체크리스트를 생성:

```markdown
## 크로스 프로젝트 변경 체크리스트

### Backend (product-service)
- [ ] 코드 수정 완료
- [ ] 단위 테스트 통과
- [ ] 트랜잭션 매니저 확인
- [ ] Facade 패턴 준수

### Frontend (level-up-together-frontend)
- [ ] API 호출 수정 완료
- [ ] 타입 정의 업데이트
- [ ] 로컬 동작 확인

### Platform (level-up-together-platform)
- [ ] 변경사항 커밋 & 푸시
- [ ] product-service CI 재실행 대기
```

## 4단계: 테스트 실행

### 4-1. 변경 파일 기반 테스트 대상 식별

변경된 서비스 파일에서 테스트 클래스명 추출:
- `*Service.java` → `*ServiceTest.java`
- `*Controller.java` → `*ControllerTest.java`

### 4-2. 테스트 실행
```bash
# 변경된 서비스의 테스트만 실행
./gradlew :service:test --tests "*.AffectedServiceTest"

# 전체 빌드 검증 (선택)
./gradlew clean build
```

### 4-3. 테스트 실패 시
- 실패 원인 분석
- 수정 후 재실행
- 사용자에게 결과 보고

## 5단계: 커밋

### 커밋 메시지 규칙

**`.claude/commands/commit.md` 전역 컨벤션을 따른다.**

**형식**: `{type}: [QA-{번호}] {한글 설명}` (50자 이내, 한글, "with claude" 푸터 금지)

**type 결정:**
| 변경 내용 | type |
|----------|------|
| 버그 수정 | `fix` |
| 리팩터링 | `refactor` |
| 기능 추가 | `feat` |
| 설정 변경 | `chore` |

**예시:**
```
fix: [QA-7] 캘린더 월 전환 시 이전 월 선택 초기화
refactor: [QA-5] 미션 실행 라이프사이클 통합
fix: [QA-12] 완료된 일반 미션이 다음날 재출현 버그 수정
```

**본문 (대규모 변경 시):**
```
refactor: [QA-5] 미션 실행 라이프사이클 통합

- Phase 1: 인스턴스 조회 로직 분리
- Phase 2: instanceId 파라미터 추가
- Phase 3: Strategy 패턴 적용
- Phase 4: 상태 전이 검증 강화
```

> 브랜치 이름은 여전히 `feature/QA-{번호}`로 유지(2단계 참고). 다만 커밋 제목에는 브랜치명을 포함하지 않는다 — JIRA 번호만 대괄호로 표기.

### 커밋 실행
```bash
git add {변경 파일들}
git commit -m "{type}: [QA-{번호}] {설명}"
```

### 여러 프로젝트 동시 작업 시
프로젝트별로 각각 별도의 커밋 메시지를 제안한다 (백엔드/프론트엔드/RN 등 분리).

## 6단계: Jira 상태 업데이트

### 상태 전환
1. `getTransitionsForJiraIssue`로 가능한 전환 목록 조회
2. 적절한 전환 실행:
   - 수정 완료: "진행 중" → "완료" 또는 "검증 대기"
   - 추가 작업 필요: 댓글로 진행 상황 공유

### Jira 댓글 템플릿
```
## 수정 완료

### 변경 내용
- {변경 요약}

### 변경 파일
- `{파일1}` — {변경 설명}
- `{파일2}` — {변경 설명}

### 테스트
- 단위 테스트: ✅ 통과 ({N}개)
- 빌드: ✅ 성공

### 브랜치
- `feature/QA-{번호}`
```

## 7단계: 결과 요약

```markdown
## QA-{번호} Fix Cycle 완료

### 처리 내역
- 브랜치: feature/QA-{번호}
- 커밋: {커밋 해시} - {커밋 메시지}
- 테스트: {통과/실패} ({N}개)
- Jira 상태: {이전} → {이후}

### 영향받는 프로젝트
- [x] Product Backend
- [ ] Web Frontend (추가 수정 필요 시 표시)

### 다음 단계
- [ ] PR 생성 (develop ← feature/QA-{번호})
- [ ] 코드 리뷰
- [ ] staging 배포 후 검증
```
