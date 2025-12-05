# Level Up Together MVP

함께 성장하는 미션 기반 커뮤니티 플랫폼의 백엔드 서비스입니다.

## 기술 스택

- **Framework**: Spring Boot 3.4.5, Spring Cloud 2024.0.0
- **Language**: Java 24
- **Build**: Gradle 8.14.3
- **Database**: PostgreSQL (Production), H2 (Test)
- **Cache**: Redis (Lettuce)
- **Messaging**: Apache Kafka
- **API**: REST + GraphQL (Netflix DGS)
- **Documentation**: Spring REST Docs + OpenAPI 3.0

## 아키텍처

Multi-Service Monolith 구조로, 단일 배포 단위 내에서 서비스별로 독립된 데이터베이스를 사용합니다.

```
src/main/java/io/pinkspider/
├── leveluptogethermvp/
│   ├── userservice/      # 인증, OAuth2, JWT, 사용자 관리
│   ├── metaservice/      # 공통코드, 메타데이터, Redis 캐싱
│   ├── missionservice/   # 미션 정의 및 진행 관리
│   ├── guildservice/     # 길드 생성 및 멤버 관리
│   ├── loggerservice/    # 이벤트 로깅, MongoDB, Kafka
│   └── bff/              # Backend-for-Frontend 집계 레이어
└── global/               # 공통 설정, 보안, 예외처리, 인프라
```

## 시작하기

### 요구사항

- JDK 24
- Gradle 8.14+
- PostgreSQL / Redis / Kafka (또는 test 프로필 사용)

### 빌드 및 실행

```bash
# 빌드
./gradlew clean build

# 테스트 실행
./gradlew test

# 애플리케이션 실행 (기본 포트: 8443)
./gradlew bootRun

# 테스트 프로필로 실행 (포트: 18080, H2 인메모리 DB)
./gradlew bootRun --args='--spring.profiles.active=test'
```

### API 문서 생성

```bash
./gradlew openapi3 && ./gradlew sortOpenApiJson && ./gradlew copySortedOpenApiJson
```

## 주요 기능

### 인증 (User Service)
- OAuth2 소셜 로그인 (Google, Kakao, Apple)
- JWT 기반 토큰 인증
- 멀티 디바이스 토큰 지원
- 약관 동의 관리

### 미션 (Mission Service)
- 미션 생성 및 관리
- 미션 진행 상태 추적

### 길드 (Guild Service)
- 길드 생성 및 관리
- 멤버 가입/탈퇴 관리

### 메타데이터 (Meta Service)
- 공통 코드 관리 (Redis 캐싱)
- 캘린더 휴일 정보

## 환경 설정

| 프로필 | 설명 |
|--------|------|
| `test` | H2 인메모리 DB, 테스트용 Kafka |
| `local` | Config Server 연동 |
| `dev` | 개발 서버 환경 |
| `prod` | 운영 서버 환경 |

## 모니터링

- **Actuator**: `/showmethemoney`
- **Tracing**: Zipkin 연동
- **Metrics**: Micrometer + Prometheus

## CI/CD

- `main` 브랜치 → Production 배포 (테스트 포함)
- `develop` 브랜치 → Dev 배포
- Slack 알림 연동
