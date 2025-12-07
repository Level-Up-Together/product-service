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

- **Multi-datasource**: Separate databases (user_db, mission_db, guild_db, meta_db) with Hikari pooling
- **Security**: JWT filter (`JwtAuthenticationFilter`), OAuth2 providers
- **Caching**: Redis with Lettuce client
- **Messaging**: Kafka topics (loggerTopic, httpLoggerTopic, alimTalkTopic, appPushTopic, emailTopic, userCommunicationTopic)
- **Exception Handling**: Extend `CustomException` from `io.pinkspider.global.exception`
- **Encryption**: `CryptoConverter` for sensitive field encryption
- **Tracing**: Zipkin/Brave distributed tracing
- **Monitoring**: Actuator at `/showmethemoney`

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