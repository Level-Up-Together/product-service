# Plan: 일본어(ja) 다국어 지원 추가

## 현재 상태

| 항목 | 현재 지원 |
|------|----------|
| SupportedLocale | ko, en, ar |
| Backend i18n properties | errors/notifications/messages × ko, en (ar 없음) |
| Frontend messages | ko.json, en.json, ar.json |
| Admin Frontend messages | ko.json, en.json (ar 없음) |
| DB 다국어 컬럼 엔티티 | 7개 (기본 + _en + _ar) |
| 온디맨드 번역 (Google API) | Feed, Comment, Guild Post/Comment |
| 금칙어 (ProfanityWord) | ko, en, ar |
| React Native | i18n 미구현 |

## 영향받는 DB 테이블 (7개 엔티티, 4개 DB)

| DB | 테이블 | 추가할 컬럼 |
|----|--------|-----------|
| gamification_db | title | name_ja, description_ja |
| gamification_db | achievement | name_ja, description_ja |
| gamification_db | event | name_ja, description_ja |
| mission_db | mission | title_ja, description_ja |
| mission_db | mission_template | title_ja, description_ja |
| meta_db | mission_category | name_ja, description_ja |
| meta_db | common_code | code_title_ja |

**총 추가 컬럼: 13개**

---

## Phase 1: Backend Core (product-service)

SupportedLocale, LocaleUtils, i18n properties 수정. DB 변경 없이 백엔드 기반을 먼저 구축.

### 1-1. SupportedLocale enum에 JAPANESE 추가
- **파일**: `service/src/main/java/io/pinkspider/global/translation/enums/SupportedLocale.java`
- **변경**: `JAPANESE("ja", "日本語")` 추가

### 1-2. LocaleUtils에 ja 분기 추가
- **파일**: `service/src/main/java/io/pinkspider/global/translation/LocaleUtils.java`
- **변경**: `getLocalizedText()` 메서드에 ja 파라미터 추가 (4개 언어 버전 오버로드)

### 1-3. i18n properties 파일 생성
- **경로**: `app/src/main/resources/i18n/`
- **생성 파일**:
  - `errors_ja.properties` (errors_en.properties 기반 번역)
  - `notifications_ja.properties` (notifications_en.properties 기반 번역)
  - `messages_ja.properties` (messages_en.properties 기반 번역)

### 1-4. 테스트
- SupportedLocale 관련 테스트 업데이트
- LocaleUtils 테스트에 ja 케이스 추가

---

## Phase 2: DB 스키마 마이그레이션

### 2-1. DDL 마이그레이션 스크립트 작성
- **경로**: `level-up-together-sql/queries/migration/`
- **파일**: `YYYYMMDD_add_japanese_columns.sql`

```sql
-- gamification_db
ALTER TABLE title ADD COLUMN name_ja VARCHAR(255);
ALTER TABLE title ADD COLUMN description_ja VARCHAR(500);
ALTER TABLE achievement ADD COLUMN name_ja VARCHAR(255);
ALTER TABLE achievement ADD COLUMN description_ja VARCHAR(500);
ALTER TABLE event ADD COLUMN name_ja VARCHAR(255);
ALTER TABLE event ADD COLUMN description_ja TEXT;

-- mission_db
ALTER TABLE mission ADD COLUMN title_ja VARCHAR(255);
ALTER TABLE mission ADD COLUMN description_ja TEXT;
ALTER TABLE mission_template ADD COLUMN title_ja VARCHAR(255);
ALTER TABLE mission_template ADD COLUMN description_ja TEXT;

-- meta_db
ALTER TABLE mission_category ADD COLUMN name_ja VARCHAR(255);
ALTER TABLE mission_category ADD COLUMN description_ja VARCHAR(500);
ALTER TABLE common_code ADD COLUMN code_title_ja VARCHAR(255);
```

### 2-2. Entity 클래스에 _ja 필드 추가 (7개)
각 엔티티에 `_ja` 컬럼 필드 + `getLocalized*()` 메서드 업데이트:

| 엔티티 | 파일 경로 (service/ 하위) |
|--------|------------------------|
| Title | `gamification-service/.../domain/entity/Title.java` |
| Achievement | `gamification-service/.../domain/entity/Achievement.java` |
| Event | `gamification-service/.../event/domain/entity/Event.java` |
| Mission | `mission-service/.../domain/entity/Mission.java` |
| MissionTemplate | `mission-service/.../domain/entity/MissionTemplate.java` |
| MissionCategory | `meta-service/.../domain/entity/MissionCategory.java` |
| CommonCode | `meta-service/.../domain/entity/CommonCode.java` |

### 2-3. 테스트 업데이트
- 각 엔티티의 getLocalized* 테스트에 ja 케이스 추가

---

## Phase 3: API/DTO 계층

### 3-1. Response DTO에 _ja 필드 추가
_en, _ar 를 노출하는 모든 Response DTO에 _ja 추가:

- `TitleResponse`, `AchievementResponse`, `EventResponse`
- `MissionResponse`, `MissionTemplateResponse`
- `MissionCategoryResponse`, `CommonCodeResponse`
- 기타 관련 DTO (탐색 필요)

### 3-2. Admin Request DTO에 _ja 필드 추가
어드민에서 다국어 콘텐츠를 입력하는 Request DTO:

- `MissionAdminRequest` (title_ja, description_ja)
- `MissionTemplateAdminRequest` 등

### 3-3. Admin Internal Controller/Service 업데이트
Admin에서 _ja 필드를 받아 저장하는 로직 추가

### 3-4. BFF 응답에 _ja 포함 확인
BffHomeService 등에서 _ja 필드가 자연스럽게 전달되는지 확인

---

## Phase 4: Frontend (level-up-together-frontend)

### 4-1. ja.json 메시지 파일 생성
- **경로**: `src/messages/ja.json`
- en.json 기반으로 일본어 번역 (약 489개 키)

### 4-2. i18n config에 ja 추가
- **파일**: `src/i18n/request.ts`
- `supportedLocales`에 `'ja'` 추가

### 4-3. getLocalizedField() 유틸 업데이트
- **파일**: `src/lib/utils/locale-utils.ts`
- 함수 시그니처에 `jaText` 파라미터 추가
- ja locale 분기 추가

### 4-4. getLocalizedField 호출부 전수 업데이트
- 모든 컴포넌트에서 `getLocalizedField(title, title_en, title_ar)` →
  `getLocalizedField(title, title_en, title_ar, title_ja)` 로 변경
- **영향 범위 큼** — 모든 다국어 필드 표시 컴포넌트

---

## Phase 5: Admin Frontend (level-up-together-admin-frontend)

### 5-1. 다국어 입력 폼에 일본어 필드 추가
미션 템플릿 생성/수정 폼에 title_ja, description_ja 입력란 추가

### 5-2. API 타입 정의 업데이트
- `api.ts`의 Mission, MissionTemplate 등 인터페이스에 `_ja` 필드 추가

### 5-3. (선택) Admin UI 일본어 메시지
- Admin은 현재 ko/en만 지원, 내부 도구이므로 ja 추가는 후순위

---

## Phase 6: 부가 시스템

### 6-1. 금칙어 (ProfanityWord)
- 일본어 금칙어 데이터 준비 및 INSERT
- locale = 'ja' 로 등록

### 6-2. Translation Service
- `SupportedLocale`에 ja가 추가되면 Google Translation API가 자동으로 ja 번역 지원
- 별도 코드 변경 불필요

### 6-3. ActivityFeed 비정규화 필드
- 피드에 저장되는 스냅샷 필드 (mission_title 등)가 다국어를 지원하는지 확인
- 필요시 스냅샷 로직 업데이트

### 6-4. DailyMissionInstance 스냅샷
- `missionTitle`, `missionDescription` 스냅샷이 있다면 ja 버전 추가 여부 검토

---

## 실행 순서 및 의존성

```
Phase 1 (Backend Core)
  ↓
Phase 2 (DB Schema + Entity)
  ↓
Phase 3 (API/DTO)
  ↓  ↘
Phase 4 (Frontend)  Phase 5 (Admin Frontend)
  ↓
Phase 6 (부가 시스템)
```

## 리스크

| 리스크 | 영향 | 대응 |
|--------|------|------|
| getLocalizedField() 호출부 누락 | ja 선택 시 fallback(ko) 표시 | 전수 검색 + 테스트 |
| DB 마이그레이션 시 테이블 잠금 | 프로덕션 다운타임 | ALTER TABLE은 NULL 허용이므로 빠름 |
| 일본어 번역 품질 | UX 저하 | 네이티브 검수 필요 |
| 금칙어 커버리지 | 부적절 콘텐츠 미탐지 | 일본어 금칙어 DB 별도 구축 |

## 예상 작업량

| Phase | 파일 수 (약) | 난이도 |
|-------|:-----------:|:------:|
| Phase 1 | 5 | LOW |
| Phase 2 | 8 | LOW |
| Phase 3 | 10~15 | MED |
| Phase 4 | 20~30 | MED |
| Phase 5 | 5~8 | LOW |
| Phase 6 | 3~5 | LOW |
