---
name: arch-review
description: "프로젝트 아키텍처 규칙 준수 여부를 검사합니다. 코드 변경 후, PR 전, 또는 '아키텍처 검사', '규칙 위반 확인' 요청 시 사용합니다."
argument-hint: "[service-name or file-path]"
allowed-tools: Read, Grep, Glob, Bash
---

# Architecture Compliance Review

대상 서비스 또는 파일: $ARGUMENTS (없으면 최근 변경된 파일 대상으로 검사)

아래 5가지 규칙을 순서대로 검사하고, 위반 사항을 표로 정리하세요.

## 1. Cross-Service Boundary 위반

다른 서비스의 Repository나 Service를 직접 import하면 위반입니다.
반드시 Facade 인터페이스(UserQueryFacade, GuildQueryFacade, GamificationQueryFacade)를 통해 접근해야 합니다.

검사 방법:
- 각 서비스 디렉토리에서 다른 서비스의 `infrastructure/` 또는 `application/` 패키지를 import하는지 확인
- `import io.pinkspider.leveluptogethermvp.{다른서비스}service.infrastructure.` 패턴 탐색
- `import io.pinkspider.leveluptogethermvp.{다른서비스}service.application.` 패턴 탐색
- Entity/Enum import는 허용 (현행 유지)

## 2. Transaction Manager 지정 누락

멀티 데이터소스 환경에서 `@Transactional`에 트랜잭션 매니저를 명시하지 않으면 `userTransactionManager`(Primary)가 사용되어 다른 서비스에서 데이터가 저장되지 않습니다.

서비스별 올바른 트랜잭션 매니저:
| 서비스 | 트랜잭션 매니저 |
|--------|--------------|
| userservice | userTransactionManager |
| missionservice | missionTransactionManager |
| guildservice | guildTransactionManager |
| chatservice | chatTransactionManager |
| metaservice | metaTransactionManager |
| feedservice | feedTransactionManager |
| notificationservice | notificationTransactionManager |
| adminservice | adminTransactionManager |
| gamificationservice | gamificationTransactionManager |

검사 방법:
- `@Transactional` 어노테이션에 `transactionManager` 속성이 없는 경우 탐색
- userservice는 Primary이므로 생략 가능 (위반 아님)
- `@Transactional(readOnly = true)`만 있고 매니저 미지정인 경우도 포함

## 3. API Response 규칙

모든 REST 컨트롤러는 `ApiResult<T>`를 반환해야 합니다.

검사 방법:
- `@RestController` 클래스의 public 메서드가 `ApiResult`로 감싸지 않은 반환 타입을 사용하는지 확인
- `ResponseEntity`, 원시 타입 직접 반환은 위반

## 4. Layer 구조 위반

각 서비스는 api -> application -> domain -> infrastructure 방향으로만 의존해야 합니다.

검사 방법:
- `api/` 패키지에서 `infrastructure/` 직접 import 금지
- `domain/` 패키지에서 `application/` 또는 `api/` import 금지
- `infrastructure/` 패키지에서 `api/` import 금지

## 5. DTO 필드명 규칙

프론트엔드와 통신하는 DTO의 JSON 필드명은 snake_case여야 합니다.

검사 방법:
- `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` 또는 `@JsonProperty("snake_case")` 사용 여부 확인
- record 타입 DTO에 snake_case 설정이 없는 경우 경고

## 결과 출력 형식

```
## Architecture Review 결과

### 검사 대상
- 서비스: {service-name}
- 파일 수: {count}

### 위반 사항
| # | 규칙 | 파일 | 라인 | 내용 | 심각도 |
|---|------|------|------|------|--------|
| 1 | Cross-Service | FooService.java | 15 | BarRepository 직접 import | HIGH |

### 요약
- HIGH: {n}건 (즉시 수정 필요)
- MEDIUM: {n}건 (수정 권장)
- LOW: {n}건 (참고)

위반 없음이면: "모든 규칙을 준수하고 있습니다."
```
