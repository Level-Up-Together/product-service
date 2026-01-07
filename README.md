# Level Up Together MVP

함께 성장하는 미션 기반 커뮤니티 플랫폼의 백엔드 서비스입니다.

## 기술 스택

- **Framework**: Spring Boot 3.4.5, Spring Cloud 2024.0.0
- **Language**: Java 21 (JDK 25 빌드 호환)
- **Build**: Gradle 8.14.3
- **Database**: PostgreSQL (Production), H2 (Test)
- **Cache**: Redis (Lettuce)
- **Messaging**: Apache Kafka
- **API**: REST + GraphQL (Netflix DGS)
- **Documentation**: Spring REST Docs + OpenAPI 3.0
- **Query**: QueryDSL (타입 안전 쿼리)
- **Resilience**: Resilience4j (Circuit Breaker)

## 아키텍처

Multi-Service Monolith 구조로, 단일 배포 단위 내에서 서비스별로 독립된 데이터베이스를 사용합니다.

```
src/main/java/io/pinkspider/
├── leveluptogethermvp/
│   ├── userservice/      # 인증, OAuth2, JWT, 사용자 관리, 경험치/레벨
│   ├── metaservice/      # 공통코드, 메타데이터, Redis 캐싱
│   ├── missionservice/   # 미션 정의, 진행 관리, Saga 오케스트레이션
│   ├── guildservice/     # 길드 생성, 멤버 관리, 길드 경험치
│   ├── loggerservice/    # 이벤트 로깅, MongoDB, Kafka
│   └── bff/              # Backend-for-Frontend 집계 레이어
└── global/
    ├── config/           # 멀티 데이터소스, 보안, 캐시 설정
    ├── saga/             # Saga 패턴 인프라 (MSA 전환 대비)
    ├── exception/        # 공통 예외 처리
    └── security/         # JWT, OAuth2 보안 필터
```

## 데이터베이스 구조

서비스별 독립된 데이터베이스를 사용하며, 각 데이터소스는 별도의 Transaction Manager를 가집니다.

| 데이터베이스 | 서비스 | Transaction Manager |
|-------------|--------|---------------------|
| `user_db` | userservice | `userTransactionManager` (Primary) |
| `mission_db` | missionservice | `missionTransactionManager` |
| `guild_db` | guildservice | `guildTransactionManager` |
| `meta_db` | metaservice | `metaTransactionManager` |
| `saga_db` | saga (global) | `sagaTransactionManager` |

## 시작하기

### 요구사항

- JDK 21 (또는 JDK 25 with toolchain)
- Gradle 8.14+
- PostgreSQL / Redis / Kafka (또는 test 프로필 사용)

### 빌드 및 실행

```bash
# 빌드
./gradlew clean build

# 테스트 실행
./gradlew test

# 단일 테스트 클래스 실행
./gradlew test --tests "io.pinkspider.leveluptogethermvp.userservice.oauth.api.Oauth2ControllerTest"

# 단일 테스트 메서드 실행
./gradlew test --tests "*.Oauth2ControllerTest.getOauth2LoginUri"

# 애플리케이션 실행 (기본 포트: 8443)
./gradlew bootRun

# 테스트 프로필로 실행 (포트: 18080, H2 인메모리 DB)
./gradlew bootRun --args='--spring.profiles.active=test'
```

### API 문서 생성

```bash
./gradlew openapi3 && ./gradlew sortOpenApiJson && ./gradlew copySortedOpenApiJson
```

### GraphQL 클래스 생성

```bash
./gradlew generateJava
```

## 주요 기능

### 인증 (User Service)
- OAuth2 소셜 로그인 (Google, Kakao, Apple)
- JWT 기반 토큰 인증
- 멀티 디바이스 토큰 지원
- 약관 동의 관리
- 경험치/레벨 시스템
- 친구 관리 (친구 요청/수락/거절)
- 업적/칭호 시스템 (LEFT+RIGHT 조합 방식)
- 출석 체크 (연속 출석 보너스)
- 퀘스트 (일일/주간)
- 활동 피드 (좋아요, 댓글)
- 알림 시스템 (인앱, 푸시)
- 마이페이지 (프로필, 통계)

### 미션 (Mission Service)
- 미션 생성 및 관리 (일일/주간/월간 인터벌)
- 미션 카테고리 (시스템 카테고리 + 사용자 정의)
- 미션 참가자 진행 상태 추적
- 미션 실행 스케줄 자동 생성
- 미션 완료 시 경험치 지급
- 미션북 (시스템 미션 라이브러리)
- Saga 패턴 기반 분산 트랜잭션 관리

### 길드 (Guild Service)
- 길드 생성 및 관리
- 멤버 가입/탈퇴/추방 관리
- 길드 경험치/레벨 시스템
- 길드 게시판 (공지사항, 일반 게시글)
- 길드 채팅
- 길드 거점 시스템 (지도 기반)
- 길드원 칭호 색상 표시

### 메타데이터 (Meta Service)
- 공통 코드 관리 (Redis 캐싱)
- 캘린더 휴일 정보
- 레벨 설정 관리
- 추천 플레이어/길드/피드 관리
- 비속어 필터링

### BFF (Backend-for-Frontend)
- 홈 화면 데이터 집계
- 다중 서비스 데이터 조합

## API 응답 형식

모든 REST 엔드포인트는 `ApiResult<T>` 래퍼를 사용합니다:

```json
{
  "code": "0000",
  "message": "success",
  "value": { ... }
}
```

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
- `develop` 브랜치 → Dev 배포 (테스트 스킵)
- Swagger 문서 자동 업데이트
- Slack 알림 연동

## 관련 프로젝트

| 프로젝트 | 설명 |
|---------|------|
| `level-up-together-frontend` | 사용자 앱 프론트엔드 (Next.js) |
| `level-up-together-mvp-admin` | 어드민 백엔드 (Spring Boot) |
| `level-up-together-admin-frontend` | 어드민 프론트엔드 (Next.js) |
| `level-up-together-sql` | SQL 스크립트 (DDL/DML) |
| `config-repository` | Spring Cloud Config 저장소 |

## 개발 서버
- https://dev.level-up-together.com:3000