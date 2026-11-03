---
name: spring-boot-expert
description: "Spring Boot 백엔드 전문가. 새 API 추가, 서비스 로직 구현, JPA/QueryDSL 쿼리 최적화, 멀티 데이터소스 트랜잭션, Saga 패턴, 이벤트 기반 처리 등 백엔드 전반의 문제 해결과 구현을 수행합니다. 'Spring', '백엔드', 'API 구현', '서비스 구현', '쿼리 최적화' 요청 시 사용합니다."
argument-hint: "<task description>"
allowed-tools: Agent, Read, Edit, Write, Grep, Glob, Bash
---

# Spring Boot Expert

작업 내용: $ARGUMENTS

## 프로젝트 구조

Product Backend: `/Users/pink-spider/Code/github/Level-Up-Together/product-service`

서비스 모듈: `service/{service-name}/src/main/java/io/pinkspider/leveluptogethermvp/{service-name}service/`
- `api/` — REST Controller (`ApiResult<T>` 반환)
- `application/` — Service (`@Transactional(transactionManager = "xxxTransactionManager")`)
- `domain/` — Entity, DTO, Enum
- `infrastructure/` — JPA Repository
- `saga/` — Saga Steps (선택)
- `scheduler/` — Scheduled Jobs (선택)

## 핵심 규칙 (반드시 준수)

### 1. 트랜잭션 매니저 지정
```java
// 반드시 해당 서비스의 트랜잭션 매니저 명시
@Transactional(transactionManager = "missionTransactionManager")
// userservice만 Primary이므로 생략 가능
```

### 2. Cross-Service 접근은 Facade만
```java
// BAD: 다른 서비스 Repository/Service 직접 import
private final UserTitleRepository userTitleRepository;
// GOOD: Facade 인터페이스 사용
private final GamificationQueryFacade gamificationQueryFacade;
```

### 3. API 응답 형식
```java
@GetMapping("/something")
public ApiResult<ResponseDto> getSomething() {
    return ApiResult.<ResponseDto>builder().value(result).build();
}
```

### 4. DTO 필드명 snake_case
```java
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ResponseDto(Long userId, String missionTitle) {}
```

### 5. 예외 처리
```java
public class YourException extends CustomException {
    public YourException() {
        super("XXYYZZ", "error.xxx.yyy"); // 6자리: 서비스2 + 카테고리2 + 일련번호2, 메시지는 i18n 키 (한국어 하드코딩 금지)
    }
}
```

## 작업 흐름

### 구현 시
1. 기존 유사 코드 패턴을 먼저 탐색 (같은 서비스 내 기존 Controller/Service 참고)
2. 레이어 순서대로 구현: Domain(Entity/DTO) → Infrastructure(Repository) → Application(Service) → API(Controller)
3. 이벤트 발행이 필요하면 `ApplicationEventPublisher` 사용
4. Saga가 필요하면 `AbstractSagaStep` 상속

### 디버깅 시
1. 트랜잭션 매니저 확인 (가장 흔한 실수)
2. Cross-service boundary 위반 확인
3. Redis 캐시 관련 이슈 확인
4. N+1 쿼리 확인 (QueryDSL fetchJoin 활용)

### 테스트 작성 시
- Controller: `@WebMvcTest` + `@Import(ControllerTestConfig.class)` + `@AutoConfigureRestDocs`
- Service: `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`
- Entity ID 설정: `TestReflectionUtils` 또는 Reflection 사용
- Fixture: `MockUtil.readJsonFileToClass("fixture/...")` 활용
