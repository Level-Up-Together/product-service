---
name: create-agent
description: "새 Claude Code 에이전트를 생성합니다. 기존 에이전트 패턴을 분석하고 프로젝트 컨벤션에 맞는 에이전트 .md 파일을 생성합니다. '에이전트 만들어', 'agent 생성', '새 에이전트' 요청 시 사용합니다."
argument-hint: "<agent name and purpose> (e.g., 'migration-gen DB 마이그레이션 스크립트 생성')"
allowed-tools: Agent, Read, Write, Grep, Glob, Bash
---

# Agent Creator

요청: $ARGUMENTS

## 1단계: 요구사항 파악

사용자에게 다음을 확인한다:
- **에이전트 이름**: kebab-case (예: `test-gen`, `api-endpoint-gen`)
- **에이전트 목적**: 어떤 작업을 자율적으로 수행하는지
- **트리거 조건**: 어떤 상황에서 이 에이전트가 호출되어야 하는지
- **도구 범위**: 읽기 전용인지, 수정도 하는지

## 2단계: 기존 에이전트 패턴 분석

`.claude/agents/` 디렉토리의 기존 에이전트를 참고한다.

### 기존 에이전트 목록 (18개)

| 에이전트 | 용도 | model | tools 특징 |
|---------|------|-------|-----------|
| `api-endpoint-gen` | API 엔드포인트 일괄 생성 | sonnet | Read,Write,Edit,Grep,Glob,Bash |
| `api-doc-gen` | OpenAPI 문서 생성 | sonnet | Bash,Read,Grep,Glob |
| `test-gen` | 테스트 코드 생성 | sonnet | Read,Write,Edit,Grep,Glob,Bash |
| `http-test-gen` | HTTP 테스트 파일 생성 | sonnet | Read,Write,Edit,Grep,Glob |
| `service-scaffolder` | 서비스 모듈 스캐폴딩 | sonnet | Read,Write,Edit,Grep,Glob,Bash |
| `saga-step-gen` | Saga Step 생성 | sonnet | Read,Write,Edit,Grep,Glob,Bash |
| `event-wiring` | 이벤트 연결 생성 | sonnet | Read,Write,Edit,Grep,Glob,Bash |
| `entity-sync` | Entity↔DDL 동기화 | sonnet | Read,Write,Edit,Grep,Glob,Bash |
| `code-simplifier` | 코드 리팩터링 | inherit | Read,Edit,Grep,Glob,Bash 등 |
| `design-pattern-advisor` | 디자인 패턴 분석 | sonnet | Read,Grep,Glob (읽기 전용) |
| `cross-service-analyzer` | 서비스 간 의존성 분석 | sonnet | Read,Grep,Glob,Bash (읽기 전용) |
| `tx-checker` | 트랜잭션 매니저 검사 | sonnet | Read,Grep,Glob,Bash (읽기 전용) |
| `config-checker` | 설정 파일 검증 | sonnet | Read,Grep,Glob,Bash (읽기 전용) |
| `db-query` | DB 쿼리 실행 | sonnet | Read,Grep,Glob + MCP DB서버 |
| `sentry-investigator` | Sentry 에러 분석 | sonnet | Read,Edit,Grep,Glob,Bash |
| `test-runner` | 테스트 실행/분석 | sonnet | Bash,Read,Grep,Glob |
| `fullstack-sync` | API 변경 시 프론트 동기화 | sonnet | Read,Grep,Glob,Bash |
| `qa-investigate` | QA 이슈 조사/근본원인 분석 | sonnet | Read,Grep,Glob,Bash (읽기 전용) |

### 작업 로그 분석 (`prompt_log/claude_log_*.log`)

필요 시 로그 파일을 분석하여 자동화할 수 있는 반복 패턴을 추출한다.

```
prompt_log/claude_log_YYYY-MM-DD.log
형식: [HH:MM:SS] USER: ... / [HH:MM:SS] CLAUDE: ...
```

## 3단계: 에이전트 .md 파일 생성

### Frontmatter 규칙

```yaml
---
name: {kebab-case-name}
description: "{한글 설명}. {트리거 조건}."
tools: {도구 목록}
model: {sonnet | haiku | inherit}
maxTurns: {숫자}
---
```

**model 선택 가이드:**

| model | 용도 | 비용 |
|-------|------|------|
| `sonnet` | 대부분의 에이전트 (코드 생성, 분석, 검사) | 중간 |
| `haiku` | 단순 반복 작업 (포맷 변환, 검색) | 저렴 |
| `inherit` | 복잡한 판단이 필요한 에이전트 (리팩터링, 아키텍처) | 호출자에 따름 |

**maxTurns 가이드:**

| 작업 규모 | maxTurns |
|----------|----------|
| 단순 검사/분석 | 15-20 |
| 코드 생성 (단일 파일) | 20-30 |
| 코드 생성 (다중 파일) | 30-50 |
| 복잡한 리팩터링 | 40-60 |

**tools 선택 가이드:**

| 에이전트 유형 | tools |
|-------------|-------|
| 읽기 전용 분석 | `Read, Grep, Glob` |
| 분석 + 명령 실행 | `Read, Grep, Glob, Bash` |
| 코드 생성 | `Read, Write, Edit, Grep, Glob, Bash` |
| DB 조회 포함 | `Read, Grep, Glob` + `mcpServers` 사용 |

**mcpServers (DB 접근 시):**
```yaml
mcpServers:
  - postgres-user-db
  - postgres-mission-db
  - postgres-guild-db
  # 필요한 DB만 선택
```

**description 작성 규칙 (중요):**
- Claude가 자동으로 에이전트를 선택하는 기준이 됨
- 구체적인 트리거 조건 기술
- `<example>` 태그로 트리거 예시를 포함하면 더 정확하게 매칭됨 (code-simplifier 참고)

### 본문 구조 패턴

**Type A: 코드 생성 에이전트** (api-endpoint-gen, test-gen, saga-step-gen)
```markdown
당신은 이 프로젝트의 {역할} 전문가입니다.

## 프로젝트 구조
{관련 디렉토리/파일 구조}

## 생성 절차
### Step 1: 요구사항 확인
### Step 2: 기존 패턴 분석
### Step 3: 코드 생성
{템플릿 코드 포함}
### Step 4: 빌드 검증

## 컨벤션 체크리스트
- [ ] 규칙1
- [ ] 규칙2

## 주의사항
```

**Type B: 분석/검사 에이전트** (tx-checker, config-checker, design-pattern-advisor)
```markdown
당신은 이 프로젝트의 {역할} 전문가입니다.

## 검사 대상
{어떤 파일/코드를 검사하는지}

## 검사 규칙
### 규칙 1: ...
### 규칙 2: ...

## 검사 절차
1. 대상 파일 탐색
2. 규칙 위반 체크
3. 결과 보고

## 결과 출력 형식
{정형화된 보고서 포맷}
```

**Type C: 동기화/연결 에이전트** (entity-sync, event-wiring, fullstack-sync)
```markdown
당신은 이 프로젝트의 {역할} 전문가입니다.

## 소스-타겟 매핑
{무엇이 변경되면 무엇을 동기화하는지}

## 동기화 절차
1. 변경 감지
2. 영향 범위 파악
3. 동기화 코드 생성
4. 검증

## 프로젝트 경로
{관련 프로젝트 경로들}
```

### 프로젝트 핵심 규칙 (본문에 반드시 포함)

에이전트가 코드를 생성/수정하는 경우, 다음 규칙을 본문에 포함한다:

```markdown
## 프로젝트 핵심 규칙

### 트랜잭션 매니저
| 서비스 | 트랜잭션 매니저 |
|--------|---------------|
| userservice | userTransactionManager (Primary) |
| missionservice | missionTransactionManager |
| guildservice | guildTransactionManager |
| gamificationservice | gamificationTransactionManager |
| feedservice | feedTransactionManager |
| notificationservice | notificationTransactionManager |
| metaservice | metaTransactionManager |
| adminservice | adminTransactionManager |

### Cross-Service Boundary
다른 서비스의 Repository/Service 직접 사용 금지 → Facade 인터페이스 사용
- UserQueryFacade, GuildQueryFacade, GamificationQueryFacade

### API 컨벤션
- 응답: ApiResult<T> 래핑
- DTO 필드: snake_case (@JsonProperty)
- 예외: CustomException 상속, 6자리 코드
- 들여쓰기: 4 spaces
```

## 4단계: 파일 생성

파일 경로: `.claude/agents/{agent-name}.md`

## 5단계: 검증

생성된 에이전트가 올바른지 확인:
- [ ] frontmatter 필수 필드 (name, description, tools, model, maxTurns)
- [ ] description이 트리거 조건을 명확히 기술
- [ ] tools가 에이전트 목적에 맞게 설정 (읽기 전용 vs 수정 가능)
- [ ] model이 작업 복잡도에 맞게 선택
- [ ] maxTurns가 작업 규모에 적절
- [ ] 프로젝트 핵심 규칙이 포함 (코드 수정 에이전트의 경우)
- [ ] 생성 절차 또는 검사 규칙이 구체적

## Skill vs Agent 선택 가이드

사용자가 원하는 것이 스킬인지 에이전트인지 불분명한 경우:

| 구분 | Skill | Agent |
|------|-------|-------|
| **호출 방식** | `/skill-name` 또는 Claude 자동 매칭 | `Agent` 도구로 서브프로세스 실행 |
| **실행 컨텍스트** | 메인 대화에서 실행 | 독립 프로세스 (별도 컨텍스트) |
| **사용 사례** | 대화형 전문가, 단계별 가이드 | 자율적 작업 수행, 병렬 처리 |
| **예시** | spring-boot-expert, qa-investigate | test-gen, api-endpoint-gen |

**판단 기준:**
- 사용자와 대화하며 진행 → **Skill**
- 지시 후 자율 수행 → **Agent**
- 다른 에이전트에서 호출 → **Agent**
