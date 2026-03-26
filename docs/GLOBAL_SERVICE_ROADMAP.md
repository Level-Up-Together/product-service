# Global Service Roadmap

Level Up Together 서비스의 글로벌 대응을 위한 단계별 마이그레이션 계획.

## 현재 상태 요약

| 영역 | 상태 | 상세 |
|------|------|------|
| 날짜/시간 포맷 | ✅ 완료 | ISO 8601 (`2026-03-24T14:30:45`) — `@JsonFormat` 제거, JavaTimeModule 기본 출력 |
| 타임존 | ✅ 완료 (코드) | JVM/Hibernate/Jackson/JDBC 전부 UTC 설정 완료. DB 마이그레이션 SQL 작성 완료 (운영 미적용) |
| MessageSource | ✅ 완료 | product-service + admin-service 양쪽 MessageConfig Bean + errors/notifications properties |
| 에러 메시지 | ✅ 완료 | product-service 100+ / admin-service 92개 throw 사이트 메시지 키 전환 완료 |
| 푸시 알림 | ✅ 완료 | `NotificationType` → 메시지 키 전환, 유저 locale 기반 발송 |
| 유저 언어 설정 | ✅ 완료 | `Users.preferredLocale` (기본값 `"en"`) + `PUT /api/v1/mypage/preferred-locale` API |
| LocaleResolver | ✅ 완료 | product-service + admin-service 양쪽 `LocaleInterceptor` 적용 |
| 번역 서비스 | ✅ 코드 완료 | Feed + Guild 게시판 번역 통합 완료. `enabled: false` (운영 활성화 필요) |
| 금칙어 필터 | ✅ 완료 | `ProfanityWord.locale` 필드 추가, locale별 캐시/조회/CRUD 지원 |
| Locale 프레임워크 | ✅ 완료 | `SupportedLocale` enum (ko, en, ar) + `LocaleInterceptor` + `MessageSource` |
| Admin Frontend | ✅ 진행 중 | `next-intl` + `messages/en.json`, `ko.json` (500+ 키) + 40개 페이지 `useTranslations` 전환 중 |
| 기본 언어 | ✅ 영어 | `SupportedLocale.DEFAULT=ENGLISH`, DB DEFAULT `'en'` |

## 지원 언어

| 코드 | 언어 | 상태 |
|------|------|------|
| `en` | English | 기본 (Default) |
| `ko` | 한국어 | 지원 |
| `ar` | العربية | 지원 (금칙어 등록 완료, 번역 API 활성화 필요) |

---

## Phase 1: 타임존 UTC 기반 통일 ✅

> **목표**: 모든 시간 데이터를 UTC로 저장하고, 클라이언트가 로컬 타임존으로 변환하는 구조 확립
>
> **영향 범위**: platform, product-service, admin-service, DB, 프론트엔드

### Phase 1 체크리스트

- [x] JVM TimeZone UTC 설정 (`@PostConstruct` in Application classes)
- [x] Hibernate `jdbc.time_zone: UTC` 설정 (config-repository 전체 프로필)
- [x] Jackson `time-zone: UTC` 설정 (`LmObjectMapper` + `application.yml`)
- [x] JDBC URL `TimeZone=Asia/Seoul` → `TimeZone=UTC` 변경 (config-repository 전체)
- [x] DB 기존 데이터 KST → UTC 마이그레이션 SQL 작성 (`V001__migrate_kst_to_utc.sql`)
- [x] 스케줄러 cron에 `zone = "Asia/Seoul"` 명시 (5개 스케줄러)
- [x] 테스트 fixture JSON 날짜 ISO-8601 변환 (product-service 12 + admin-service 3 파일)
- [x] 전체 테스트 통과 확인
- [ ] **운영**: 프론트엔드 시간 표시 UTC 기반 변환 적용
- [ ] **운영**: DB 마이그레이션 SQL 실행 (서비스 중단 후)

---

## Phase 2: i18n 인프라 구축 ✅

> **목표**: Spring MessageSource 기반 다국어 메시지 프레임워크 구축 + 유저 언어 설정 저장
>
> **영향 범위**: platform, product-service, admin-service

### Phase 2 체크리스트

- [x] `Users` 엔티티에 `preferredLocale` 필드 추가 + DB 마이그레이션 SQL (`V002__add_preferred_locale.sql`)
- [x] 언어 설정 API 추가 (`PUT /api/v1/mypage/preferred-locale`)
- [x] `MessageSource` Bean 설정 (`MessageConfig.java`)
- [x] `LocaleInterceptor` → Accept-Language 헤더 → `LocaleContextHolder` 설정
- [x] `errors_ko/en.properties` 작성 (60+ 메시지 키)
- [x] `RestExceptionHandler` MessageSource 연동 (`resolveMessage()`)
- [x] `CustomException` 100+ throw 사이트 메시지 키 전환
- [x] `notifications_ko/en.properties` 작성 (Phase 5에서 완료)
- [ ] **운영**: DB 마이그레이션 SQL 실행 (V002)

---

## Phase 3: 콘텐츠 번역 활성화 ✅ (코드)

> **목표**: UGC 및 시스템 콘텐츠 실시간 번역
>
> **영향 범위**: product-service (feedservice, guildservice)

### Phase 3 체크리스트

- [x] 피드 조회 API 번역 통합 (기존 완료)
- [x] 길드 게시판 조회 API 번역 통합 (6개 엔드포인트)
- [x] 길드 댓글 번역 통합 (대댓글 포함)
- [x] DTO에 `TranslationInfo` 필드 추가
- [x] BFF 서비스 호환성 유지
- [ ] **운영**: Google Translation API `enabled: true` 설정
- [ ] **운영**: 번역 비용 모니터링

---

## Phase 4: 금칙어 필터 다국어 확장 ✅

> **목표**: 언어별 금칙어 목록 관리
>
> **영향 범위**: product-service (profanity), admin-service

### Phase 4 체크리스트

- [x] `ProfanityWord.locale` 필드 추가 (unique: `locale+word`)
- [x] locale별 캐시/조회/CRUD 지원
- [x] DB 마이그레이션 SQL (`V003__add_profanity_locale.sql`) + 기존 데이터 locale 분류
- [ ] **운영**: Admin UI 언어별 금칙어 관리 탭

---

## Phase 5: 푸시 알림 다국어 발송 ✅

> **목표**: 유저 `preferredLocale`에 따른 다국어 푸시 알림
>
> **영향 범위**: notificationservice, platform (NotificationType)

### Phase 5 체크리스트

- [x] `NotificationType` enum 메시지 키 전환
- [x] `notifications_ko/en.properties` 작성 (25개 키)
- [x] `NotificationService` → 유저 locale 조회 → MessageSource로 locale별 메시지 생성
- [x] 신고 알림 locale-aware 전환

---

## Phase 6: Admin 서비스 다국어 ✅

> **목표**: Admin 백오피스 백엔드 + 프론트엔드 다국어
>
> **영향 범위**: admin-service, admin-frontend

### Phase 6 체크리스트

- [x] Admin API: MessageConfig + LocaleInterceptor + errors_ko/en.properties + 92개 메시지 키 전환
- [x] 기본 언어 영어 전환 (`SupportedLocale.DEFAULT=ENGLISH`, DB DEFAULT `'en'`)
- [x] Admin Frontend: `next-intl` 설치 + `messages/en.json`, `ko.json` (500+ 키)
- [x] Admin Frontend: RootLayout `NextIntlClientProvider` 설정
- [x] Admin Frontend: 로그인/레이아웃(네비게이션) `useTranslations` 전환
- [x] Admin Frontend: 전체 40개 페이지 `useTranslations` 전환 (진행 중)
- [ ] 시스템 콘텐츠 다국어 입력 UI (미션, 이벤트 ko/en/ar 입력 폼)

---

## 전체 변경 통계

| 프로젝트 | 변경 내역 |
|---------|----------|
| **level-up-together-platform** | `LmObjectMapper` UTC, `RestExceptionHandler` MessageSource, `NotificationType` 메시지 키, `UserQueryFacade`, base entity `@JsonFormat` 제거 |
| **product-service** | Phase 1~5 전체: UTC 설정, MessageConfig, LocaleInterceptor, i18n properties (errors/notifications ko/en), CustomException 100+ 메시지 키 전환, Feed/Guild 번역 통합, ProfanityWord.locale, NotificationService locale-aware |
| **admin-service** | UTC 설정, MessageConfig, LocaleInterceptor, i18n properties, CustomException 92개 메시지 키 전환, @JsonFormat 제거 |
| **admin-frontend** | `next-intl`, messages/en.json + ko.json (500+ 키), 40개 페이지 useTranslations 전환 |
| **config-repository** | JDBC URL TimeZone=UTC, hibernate.jdbc.time_zone: UTC (전체 프로필) |
| **level-up-together-sql** | V001 (KST→UTC 162 컬럼), V002 (preferred_locale), V003 (profanity locale) |

## 운영 배포 체크리스트

| 순서 | 작업 | 비고 |
|------|------|------|
| 1 | V001 DB 마이그레이션 실행 | **서비스 중단 필요** (KST→UTC) |
| 2 | V002 DB 마이그레이션 실행 | users.preferred_locale 컬럼 추가 |
| 3 | V003 DB 마이그레이션 실행 | profanity_word.locale 컬럼 추가 (실행 완료) |
| 4 | platform publish | GitHub Packages 배포 |
| 5 | config-repository 배포 | UTC 설정 반영 |
| 6 | product-service 배포 | 코드 변경 반영 |
| 7 | admin-service 배포 | 코드 변경 반영 |
| 8 | admin-frontend 배포 | i18n 반영 |
| 9 | Google Translation `enabled: true` | Config Server 설정 |
| 10 | 프론트엔드 UTC 시간 표시 적용 | Product Frontend + React Native |
