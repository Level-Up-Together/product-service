# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
# Build
./gradlew clean build

# Run tests
./gradlew test

# Run single test class
./gradlew test --tests "io.pinkspider.leveluptogethermvp.userservice.oauth.api.Oauth2ControllerTest"

# Run single test method
./gradlew test --tests "*.Oauth2ControllerTest.getOauth2LoginUri"

# Run application
./gradlew bootRun                                    # Default (port 8443)
./gradlew bootRun --args='--spring.profiles.active=test'  # Test profile (port 18080)

# Generate API documentation
./gradlew openapi3 && ./gradlew sortOpenApiJson && ./gradlew copySortedOpenApiJson

# Generate GraphQL classes from DGS schema
./gradlew generateJava
```

## Architecture Overview

**Multi-Service Monolith**: Spring Boot 3.4.5 application with 6 service modules sharing a single deployment unit but using separate databases per service.

### Service Modules (`src/main/java/io/pinkspider/leveluptogethermvp/`)

| Service | Purpose |
|---------|---------|
| `userservice` | OAuth2 authentication (Google, Kakao, Apple), JWT token management, user profiles, terms |
| `metaservice` | Common codes, calendar holidays, Redis-cached metadata |
| `missionservice` | Mission definition and progress tracking |
| `guildservice` | Guild/group creation and member management |
| `loggerservice` | Event logging with MongoDB and Kafka |
| `bff` | Backend-for-Frontend API aggregation |

### Global Infrastructure (`src/main/java/io/pinkspider/global/`)

- **Multi-datasource**: Separate databases (user_db, mission_db, guild_db, meta_db, saga_db) with Hikari pooling
- **Security**: JWT filter (`JwtAuthenticationFilter`), OAuth2 providers
- **Caching**: Redis with Lettuce client
- **Messaging**: Kafka topics (loggerTopic, httpLoggerTopic, alimTalkTopic, appPushTopic, emailTopic, userCommunicationTopic)
- **Exception Handling**: Extend `CustomException` from `io.pinkspider.global.exception`
- **Encryption**: `CryptoConverter` for sensitive field encryption
- **Tracing**: Zipkin/Brave distributed tracing
- **Monitoring**: Actuator at `/showmethemoney`
- **Saga Pattern**: `io.pinkspider.global.saga` - 분산 트랜잭션 관리 (MSA 전환 대비)

### Transaction Manager 주의사항

멀티 데이터소스 환경에서 `userTransactionManager`가 `@Primary`로 설정되어 있으므로, **각 서비스의 `@Transactional`에 명시적으로 트랜잭션 매니저를 지정해야 함**:

```java
// GuildService 예시
@Transactional(transactionManager = "guildTransactionManager")
public void updateGuild(...) { ... }

// MissionService 예시
@Transactional(transactionManager = "missionTransactionManager")
public void updateMission(...) { ... }
```

트랜잭션 매니저 매핑:
| Service | Transaction Manager |
|---------|---------------------|
| userservice | `userTransactionManager` (Primary) |
| missionservice | `missionTransactionManager` |
| guildservice | `guildTransactionManager` |
| metaservice | `metaTransactionManager` |
| saga | `sagaTransactionManager` |

### Service Layer Pattern

Each service module follows a consistent layered structure:
- `api/` - REST controllers returning `ApiResult<T>` wrapper
- `application/` - Business logic services with `@Transactional`
- `domain/` - Entities, DTOs, enums
- `infrastructure/` - JPA repositories

### API Response Format

All REST endpoints return `ApiResult<T>` from `io.pinkspider.global.api`:
```java
{
  "code": "0000",      // ApiStatus code
  "message": "success",
  "value": { ... }     // Response payload
}
```

### Key Technologies

- GraphQL via Netflix DGS (schemas in `src/main/resources/schema/*.graphqls`)
- REST API with Spring REST Docs + OpenAPI 3.0
- QueryDSL for type-safe queries (generated in `src/main/generated/querydsl`)
- Resilience4j for circuit breaker
- Pact for contract testing

## Database Configuration

- **Production**: PostgreSQL with SSH tunnel support
- **Testing**: H2 in-memory with `MODE=PostgreSQL`
- **JPA**: `create-drop` in test, `validate` in production

Each service has its own datasource configuration in `io.pinkspider.global.config.datasource`.

## Testing

Tests exclude classes in `io/pinkspider/global/**`.

### Test Fixtures
JSON fixtures in `src/test/resources/fixture/{servicename}/` are loaded via `MockUtil`:
```java
MockUtil.readJsonFileToClass("fixture/userservice/oauth/mockCreateJwtResponseDto.json", CreateJwtResponseDto.class);
```

### Controller Tests
Use `@WebMvcTest` with `ControllerTestConfig` for isolated controller testing:
```java
@WebMvcTest(controllers = YourController.class, excludeAutoConfiguration = {...})
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
```

### Unit Tests (Service Layer)
Use `@ExtendWith(MockitoExtension.class)` for service layer unit testing:
```java
@ExtendWith(MockitoExtension.class)
class YourServiceTest {
    @Mock
    private YourRepository repository;

    @InjectMocks
    private YourService service;

    // Reflection을 사용하여 엔티티 ID 설정 (JPA 엔티티는 ID가 auto-generated)
    private void setEntityId(Entity entity, Long id) {
        try {
            Field idField = Entity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

## CI/CD

- Push to `main` → Production deployment with tests
- Push to `develop` → Dev deployment without tests
- Swagger docs updated on both environments
- Slack notifications on deployment status

## Configuration Profiles

- `application.yml` - Default configuration
- `application-test.yml` - H2 databases, test Kafka
- `application-local.yml` - Config server integration
- `application-dev.yml` / `application-prod.yml` - Environment-specific


# Project Context
## 프로젝트 구조
- admin backend = /Users/pink-spider/Code/github/Level-Up-Together/level-up-together-mvp-admin
- admin front = /Users/pink-spider/Code/github/Level-Up-Together/level-up-together-admin-frontend
- product backend = /Users/pink-spider/Code/github/Level-Up-Together/level-up-together-mvp 
- product front = /Users/pink-spider/Code/github/Level-Up-Together/level-up-together-frontend
- sql = /Users/pink-spider/Code/github/Level-Up-Together/level-up-together-sql/queries
- config = /Users/pink-spider/Code/github/Level-Up-Together/config-repository

## 코드 컨벤션
- 프론트와 백엔드간의 통신은 필드들이 snake case 로 한다.
- 들여쓰기: 4 spaces
- DTO는 record 사용 권장
- Controller → Service → Repository 레이어 구조
- 테스트: JUnit 5 + Mockito

## 작업 완료 시 규칙
- 작업이 끝나면 반드시 변경 있는 프로젝트의 커밋 메시지를 생성해줄 것(커밋은 내가 직접)
- 커밋 메시지 형식: `feat|fix|refactor: 간단한 설명`
- 한글로 커밋 메시지 작성
- 새로 작성된 코드에 대한 테스트 코드 병행 작성 필수
- 필요시 CLAUDE.md, README.md 업데이트

## 자주 발생하는 이슈

### QueryDSL 빌드 오류
`Attempt to recreate a file for type Q*` 오류 발생 시:
```bash
./gradlew clean compileJava
```

### 트랜잭션 매니저 미지정 오류
데이터가 저장되지 않거나 조회되지 않는 경우, `@Transactional`에 올바른 트랜잭션 매니저가 지정되어 있는지 확인