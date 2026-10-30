# Internal API (Admin Backend ↔ MVP 연동)

`/api/internal/**` 경로는 `SecurityConfig`에서 `permitAll`이지만 **공유 시크릿 헤더 인증 + VPC 내부 접근**으로 보호. Admin Backend가 MVP의 도메인 데이터를 읽거나 액션을 실행할 때 사용.

> 2026-10-29 코드 기준 현행화 — `@RequestMapping("/api/internal…")` 컨트롤러 **40개 = 베이스 경로 40개**(중첩 경로 포함). chat·bff·notice·support 서비스에는 내부 API 컨트롤러가 없다.

## 인증 (LUT-244)

네트워크 격리(VPC)만으로는 dev(공인망 홈서버)가 무인증 노출되므로, 애플리케이션 계층에서 공유 시크릿 헤더를 검증한다.

- **product-service**: `InternalApiKeyFilter`가 `/api/internal/**` 요청의 `X-Internal-Api-Key` 헤더를 `app.security.internal-api.key`와 상수시간 비교(`MessageDigest.isEqual`). 불일치/누락 시 401 + `{"code":"000401","message":"Unauthorized internal API access","value":<요청 URI>}`.
  - `SecurityConfig`에서 `JwtAuthenticationFilter` **앞**에 등록 — JWT 해석 전에 선차단. 필터는 `@Profile("!test")`라 `test` 프로필(컨트롤러 슬라이스 테스트)에는 없다.
  - 경로 판정은 디스패처와 같은 정규화 경로(`UrlPathHelper` — 퍼센트 디코딩·`;param` 제거·중복 슬래시 축약)로 한다. 원시 URI 에 `%`·`;`·`..`·`\` 가 섞인 보호 접두사 요청은 해석과 무관하게 키를 요구한다(fail-closed, LUT-605).
- **admin-service**: `MvpFeignConfig.mvpInternalApiKeyInterceptor`가 모든 MVP 내부 Feign 호출에 `app.mvp.internal-api-key`를 헤더로 주입.
- **product-service → admin-service** (LUT-686, 2026-10-30): 반대 방향도 같은 키를 쓴다. admin 의 product 전용 경로(`/api/v1/inquiries/**`·`/api/internal/**`·신고 등록/`check`/`check-batch`)는 admin `ProductCallKeyFilter` 가 `X-Internal-Api-Key` 를 요구하고, product 는 `AdminInternalApiKeyInterceptor`(`AdminFeignConfig`·`ReportFeignConfig`·`AdminInternalFeignConfig` 가 등록)로 `app.security.internal-api.key` 를 보낸다. 키가 비면 생략(fail-open). 예전엔 admin 의 IP 목록(비면 전체 허용)에만 의존해 누구나 `X-User-Id` 만 바꿔 남의 문의를 읽고 쓸 수 있었다. 배포 순서 product → admin.
- **시크릿**: config-repository에 환경별 `{cipher}` 암호화 저장(dev/prod 각각 다른 값, product ↔ admin 동일 값이어야 매칭).
- **fail-open**: 키 미설정(공백)이면 검증/주입을 건너뛴다 → config 선배포 없이 무중단 롤아웃 가능. 배포 후 config에 키가 있으면 자동 활성화.
- **액추에이터 보호 (LUT-605)**: 같은 필터가 `management.endpoints.web.base-path`(`/showmethemoney`, config-repository `actuator/actuator-{env}.yml`) 하위도 지킨다. `/showmethemoney/health`(및 `/health/**`)만 공개, 나머지 엔드포인트와 base-path 자체는 키 필수. 키는 `X-Internal-Api-Key` 헤더 또는 **HTTP Basic 비밀번호**(사용자명 무관, Prometheus/Alloy 스크레이프 호환) 로 전달 — 플랫폼 `JwtAuthenticationFilter`는 `Bearer`만 해석해 충돌 없음. exposure 는 prod 가 아직 `*` 라 보호는 이 필터가 전담한다 — dev 는 2026-10-30 `health,info,prometheus` 로 축소(LUT-675, config `product-service-dev.yml`), prod 는 v1.1.4 운영 배포 때 같은 값으로 줄인다. 예전엔 `permitAll` + exposure `*` 로 prod `/env`·`/beans`·`/prometheus` 가 공개돼 있었다(2026-10-24 확인).
- **`X-Admin-Id` 헤더**: 쓰기 이력에 어드민 ID 를 남기는 API 는 필수 헤더로 받는다 — `push-campaigns` `POST`, `title-grants` `POST`/`DELETE`, `item-grants` `POST`/`DELETE`, `shop-items/{itemId}/push-setting` `PUT`, `shop-items/{itemId}/push-messages` `POST`.
- **DAU 집계 제외 (LUT-598)**: `WebMvcConfig`가 `/api/internal/**`를 `UserActivityInterceptor` 대상에서 뺀다 — 어드민 호출은 유저 활동이 아니다.

## 도메인별 베이스 경로

표기: 하위 경로는 베이스 경로 기준, `GET /` = 베이스 경로 자체. 목록 API 는 대부분 `page`(0)/`size`(20) 페이징. "Feign 전환(2026-02)" 은 admin-service 의 MVP 조회를 Feign 내부 API 로 옮기며 서비스별로 일괄 추가한 초기 커밋(P1~P7, 티켓 없음).

### user-service (user_db)

| 베이스 경로 | 용도 · 주요 엔드포인트 | 도입·변경 |
|---|---|---|
| `/api/internal/users` | 유저 검색(`keyword`·`provider`·정렬)·조회·통계·상세 탭, 블랙리스트, 신고 처리, 즉시 탈퇴. `GET /` · `GET /{userId}` · `GET /email/{email}` · `GET /statistics` · `GET /{userId}/{detail,titles,achievements,guild}` · `DELETE /{userId}/profile-image?reason=` · `POST /{userId}/withdraw` · `POST`/`DELETE /{userId}/blacklist` · `GET /{userId}/blacklist/history` · `GET /blacklist`(유형·활성·기간 필터) · `POST /{userId}/suspend-from-report` · `POST /{userId}/warn-from-report` · `POST /batch`(유저 ID 목록 → 요약 맵) | Feign 전환(2026-02) · 신고 처리(2026-04) · LUT-584 · LUT-603 |
| `/api/internal/terms` | 약관·버전·동의 관리. 약관 `GET /`·`/search`·`/{id}`·`/code/{code}`·`/required`·`/types`·`/type/{type}` + `POST`/`PUT`/`DELETE` · 버전 `GET`/`POST /{termsId}/versions`, `GET /{termsId}/versions/latest`, `GET`/`PUT`/`DELETE /versions/{versionId}`, **`POST /versions/{versionId}/publish`** · 동의 `GET /agreements/user/{userId}[/terms/{termsId}]`, `/agreements/version/{termVersionId}/count`, `/agreements/terms/{termsId}/count`, `/agreements/search`, `/agreements/summaries` | Feign 전환(2026-02) · LUT-364(DRAFT→PUBLISHED, DRAFT만 수정·삭제) |
| `/api/internal/daily-mvp-exclusions` | 일간 MVP 제외 명단. `GET /{date}` · `POST` · `DELETE /{date}/{userId}` | Feign 전환(2026-02) |

- **`GET /users/statistics` (LUT-603)**: `total_users`·`users_by_provider` 는 탈퇴(WITHDRAWN) 제외 — 예전엔 `count()` 라 익명화된 탈퇴 행까지 셌다. 탈퇴 수는 `withdrawn_users`. `new_users_{today,this_week,this_month}`·`daily_new_users`(최근 30일) 는 가입 시점 집계라 이후 탈퇴해도 포함된다.
- **`POST /users/{userId}/withdraw` (LUT-584)**: 본문 `{admin_id, reason}`(admin_id 는 로그용, reason 선택). `MyPageService.withdrawUserByAdmin` 으로 일반 탈퇴와 같은 정리(즉시 `WITHDRAWN` + 익명화 등)를 하고 `withdrawal_cooldown_exempt=true` 로 재가입 cool-down 을 면제한다. 이미 탈퇴면 `USER_002`.

### guild-service (guild_db)

| 베이스 경로 | 용도 · 주요 엔드포인트 | 도입·변경 |
|---|---|---|
| `/api/internal/guilds` | 길드 검색(`keyword`·`category_id`/`category_ids` 멀티·`is_active`·`visibility`)·상세·멤버·통계·활성 토글, **신고 처리 차단**. `GET /` · `GET /{id}` · `GET /{id}/members` · `GET /statistics` · `PATCH /{id}/toggle-active` · `POST /batch-names`(ID 목록 → 이름 맵) · `POST /{guildId}/ban-from-report?reason=` | Feign 전환(2026-02) · 신고 처리(2026-04) · 멀티 카테고리(2026-07) |
| `/api/internal/guilds/{guildId}` | 길드 게시글·댓글 어드민. 게시글 `GET /posts`(`include_deleted`)·`/posts/all`·`/posts/{postId}`·`/posts/type/{postType}`·`/posts/deleted`·`/posts/search?keyword=`·`/posts/count` · `DELETE /posts/{postId}`(soft) · `DELETE /posts/{postId}/hard` · `PATCH /posts/{postId}/restore` · 댓글은 `/posts/{postId}/comments/...` 아래 같은 패턴 · 길드 전체 댓글 `GET /comments`·`/comments/count` | Feign 전환(2026-02) |

`/api/internal/guilds/exp` 는 경로는 길드 하위지만 mission-service 소유다(아래).

### mission-service (mission_db)

| 베이스 경로 | 용도 · 주요 엔드포인트 | 도입·변경 |
|---|---|---|
| `/api/internal/missions` | 미션 검색(`keyword`·`source`·`status`·`type`·`participation_type`·`creator_id`·`category_id`)·CRUD·상태 변경. `GET /`·`/all`·`/{id}`·`/count` · `POST` · `PUT /{id}` · `PATCH /{id}/status?status=` · `DELETE /{id}` | Feign 전환(2026-02) |
| `/api/internal/mission-templates` | 미션 템플릿 CRUD. `GET /`·`/all`·`/{id}`·`/count` · `POST` · `PUT`/`DELETE /{id}` | Feign 전환(2026-02) |
| `/api/internal/mission-participants` | 수행 기록·건수(유저 상세용). `GET /user/{userId}/history`(`type`·`start_date`·`end_date`) · `GET /guild/{guildId}/history` · `GET /user/{userId}/count` · `GET /user/{userId}/executions/count` · `GET /user/{userId}/executions/completed/count` | Feign 전환(2026-02) · QA-165 · QA-205(수행 건 기반) · LUT-239(길드 기록) |
| `/api/internal/mission-comments` | 미션 댓글 어드민 삭제(신고 `CONTENT_DELETED`). `DELETE /{commentId}?reason=` | 신고 처리(2026-04) |
| `/api/internal/mission-images` | 과거 업로드 이미지 thumb/medium 변형 백필. `POST /backfill-variants?limit=` | LUT-409 |
| `/api/internal/guilds/exp` | 자동종료로 누락된 길드 EXP 소급(일회성 수동, 멱등). `POST /backfill-auto-complete` | LUT-236 |

### feed-service (feed_db)

| 베이스 경로 | 용도 · 주요 엔드포인트 | 도입·변경 |
|---|---|---|
| `/api/internal/feeds` | 피드 검색(`activityType`·`visibility`·`userId`·`categoryId`·`keyword`)·상세·삭제·통계. `GET /`·`/{id}`·`/stats` · `DELETE /{id}`(본문 `{reason(필수), admin_info}`) | Feign 전환(2026-02) |
| `/api/internal/feed-comments` | 피드 댓글 어드민 삭제(신고 `CONTENT_DELETED`). `DELETE /{commentId}?reason=` | 신고 처리(2026-04) |

### meta-service (meta_db)

| 베이스 경로 | 용도 · 주요 엔드포인트 | 도입·변경 |
|---|---|---|
| `/api/internal/user-level-configs` | 유저 레벨 설정. `GET /`·`/all`·`/{id}`·`/level/{level}` · `POST` · `PUT`/`DELETE /{id}` | Feign 전환(2026-02) · QA-99 |
| `/api/internal/guild-level-configs` | 길드 레벨 설정(같은 엔드포인트 구성). 레벨 기준은 `cumulative_point` | Feign 전환(2026-02) · QA-99 · LUT-483 |
| `/api/internal/attendance-reward-configs` | 출석 보상 설정. `GET /`·`/all`·`/active`·`/consecutive`·`/{id}` · `POST` · `PUT /{id}` · `PATCH /{id}/toggle-active` · `DELETE /{id}` | Feign 전환(2026-02) · QA-131 |
| `/api/internal/mission-categories` | 미션 카테고리. `GET /`·`/all`·`/active`·`/{id}`·`/by-name/{name}`·`/by-ids` · `POST` · `PUT`/`DELETE /{id}` · `POST /{id}/toggle-active`(PATCH 아님) | Feign 전환(2026-02) |
| `/api/internal/profanity-words` | 금칙어. `GET /`·`/all`·`/active`·`/{id}` · `POST` · `PUT`/`DELETE /{id}` · `PATCH /{id}/toggle` | Feign 전환(2026-02) |

### gamification-service (gamification_db)

| 베이스 경로 | 용도 · 주요 엔드포인트 | 도입·변경 |
|---|---|---|
| `/api/internal/achievements` | 업적 CRUD(`category_id`/`category_ids` 멀티 검색). `GET /`·`/all`·`/active`·`/visible`·`/{id}`·`/category/{categoryCode}` · `POST` · `PUT /{id}` · `PATCH /{id}/toggle-active` · `DELETE /{id}` | Feign 전환(2026-02) |
| `/api/internal/achievement-categories` | 업적 카테고리. `GET /`·`/active`·`/{id}`·`/code/{code}` · `POST` · `PUT /{id}` · `PATCH /{id}/toggle-active` · `DELETE /{id}` | Feign 전환(2026-02) |
| `/api/internal/titles` | 칭호 CRUD. `GET /`(`position_type`)·`/all`·`/active`·`/statistics`·`/position/{positionType}`·`/left`·`/right`·`/rarity/{rarity}`·`/{id}` · `POST` · `PUT /{id}` · `PATCH /{id}/toggle-active` · `DELETE /{id}` | Feign 전환(2026-02) |
| `/api/internal/title-grants` | 칭호 수동 부여/회수/이력. `POST`(이미 보유 시 거부) · `DELETE /{userTitleId}` · `GET /?keyword=` | 2026-04 |
| `/api/internal/check-logic-types` | 업적 판정 로직 타입. `GET /`·`/all`·`/active`·`/by-data-source`·`/{id}`·`/code/{code}`·`/data-sources`·`/comparison-operators` · `POST` · `PUT /{id}` · `PATCH /{id}/toggle-active` · `DELETE /{id}` | Feign 전환(2026-02) |
| `/api/internal/events` | 이벤트 CRUD. `GET /`·`/active`·`/current`·`/{id}` · `POST` · `PUT`/`DELETE /{id}` | Feign 전환(2026-02) |
| `/api/internal/seasons` | 시즌 CRUD. `GET /`·`/all`·`/current`·`/upcoming`·`/{id}` · `POST` · `PUT`/`DELETE /{id}` · `PATCH /{id}/toggle` | Feign 전환(2026-02) |
| `/api/internal/seasons/{seasonId}/rank-rewards` | 시즌 순위 보상. `GET /` · `POST` · `POST /bulk` · `PUT`/`DELETE /{rewardId}` · `GET /history`·`/stats` · `POST /process`(종료 직후 즉시 부여) · `POST /retry`(실패분 재처리) | Feign 전환(2026-02) · QA-6 · QA-103(`process`/`retry`) |
| `/api/internal/experience-history` | 기간별 EXP 상위 유저·카테고리별 미션 통계. `GET /top-gainers` · `GET /top-gainers-excluding`(`excludedUserIds`) · `GET /category-mission-stats` (기간 `startDate`/`endDate` 필수) | Feign 전환(2026-02) |
| `/api/internal/mvp-history` | MVP 이력. `GET /date?date=` · `GET /period` · `GET /user/{userId}` · `GET /stats` · `GET /user/{userId}/category-activity` | Feign 전환(2026-02) |
| `/api/internal/shop-items` | 상점 아이템 CRUD(`item_type`·`rarity`·`is_active` 필터) + 이미지 업로드. `GET /`·`/{id}` · `POST` · `PUT /{id}` · `PATCH /{id}/toggle-active` · `DELETE /{id}` · `POST /images`(multipart `file`) | QA-225 · LUT-225/271/294/341(필드·검증 변경) |
| `/api/internal/shop-items/{itemId}/push-setting` | 장착 아이템 푸시 슬롯 시각. `GET` · `PUT`(`{before_activity_time 08:00~11:59, after_complete_time 12:00~20:59}` 둘 다 선택, `@Pattern` 검증) · `DELETE` | LUT-528 · LUT-573(아침/오후 2필드) |
| `/api/internal/shop-items/{itemId}/push-messages` | 장착 아이템 푸시 대사(`trigger_type`: `ANY`/`BEFORE_ACTIVITY`/`AFTER_COMPLETE`/`INACTIVE`). `GET` · `POST` · `PUT /{messageId}` · `PATCH /{messageId}/toggle` · `DELETE /{messageId}` | LUT-516 · LUT-528(`trigger_type`) |
| `/api/internal/shop-purchases` | 아이템 구매이력. `GET /?keyword=` | LUT-328 |
| `/api/internal/item-grants` | 아이템 수동 지급/회수/이력. `POST`(보유 중이면 멱등 no-op, 이력 미기록·`already_owned` 응답, 지급 시 `ITEM_GRANTED` 알림) · `DELETE /{itemGrantId}`(인벤토리 삭제 + 이력 revoked 마킹, 재회수는 `120905`) · `GET /?keyword=` | LUT-472 |
| `/api/internal/diamonds` | 다이아 원장 조회·소급 지급. `GET /user/{userId}/history` · `POST /migrate`(일회성 수동, 멱등) | QA-220 |
| `/api/internal/diamond-bundles` | 핑크다이아 묶음상품 CRUD + 이미지. `GET /`·`/{id}` · `POST` · `PUT /{id}` · `PATCH /{id}/toggle-active` · `DELETE /{id}` · `POST /images` | LUT-356 |
| `/api/internal/diamond-payments` | 다이아 결제이력(`start_at`·`end_at`·`nickname`·`user_id`·`platform`·`bundle_id`·`status`). `GET /` (어드민 화면 권한 `DIAMOND_PAYMENT_READ`) | LUT-401 · LUT-486(`user_id`) |
| `/api/internal/subscription-payments` | 구독 결제 원장 조회(`start_at`·`end_at`·`nickname`·`user_id`·`platform`·`plan`·`event_type`, 응답에 `price_source` LUT-600, 어드민 권한은 다이아 결제이력과 같은 `DIAMOND_PAYMENT_READ`) · **`POST /reconcile?user_id=`**: Android 구독 원장을 Play 주문 연번과 전수 대조해 누락 갱신 백필·어긋난 행 정정(구독 권한은 불변). 구독 행 없음 `120803`, Android 아님 `120804` | LUT-486 · LUT-488 · LUT-600 · LUT-617 |

### notification-service (notification_db)

| 베이스 경로 | 용도 · 주요 엔드포인트 | 도입·변경 |
|---|---|---|
| `/api/internal/push-campaigns` | 어드민 푸시 발송(전체/일부 유저) + 발송 이력. `POST`(`{title ≤100, body ≤500, action_url, target_type ALL\|USERS, user_ids}` — 이력 행 PENDING 커밋 후 비동기 발송, 표준 알림 파이프라인 `ADMIN_PUSH`) · `GET /` · `GET /{campaignId}`. 대상 없음 `140101` / 활성 유저 없음 `140102` / 캠페인 없음 `140103` | LUT-508 |

### stats-service (stats_db)

| 베이스 경로 | 용도 · 주요 엔드포인트 | 도입·변경 |
|---|---|---|
| `/api/internal/stats/activity` | Redis `active:{KST날짜}` 활동 집합 수동 적재(스케줄러 00:20 대기 없이 배포 검증·누락 보정, 멱등). `POST /flush?date=YYYY-MM-DD` | LUT-598 |
| `/api/internal/kpi` | KPI 스냅샷·코호트 조회 + 소급 재계산. `GET /daily?from&to` · `GET /retention?cohort_from&cohort_to[&source=REQUEST\|ATTENDANCE]` · `GET /revenue/cohorts?from_month&to_month` · `GET /signups/by-channel?from_month&to_month`(KST 월 × `signup_channel`, null→`unknown`, user_db 직접 집계 — 어드민 CAC 분모) · `POST /rebuild?from&to`(어제까지로 자르고 최대 400일, 넘으면 최근 400일만) | LUT-601 · LUT-602 · LUT-616 |

- 날짜는 ISO(`yyyy-MM-dd`), 월은 `yyyy-MM`. `from > to` 는 서로 바꿔 처리한다.
- **파라미터 오류는 400 (LUT-616)**: `StatsRequestExceptionHandler`(statsservice 패키지 한정 `HIGHEST_PRECEDENCE` 어드바이스) 가 형식 오류·enum 허용값 밖 → `100101`, 필수 파라미터 누락 → `100102` 로 응답한다. 이게 없으면 플랫폼 `RestExceptionHandler` 의 `Exception.class` 핸들러가 500(`000500`) 으로 바꾼다(`@WebMvcTest` 슬라이스엔 플랫폼 핸들러가 없어 테스트로는 안 보임).

### 역방향 — MVP → Admin (`AdminInternalFeignClient`)

MVP 도 admin-service(`app.admin.api-url`) 의 내부 API 를 읽는다: `GET /api/internal/featured-content/{players,guilds,feeds}?category_id=`(추천 유저·길드·피드 ID) · `GET /api/internal/banners` · `GET /api/internal/banners/type/{bannerType}`. 호출처는 `HomeService`·`FeedQueryService`·`GuildQueryService`. 이 Feign 설정(`AdminInternalFeignConfig`)은 `X-Internal-Api-Key` 를 붙이지 않는다.

### 참고 — `/api/internal` 밖의 "내부용" 경로

예전의 `DELETE /api/v1/bff/season/cache`(시즌 캐시 삭제)는 메서드 제한 없는 `permitAll` 이라 비로그인 누구나 호출할 수 있었고 호출처도 없어 **LUT-649(2026-10-30)에 엔드포인트와 permitAll 을 지웠다**. 운영에서 시즌 캐시를 비울 일이 생기면 `/api/internal/**` 아래(내부 키 보호)로 새로 만든다. 시즌 캐시는 어드민 시즌 변경 시 이미 evict 된다.

## 신고 처리 워크플로우 (Admin → MVP)

유저 신고는 admin-service 가 저장·심사하고, 조치(`ReportActionType`) 의 부수 효과만 MVP 내부 API 로 적용한다(`ContentReportAdminService.takeAction` — 외부 호출 실패 시 신고 처리 트랜잭션 전체 롤백).

| 단계 | 엔드포인트 | 처리 |
|---|---|---|
| 신고 접수 (User → MVP) | `POST /api/v1/reports` | `ReportService.createReport()` → admin-service `POST /api/admin/reports`(`AdminReportFeignClient`), `reportUnderReview` 캐시 evict |
| 신고 상태 확인 | `GET /api/v1/reports/check?targetType=&targetId=` | admin `GET /api/admin/reports/check` 결과를 `reportUnderReview` 캐시. 서킷브레이커 폴백은 `false` |
| **WARNING** (PR3) | `POST /api/internal/users/{userId}/warn-from-report` | `warning_count` +1. `suspension_threshold` 이상이면 카운트 리셋 후 `suspendFromReport` 로 자동 정지 전환 |
| **USER_SUSPENDED** (PR2) | `POST /api/internal/users/{userId}/suspend-from-report` | `suspension_count` +1. `permanent_ban_threshold` 이상이면 `PERMANENT_BAN`(`PERMANENTLY_BANNED`), 아니면 `duration_days` 일 `SUSPENSION`. 기존 활성 블랙리스트는 비활성화 |
| **CONTENT_DELETED** (PR1) | 대상 유형별 | `FEED` → `DELETE /api/internal/feeds/{id}` · `MISSION` → `DELETE /api/internal/missions/{id}` · `FEED_COMMENT` → `DELETE /api/internal/feed-comments/{commentId}` · `MISSION_COMMENT` → `DELETE /api/internal/mission-comments/{commentId}` · `GUILD` → `POST /api/internal/guilds/{guildId}/ban-from-report`(`is_banned`·`banned_at`·`banned_reason` 기록, 멤버·콘텐츠 후속 처리는 운영자 수동) · `GUILD_NOTICE` → `DELETE /api/internal/guilds/{guildId}/posts/{postId}`(soft, `target_guild_id` 필요) |

- 임계값·기간은 **요청 값**이다 — MVP 는 받은 값대로 판정하고, 현재 admin-service 상수는 경고 3회 → 30일 정지, 정지 누적 3회 → 영구강퇴(`UserWarningService`·`UserSuspensionService`).
- 길드 차단은 별도 조치 유형(`GUILD_BANNED`)이 아니라 `CONTENT_DELETED` + 대상 `GUILD` 다. `PROFILE_RESET` 조치는 신고 처리에서 MVP 를 호출하지 않으며, 프로필 이미지 초기화는 어드민 유저 관리의 `DELETE /api/internal/users/{userId}/profile-image` 로 따로 한다.

신고 처리 결과 알림은 MVP 내부에서 `NotificationService`로 발송 (`REPORT_WARNING_RECEIVED` / `REPORT_SUSPENDED` / `REPORT_PERMANENTLY_BANNED` — in-app 저장 + 실시간 + 푸시). 길드 차단·콘텐츠 삭제에는 신고 결과 알림이 없다.

1:1 문의 답변(`INQUIRY_REPLIED`)은 admin-service가 Redis Stream(`stream:app-push`)에 직접 발행 → MVP `AppPushMessageConsumer`가 소비 시점에 in-app 저장 + 유저 locale 재현지화 (QA-94).
