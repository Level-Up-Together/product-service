# Global Service Roadmap

Level Up Together 서비스의 글로벌 대응을 위한 단계별 마이그레이션 계획.

## 현재 상태 요약

| 영역 | 상태 | 상세 |
|------|------|------|
| 날짜/시간 포맷 | ✅ 완료 | ISO 8601 (`2026-03-24T14:30:45`) — `@JsonFormat` 제거, JavaTimeModule 기본 출력 |
| 타임존 | ✅ 완료 (코드) | JVM/Hibernate/Jackson/JDBC 전부 UTC 설정 완료. DB 마이그레이션 SQL 작성 완료 (운영 미적용) |
| MessageSource | ✅ 완료 | `i18n/errors_ko.properties`, `errors_en.properties` + MessageConfig Bean |
| 에러 메시지 | ✅ 완료 | RestExceptionHandler → MessageSource 연동 + 100+ throw 사이트 메시지 키 전환 완료 |
| 푸시 알림 | ❌ 한국어 하드코딩 | `NotificationType` 45개 타입 (Phase 5) |
| 유저 언어 설정 | ✅ 완료 | `Users.preferredLocale` + `PUT /api/v1/mypage/preferred-locale` API |
| LocaleResolver | ✅ 완료 | `LocaleInterceptor` → Accept-Language 헤더 → `LocaleContextHolder` |
| 번역 서비스 | ✅ 코드 완료 | Feed + Guild 게시판 번역 통합 완료. `enabled: false` (운영 활성화 필요) |
| 금칙어 필터 | ✅ 완료 | `ProfanityWord.locale` 필드 추가, locale별 캐시/조회/CRUD 지원 |
| Locale 프레임워크 | ⚠️ 스켈레톤 | `SupportedLocale` enum (ko, en, ar) 존재 |

## 지원 언어 (계획)

| 코드 | 언어 | 우선순위 |
|------|------|---------|
| `ko` | 한국어 | 기본 (현재) |
| `en` | English | Phase 2~ |
| `ar` | العربية | Phase 3~ |

---

## Phase 1: 타임존 UTC 기반 통일

> **목표**: 모든 시간 데이터를 UTC로 저장하고, 클라이언트가 로컬 타임존으로 변환하는 구조 확립
>
> **영향 범위**: platform, product-service, admin-service, DB, 프론트엔드

### 1-1. JVM 타임존 UTC 고정

**파일**: `LevelUpTogetherMvpApplication.java`

```java
@PostConstruct
void setTimezone() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
}
```

### 1-2. Hibernate JDBC 타임존 설정

**파일**: `application.yml`

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
```

### 1-3. Jackson ISO 8601 + UTC Offset 출력

**파일**: `LmObjectMapper.java` 또는 `application.yml`

```yaml
spring:
  jackson:
    time-zone: UTC
```

이 설정으로 `LocalDateTime` → `"2026-03-24T14:30:45"` 출력 시 서버가 UTC임을 보장.
프론트엔드에서 `Z` suffix 또는 UTC 전제로 로컬 변환.

### 1-4. 기존 데이터 마이그레이션

현재 DB에 KST(`Asia/Seoul`, UTC+9)로 저장된 데이터를 UTC로 변환:

```sql
-- 각 서비스 DB별 실행
UPDATE {table} SET created_at = created_at - INTERVAL '9 hours';
UPDATE {table} SET modified_at = modified_at - INTERVAL '9 hours';
-- ... 모든 timestamp 컬럼 대상
```

### 1-5. 프론트엔드 대응

- API 응답의 모든 시간을 UTC로 간주
- 표시 시 `Intl.DateTimeFormat` 또는 `dayjs.tz()` 로 유저 로컬 타임존 변환
- React Native: `Date` 객체 + 디바이스 타임존 자동 적용

### 1-6. (선택) LocalDateTime → Instant 전환 검토

| 선택지 | 장점 | 단점 |
|--------|------|------|
| **A. LocalDateTime 유지 + UTC 고정** | 변경 최소, JPA 호환 | TZ 정보가 타입에 없음 |
| **B. Instant 전환** | 명시적 UTC, 글로벌 표준 | 180 엔티티 수정, DB 마이그레이션 |

**권장**: Phase 1에서는 **A안** (LocalDateTime + UTC 고정)으로 빠르게 적용.
향후 신규 엔티티부터 `Instant` 사용을 점진적으로 도입.

### Phase 1 체크리스트

- [x] JVM TimeZone UTC 설정 (`@PostConstruct` in Application classes)
- [x] Hibernate `jdbc.time_zone: UTC` 설정 (config-repository 전체 프로필)
- [x] Jackson `time-zone: UTC` 설정 (`LmObjectMapper` + `application.yml`)
- [x] JDBC URL `TimeZone=Asia/Seoul` → `TimeZone=UTC` 변경 (config-repository 전체)
- [x] DB 기존 데이터 KST → UTC 마이그레이션 SQL 작성 (`level-up-together-sql/queries/migration/V001__migrate_kst_to_utc.sql`)
- [x] 스케줄러 cron에 `zone = "Asia/Seoul"` 명시 (5개 스케줄러)
- [x] 테스트 fixture JSON 날짜 ISO-8601 변환 (product-service 12 + admin-service 3 파일)
- [x] 전체 테스트 통과 확인 (BUILD SUCCESSFUL)
- [ ] 프론트엔드 시간 표시 UTC 기반 변환 적용 (프론트엔드 팀 작업)
- [ ] DB 마이그레이션 SQL 운영 실행 (서비스 중단 후)

---

## Phase 2: i18n 인프라 구축

> **목표**: Spring MessageSource 기반 다국어 메시지 프레임워크 구축 + 유저 언어 설정 저장
>
> **영향 범위**: platform, product-service, admin-service

### 2-1. 유저 언어 설정 추가

**엔티티**: `Users`

```java
@Column(name = "preferred_locale", length = 5)
@Comment("선호 언어 (ko, en, ar)")
private String preferredLocale = "ko";
```

**API**: 프로필 설정에서 언어 변경 엔드포인트 추가

### 2-2. Request-Scoped Locale 처리

**LocaleResolver 설정**: `WebMvcConfig`

```
요청 흐름:
1. Authorization 헤더 → JWT → userId → preferredLocale (DB)
2. Accept-Language 헤더 (fallback)
3. 기본값: "ko"
```

경량 구현 — `LocaleContextHolder`에 설정하여 서비스 레이어에서 `MessageSource` 접근 가능.

### 2-3. MessageSource 설정

**디렉토리 구조**:

```
app/src/main/resources/
├── i18n/
│   ├── messages_ko.properties      # 한국어 (기본)
│   ├── messages_en.properties      # 영어
│   ├── messages_ar.properties      # 아랍어
│   ├── notifications_ko.properties # 알림 메시지
│   ├── notifications_en.properties
│   ├── notifications_ar.properties
│   ├── errors_ko.properties        # 에러 메시지
│   ├── errors_en.properties
│   └── errors_ar.properties
```

### 2-4. ApiStatus / CustomException 다국어 대응

**Before** (현재):

```java
throw new CustomException("030101", "존재하지 않는 사용자입니다.");
```

**After**:

```java
throw new CustomException("030101", "error.user.not_found");
// → MessageSource가 locale에 따라 메시지 해석
// ko: "존재하지 않는 사용자입니다."
// en: "User not found."
```

**RestExceptionHandler** 변경: `CustomException.message`를 메시지 키로 인식 → `MessageSource.getMessage()` 호출.

### 2-5. NotificationType 다국어 대응

**Before** (현재):

```java
WELCOME("가입 환영", "SYSTEM", "Level Up Together에 오신 것을 환영합니다!", ...)
```

**After**:

```java
WELCOME("notification.welcome.title", "SYSTEM", "notification.welcome.message", ...)
// NotificationType.formatTitle() → MessageSource에서 locale별 메시지 조회
```

### Phase 2 체크리스트

- [x] `Users` 엔티티에 `preferredLocale` 필드 추가 + DB 마이그레이션 SQL (`V002__add_preferred_locale.sql`)
- [x] 언어 설정 API 추가 (`PUT /api/v1/mypage/preferred-locale`)
- [x] `MessageSource` Bean 설정 (`MessageConfig.java`)
- [x] `LocaleInterceptor` → Accept-Language 헤더 → `LocaleContextHolder` 설정
- [x] `errors_ko.properties` 작성 (50+ 메시지 키)
- [x] `errors_en.properties` 작성 (영어 번역)
- [x] `RestExceptionHandler` MessageSource 연동 (`resolveMessage()` — 키 있으면 번역, 없으면 원문)
- [x] 전체 테스트 통과 확인
- [x] `CustomException` throw 코드를 메시지 키로 전환 완료 (40+ 소스 파일 + 17 테스트 파일, 100+ throw 사이트)
- [ ] `notifications_ko/en.properties` 작성 (Phase 5에서 진행)
- [ ] DB 마이그레이션 SQL 운영 실행

---

## Phase 3: 콘텐츠 번역 활성화

> **목표**: UGC(사용자 생성 콘텐츠) 및 시스템 콘텐츠 실시간 번역
>
> **영향 범위**: product-service (feedservice, guildservice, missionservice)

### 3-1. Google Translation API 활성화

```yaml
google:
  translation:
    enabled: true
    api:
      key: ${GOOGLE_TRANSLATION_API_KEY}
```

### 3-2. 번역 대상 콘텐츠

| 콘텐츠 | ContentType | 번역 전략 |
|--------|-------------|----------|
| 피드 본문 | `FEED` | 조회 시 on-demand 번역 + 캐시 |
| 피드 댓글 | `FEED_COMMENT` | 조회 시 on-demand 번역 + 캐시 |
| 길드 게시판 | `GUILD_POST` | 조회 시 on-demand 번역 + 캐시 |
| 길드 댓글 | `GUILD_COMMENT` | 조회 시 on-demand 번역 + 캐시 |
| 미션 이름/설명 | (추가 필요) | 시스템 콘텐츠 → 사전 번역 |
| 길드 이름/설명 | (추가 필요) | UGC → on-demand 번역 |

### 3-3. 번역 캐시 전략 (기존 3-tier 활용)

```
1. Redis (TTL 7일) → 2. DB (ContentTranslation) → 3. Google API
```

### 3-4. API 응답 구조

```json
{
  "content": "오늘 미션 완료했습니다!",
  "translated_content": "I completed today's mission!",
  "original_locale": "ko",
  "is_translated": true
}
```

클라이언트가 `Accept-Language: en` 헤더를 보내면 번역된 콘텐츠 포함.

### Phase 3 체크리스트

- [x] 피드 조회 API 번역 통합 (이미 완료되어 있었음)
- [x] 길드 게시판 조회 API 번역 통합 (GuildPostService + GuildPostController — 6개 엔드포인트)
- [x] 길드 댓글 번역 통합 (대댓글 포함)
- [x] DTO에 `TranslationInfo` 필드 추가 (`GuildPostListResponse`, `GuildPostResponse`, `GuildPostCommentResponse`)
- [x] BFF 서비스 호환성 유지
- [x] 전체 테스트 통과 확인
- [ ] Google Translation API 키 발급 및 Config Server 설정 (`google.translation.enabled: true`)
- [ ] 미션 이름/설명 번역 지원 (`ContentType` 추가 — 필요 시)
- [ ] 번역 비용 모니터링 (API 호출 수 추적)

---

## Phase 4: 금칙어 필터 다국어 확장

> **목표**: 언어별 금칙어 목록 관리 + 언어 감지 기반 필터링
>
> **영향 범위**: platform (profanity), admin-service

### 4-1. ProfanityWord 엔티티 확장

```java
@Column(name = "locale", length = 5)
@Comment("언어 코드 (ko, en, ar)")
private String locale = "ko";
```

### 4-2. 언어별 필터링

```java
// 콘텐츠 언어 감지 → 해당 언어 금칙어 목록으로 필터링
// 또는 전체 언어 금칙어 통합 검사
```

### 4-3. Admin 금칙어 관리 UI

- 언어별 탭으로 금칙어 관리
- 일괄 등록/삭제

### Phase 4 체크리스트

- [x] `ProfanityWord` 엔티티에 `locale` 필드 추가 (기본값 `"ko"`)
- [x] Unique constraint: `(word)` → `(locale, word)` 복합 유니크로 변경
- [x] `ProfanityValidationService` — `getActiveProfanityWordsByLocale(locale)` 캐시 메서드 추가
- [x] `ProfanityWordAdminService` — CRUD locale 지원 (생성/수정 시 locale 파라미터)
- [x] `ProfanityWordRequest/Response` DTO에 locale 필드 추가
- [x] `ProfanityWordRepository` — `findAllByLocaleAndIsActiveTrue()`, `existsByLocaleAndWord()`, `searchByLocaleAndKeyword()` 추가
- [x] DB 마이그레이션 SQL (`V003__add_profanity_locale.sql`)
- [x] 전체 테스트 통과 확인
- [ ] Admin UI 언어별 금칙어 관리 탭 (Admin Frontend)
- [ ] 영어/아랍어 기본 금칙어 목록 초기 데이터 등록

---

## Phase 5: 푸시 알림 다국어 발송

> **목표**: 유저 `preferredLocale`에 따른 다국어 푸시 알림
>
> **영향 범위**: notificationservice, platform (NotificationType)

### 5-1. 알림 발송 플로우 변경

```
현재: NotificationType → 한국어 템플릿 → FCM 발송
변경: NotificationType → 유저 locale 조회 → MessageSource에서 해당 언어 메시지 → FCM 발송
```

### 5-2. FCM 다국어 전략

| 전략 | 설명 | 채택 |
|------|------|------|
| 서버 사이드 번역 | 발송 시 유저 locale별 메시지 생성 | ✅ 권장 |
| FCM data message | 키만 전송, 클라이언트 번역 | 오프라인 알림 불가 |

### Phase 5 체크리스트

- [ ] `NotificationEventListener` locale-aware 발송 로직
- [ ] `notifications_ko/en/ar.properties` 45개 타입 메시지 작성
- [ ] FCM 발송 시 유저 preferredLocale 조회
- [ ] 알림 히스토리에 locale 저장

---

## Phase 6: Admin 서비스 다국어

> **목표**: Admin 백오피스 다국어 (선택적, 운영팀 필요에 따라)
>
> **영향 범위**: admin-service, admin-frontend

### Phase 6 체크리스트

- [ ] Admin API 에러 메시지 다국어
- [ ] Admin Frontend i18n (react-intl 또는 next-intl)
- [ ] 시스템 콘텐츠 다국어 입력 UI (미션, 이벤트 등)

---

## 의존 관계

```
Phase 1 (타임존)
    ↓
Phase 2 (i18n 인프라) ← 가장 핵심, Phase 3~5의 선행 조건
    ↓
  ┌─────────┬─────────┐
  ↓         ↓         ↓
Phase 3   Phase 4   Phase 5
(콘텐츠)  (금칙어)  (푸시알림)
                      ↓
                   Phase 6
                  (Admin)
```

## 프론트엔드 영향

| Phase | Product Frontend (Next.js) | React Native App | Admin Frontend |
|-------|---------------------------|-------------------|----------------|
| 1 | UTC 시간 로컬 변환 | UTC 시간 로컬 변환 | UTC 시간 로컬 변환 |
| 2 | Accept-Language 헤더, 에러 메시지 표시 | 언어 설정 UI, Accept-Language | - |
| 3 | 번역 콘텐츠 표시 UI | 번역 콘텐츠 표시 UI | - |
| 4 | - | - | 금칙어 관리 UI |
| 5 | - | 다국어 푸시 수신 | - |
| 6 | - | - | 전체 i18n |

---

## 참고: 기존 활용 가능 자산

- `SupportedLocale` enum — locale 파싱/매핑 유틸 (ko, en, ar)
- `TranslationService` — Google Translation 3-tier 캐시 구조 (비활성 상태)
- `ContentTranslation` 엔티티 — 번역 결과 DB 캐시
- `NotificationType.formatMessage()` — MessageFormat 기반 템플릿 (다국어 확장 가능)
- `LmObjectMapper` — ISO 8601 + snake_case 글로벌 표준 적용 완료
