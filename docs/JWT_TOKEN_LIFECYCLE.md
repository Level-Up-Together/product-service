# JWT 토큰 라이프사이클 (Web / App)

JWT 발급·만료·슬라이딩(자동 연장) 로직이 백엔드, 웹(브라우저), 앱(RN WebView 하이브리드)에서 각각 어떻게 동작하는지 정리한다.

- 발급 주체는 **백엔드 단일** (`user-service` + `lut-platform-infra`의 `JwtUtil`)
- 소비 방식이 다르다: **웹 = httpOnly 쿠키**, **앱 = AsyncStorage + WebView 브릿지**
- 정책 목표: **재로그인 최소화** — 활성 사용자는 재로그인 없이 유지, 90일 무접속 또는 1년 경과 시에만 재인증

## 한눈에 보기 (prod 기준)

| 항목 | 값 | 비고 |
|------|-----|------|
| Access Token 만료 | **24시간** | `expires_in: 86400` (초)로 응답 |
| Refresh Token 만료 | **90일** | JWT 자체 exp = **무접속 허용 기간** |
| Refresh rotation | **매 갱신 시** (임계 90일 = 수명) | reissue마다 새 refresh 발급 → 90일 시계가 매번 리셋 |
| rotation grace | **2분** | 응답 유실 재시도 보호 — 직전 토큰으로 재시도 시 현재 토큰 재전달 |
| 슬라이딩 절대 상한 | **365일** | **세션 최초 loginTime 기준** (토큰 iat 아님). 초과 시 `010106` → 재로그인 |
| Redis 세션 TTL | **refresh 잔여 유효기간 + 1일 버퍼** | 로그인/갱신 시마다 재설정 — 토큰 유효한데 세션만 소멸하는 상태 없음 |
| 웹 refresh 쿠키 maxAge | **refresh JWT exp 기반** + 매 갱신 재설정 | 쿠키가 토큰보다 먼저 죽지 않음 |
| 웹 device_id | 브라우저별 UUID 쿠키 (**400일**, JS-readable) | 브라우저 간 세션 덮어쓰기 없음 |
| reissue 인증 실패 응답 | **HTTP 401** + code `0101xx` | `JwtExceptionHandler` (서버 장애 500과 구분) |
| 앱 갱신 타이밍 | access 만료 **5분 전** setTimeout | RN `useAuth` |
| 웹 갱신 타이밍 | 1분 주기 폴링 + 만료 **5분 전** / 요청 직전 / 401 후 | `TokenRefreshManager` + `fetch-client` |

**결과적인 사용자 경험**: 웹·앱 모두 90일에 한 번만 접속하면 로그인 유지, 최대 1년 후 1회 재인증.

---

## 1. 토큰 발급 (백엔드 공통)

### 생성

`level-up-together-platform/infra/.../global/security/JwtUtil.java`

- 서명: **HS512** (secret 최소 64바이트)
- Access/Refresh 모두 동일 구조, `type` 클레임(`access`/`refresh`)으로 구분
- 클레임: `sub`(userId), `jti`(UUID — 블랙리스트 키), `user_id`, `email`, `device_id`, `type`, `iat`, `exp`

### 만료 설정 (환경별)

`@Value("${app.jwt.access-token-expiry:900000}")` — yml 미설정 시 코드 기본값(access 15분 / refresh 7일)이지만, **모든 환경에서 yml이 오버라이드**한다.

| 환경 | 설정 위치 | Access | Refresh | rotation 임계 | 절대 상한 |
|------|----------|--------|---------|--------------|----------|
| test / push-test | `app/src/main/resources/config/application-{test,push-test}.yml` | 24시간 | **7일** | **3일** | **30일** |
| dev / prod | Config Server → `config-repository/jwt/jwt-{dev,prod}.yml` | 24시간 | **90일** | **90일 (=매 갱신 rotation)** | **365일** |

> dev/prod 값은 `product-service-{dev,prod}.yml`이 아니라 **`config-repository/jwt/` 디렉터리**에 있다.
> test 프로필의 짧은 값은 테스트 시나리오 검증용으로 의도적으로 유지한다.

### 발급 API

| 클라이언트 | 엔드포인트 | 입력 | 응답 |
|-----------|-----------|------|------|
| 웹 | `POST /oauth/callback/{provider}` | 인가 코드(`code`) + `device_type=web`, `device_id`(브라우저별 UUID) | JSON body |
| 앱 | `POST /oauth/mobile/login` | 소셜 SDK 토큰(`access_token`/idToken) + device 정보, locale/timezone | JSON body |

응답 (기존 사용자): `access_token`, `refresh_token`, `token_type: Bearer`, `expires_in`(초), `user_id`, `device_id`, `nickname_set`.
**쿠키는 백엔드가 발급하지 않는다** — 항상 JSON body이며, 쿠키 전환은 웹 프론트(Next.js Route Handler)의 몫.

신규 사용자는 DB INSERT 없이 `signup_token`만 발급(Redis TTL **30분**, `SignupTokenService`) → 닉네임/약관 후 `/oauth/complete-signup`에서 최종 가입 + 정식 토큰 발급 (웹은 이때도 `device_id` 쿠키 값을 전달).

### 세션 저장 (Redis, `MultiDeviceTokenService`)

- `session:{userId}:{deviceType}:{deviceId}` (Hash): accessToken, refreshToken, **previousRefreshToken/previousRefreshTime**(grace 재시도용), loginTime, lastRefreshTime 등
- TTL: **refresh 토큰 잔여 유효기간 + 1일 버퍼** — 로그인 시와 갱신 시마다 재설정
- `userSessions:{userId}` (Set): 유저의 활성 세션 키 목록 (동일 TTL로 연장)
- `blacklist:{jti}`: 무효화된 토큰 (TTL = 토큰 잔여시간)
- DB 테이블 없음, 전부 Redis

---

## 2. 검증 (모든 API 요청)

`level-up-together-platform/infra/.../global/security/JwtAuthenticationFilter.java`

1. **Authorization: Bearer 헤더 우선**, 없으면 **쿠키**에서 읽음 (쿠키명 `${app.cookie.access-token-name}` — prod `access_token`, dev `dev_access_token`)
2. 서명/만료/블랙리스트 검증 → 통과 시 SecurityContext 설정, 실패 시 미설정 → downstream 401
3. 예외: `/jwt/reissue` 요청은 **만료된 access token도 서명만 유효하면 통과** (`X-Token-Expired` attribute 설정)

즉 웹은 httpOnly 쿠키 자동 전송으로, 앱은 Bearer 헤더로 인증된다. 둘 다 같은 필터를 탄다.

---

## 3. 갱신·슬라이딩 (백엔드)

### `POST /jwt/reissue` (`JwtService.reissue`)

```
refresh_token 검증 (만료/블랙리스트/서명)
  → 절대 상한 확인: 세션 loginTime 기준 365일     # rotation이 iat를 리셋하므로 세션 기준이 정확
  → Redis 세션의 저장된 refresh와 문자열 비교
      ├─ 일치 → 정상 갱신
      ├─ 불일치 + grace window(2분) 내 직전 토큰 → 재시도로 판정, 현재 세션 토큰 재전달
      └─ 그 외 → 010102 거절
  → 새 access token 발급 (항상)
  → 슬라이딩 판정 (SlidingExpirationService, property 기반):
      prod는 임계=수명(90일)이라 매 갱신마다 rotation
      → 새 refresh 발급, 구 토큰은 previous로 보관(grace용), 한 세대 전 previous는 블랙리스트
  → Redis 세션 갱신 + TTL = 새 refresh 잔여시간 + 1일
```

응답에 `refresh_token_renewed: true|false` 포함 (grace 재시도 응답도 `true` — 클라이언트가 보낸 토큰과 다르므로).

### rotation grace (응답 유실 보호)

매 갱신 rotation 체제에서는 "서버는 rotation했는데 클라이언트가 응답을 못 받는" 레이스가 생긴다.
이를 위해 rotation 직후 **2분** 동안은 직전(previous) refresh 토큰으로 재시도하면 현재 세션의
토큰을 그대로 재전달한다. 동시에 유효한 refresh는 최대 2개(current + grace 중 previous)로 제한되고,
previous는 다음 rotation 또는 로그아웃 시점에 블랙리스트된다.

### 절대 상한 (365일)

rotation이 매번 새 토큰(iat 리셋)을 만들기 때문에 **토큰 나이로는 상한을 강제할 수 없다**.
세션 Hash의 `loginTime`(최초 로그인 시각) 기준으로 판정하며, 초과 시 `010106`으로 거절 → 재로그인.
loginTime이 없는 레거시 세션은 관대하게 통과시킨다.

### 실패 응답 (HTTP 401)

인증 실패는 `JwtException` → user-service 로컬 `JwtExceptionHandler`가 **HTTP 401** + ApiResult로 응답한다
(예기치 못한 서버 오류 `TOKEN_REISSUE_FAILED(010108)`는 기존대로 500):

| code | 의미 |
|------|------|
| `010102` | NOT_VALID_REFRESH_TOKEN (만료·불일치) |
| `010105` | BLACKLISTED_JWT |
| `010106` | TOKEN_EXCEEDED_MAXIMUM_LIFETIME (절대 상한 초과) |

클라이언트는 status 401 또는 code로 재로그인 여부를 판단한다 (RN·웹 모두 기존 처리 로직과 호환).

### 만료 세션 정리 (`TokenMaintenanceScheduler`)

- 매일 02:00 KST: `session:*` 순회, refresh 만료 세션 삭제
- 매일 02:30 KST: 고아 `userSessions:*` 참조 정리
- (Redis TTL이 refresh 수명과 정렬되어 있어 대부분은 TTL로 자동 소멸)

---

## 4. Web (브라우저)

### 저장: 쿠키 (`src/lib/auth/cookie-options.ts`)

| 쿠키 | httpOnly | maxAge | 용도 |
|------|----------|--------|------|
| `access_token` | O | `expires_in` (24시간) | 인증 본체 (백엔드가 쿠키에서 직접 읽음) |
| `refresh_token` | O | **refresh JWT exp 기반** (`getRefreshTokenMaxAgeSeconds`, prod 90일) | 갱신용. 디코딩 실패 시만 fallback 7일 |
| `access_expires_at` | X | access와 동일 | JS 만료 판단용 epoch ms 인디케이터 |
| `refresh_expires_at` | X | refresh와 동일 (JWT exp 값) | 〃 |
| `device_id` | X | **400일** (Chrome 쿠키 상한) | 브라우저별 세션 분리용 UUID — middleware가 없으면 발급 |

- 프리픽스: dev/local은 `NEXT_PUBLIC_COOKIE_NAME_PREFIX=dev_` → `dev_access_token` 등. prod는 무프리픽스
- Domain=`level-up-together.com` (프론트 ↔ `api.` 서브도메인 쿠키 공유), SameSite=Lax

### 발급 경로

- 브라우저 로그인: `/api/auth/callback/{provider}` Route Handler가 `device_id` 쿠키를 읽어 백엔드
  `/oauth/callback/{provider}?...&device_type=web&device_id={uuid}`로 전달 → **302 리다이렉트 + Set-Cookie**
- 신규 가입: `/oauth/complete-signup` 호출 시 클라이언트가 `getDeviceId()`(JS-readable 쿠키)로 device_id 전달
- 앱 웹뷰 진입: `/auth/mobile?access_token=...` → `/api/auth/set-cookies` POST로 쿠키 설정 (아래 5절)

### 갱신 (3중 장치, 모두 앱에서는 비활성)

| 장치 | 파일 | 동작 |
|------|------|------|
| 주기 갱신 | `components/TokenRefreshManager.tsx` | 1분 인터벌 + visibilitychange + online 이벤트. 만료됐거나 **5분 이내 임박**이면 갱신 |
| 선제 갱신 | `lib/api/fetch-client.ts` | 요청 직전 만료면 갱신 후 요청 (QA-210) |
| 401 복구 | `lib/api/fetch-client.ts` | 401 → 갱신 → 1회 재시도, 재차 401이면 `session_expired` 로그인 리다이렉트 |

- 실제 갱신: `token-service.ts` → `/api/auth/refresh` → 백엔드 `/jwt/reissue` (`device_type=web`, `device_id`는 쿠키 값)
- **웹 슬라이딩**: 갱신 성공 시마다 refresh 쿠키를 exp 기반 maxAge로 재설정 — prod는 매 갱신 rotation이므로 90일 시계가 매번 리셋된다
- 동시성: 탭 내 single-flight promise + **BroadcastChannel**(`lut-auth-token-refresh`)로 멀티탭 중복 갱신 방지 (외부 탭 완료 대기 10초)
- `middleware.ts`는 갱신하지 않는다 — 만료 판단·보호 경로 리다이렉트·stale 쿠키 정리 + **device_id 발급**만 담당

### 로그아웃

`/api/auth/logout` — POST(fetch)와 **GET(302, WKWebView 대비)** 두 방식. host-only/Domain-scoped 두 variant 모두 삭제 + localStorage/React Query 캐시 정리. (device_id 쿠키는 로그아웃해도 유지 — 기기 식별자이므로)

---

## 5. App (React Native)

### 저장

AsyncStorage `@auth_state` — accessToken, refreshToken, userId, deviceId, **expiresAt(epoch ms)**, nicknameSet. 쿠키 아님.

### 로그인

네이티브 소셜 SDK(Google/Kakao/Apple) → `POST /oauth/mobile/login` (`src/services/authService.ts`) → JSON 응답을 AsyncStorage에 저장. `device_id`는 실제 기기 UUID → 기기별 독립 세션.

### 슬라이딩 갱신 (`src/hooks/useAuth.ts`)

- `TOKEN_REFRESH_MARGIN = 5분`: `expiresAt - 5분` 시점에 setTimeout으로 `POST /jwt/reissue` 호출
- prod는 매 갱신 rotation → 새 access+refresh로 교체 → 다음 타이머 재예약 → **접속할 때마다 90일 시계 리셋**
- 앱 재시작 시 저장된 `expiresAt` 기준으로 즉시 갱신 또는 타이머 재설정
- 중복 방지: `isRefreshingRef` single-flight (+백엔드 grace 2분이 응답 유실 재시도를 보호)
- 실패 처리: 401/403 또는 code `010102`/`010105`/`010106` → `REFRESH_TOKEN_EXPIRED` → 강제 로그아웃

### WebView 연동 (웹과의 토큰 동기화)

```
[최초 로그인]  RN → WebView URL: /auth/mobile?access_token=..&refresh_token=..&nickname_set=..
              → 웹이 /api/auth/set-cookies 호출로 httpOnly 쿠키 설정 (refresh maxAge는 JWT exp 기반)
[갱신 시마다]  RN → postMessage {type:'tokenRefresh', accessToken, refreshToken}
              → 웹 MobileTokenListener가 수신 → /api/auth/set-cookies 재호출
[401 대비]    웹 fetch-client는 쿠키 미전송 상황(WKWebView 교차 서브도메인)에 대비해
              메모리 브릿지 토큰(setBridgedAccessToken)을 Bearer 헤더로 fallback
```

- 앱 웹뷰 안에서는 `isRunningInNativeApp()` 판정으로 웹의 TokenRefreshManager/선제 갱신이 **비활성** — 갱신 주체는 RN 단일

---

## 6. Web vs App 비교

| | Web (브라우저) | App (RN + WebView) |
|---|---|---|
| 토큰 저장 | httpOnly 쿠키 (+JS용 만료 인디케이터 쿠키) | AsyncStorage (네이티브) → 쿠키는 웹뷰에 위임 |
| 인증 전달 | 쿠키 자동 전송 | Bearer 헤더 (웹뷰 내부는 쿠키 + 브릿지 fallback) |
| 갱신 주체 | 웹 3중 장치 (폴링/선제/401 복구) | RN `useAuth` 단일 (웹 장치는 비활성) |
| 갱신 타이밍 | 만료 5분 전 (1분 폴링) | 만료 5분 전 (setTimeout) |
| device_id | 브라우저별 UUID 쿠키 (400일) → 기기별 세션 | 기기 UUID → 기기별 세션 |
| 실효 세션 수명 (prod) | **90일 무접속까지 / 최대 365일** | **90일 무접속까지 / 최대 365일** |
| 로그아웃 | 쿠키 삭제 (POST+GET 302) | AsyncStorage 삭제 + 웹에 logoutConfirmed 위임 |

---

## 7. 정책 이력 및 남은 과제

### 2026-07 정책 개편으로 해결된 문제

1. ~~웹 refresh 쿠키 7일 하드코딩~~ → 쿠키 maxAge를 JWT exp 기반으로 변경 + 매 갱신 재설정 (웹 슬라이딩)
2. ~~Redis 세션 TTL 7일/30일 하드코딩~~ → refresh 잔여시간 + 1일 버퍼로 정렬
3. ~~reissue 인증 실패가 HTTP 500~~ → `JwtExceptionHandler`로 401 응답 (code는 기존 `0101xx` 유지)
4. ~~웹 device_id=`web` 고정 → 브라우저 간 세션 덮어쓰기~~ → 브라우저별 UUID 쿠키 (기존 `web` 세션은 자연 소멸, 새 로그인부터 적용)
5. ~~절대 상한 365일이 실질 미동작~~ (토큰 iat 기준이라 rotation마다 리셋) → 세션 loginTime 기준으로 판정

### 남은 과제 (별도 티켓)

1. **refresh 토큰 Redis 평문 저장** → 해시화 (세션 조회 API의 토큰 원문 반환 정리 포함)
2. **Silent 소셜 재로그인** — refresh 만료/절대 상한 도달 시 로그인 화면 없이 자동 재인증 (RN)
3. **Face ID** — 앱 잠금·민감 작업 확인 레이어
4. **Access 토큰 단축 (24h → 1h)** — RN 백그라운드 복귀 선갱신 + 웹뷰 401 브릿지 보강과 함께 진행
   (현재 24h 유지 이유: 복귀 직후 웹뷰 API 콜과 RN 갱신의 레이스가 1h에서는 일상 경로가 됨)

## 관련 파일 인덱스

| 영역 | 파일 |
|------|------|
| 토큰 생성/검증 | `platform/infra/.../security/JwtUtil.java`, `JwtAuthenticationFilter.java` |
| 발급/갱신 서비스 | `user-service/.../oauth/application/{Oauth2Service,JwtService,SlidingExpirationService,MultiDeviceTokenService,SignupTokenService}.java` |
| 401 응답 | `user-service/.../core/exception/handler/JwtExceptionHandler.java` |
| API | `user-service/.../oauth/api/{Oauth2Controller,JwtController}.java` |
| 만료 설정 | `app/src/main/resources/config/application-test.yml`, `config-repository/jwt/jwt-{dev,prod}.yml` |
| 세션 정리 | `user-service/.../oauth/scheduler/TokenMaintenanceScheduler.java` |
| 웹 쿠키/갱신 | `frontend/src/lib/auth/{cookie-options,token-service,session-manager}.ts`, `src/app/api/auth/{set-cookies,refresh,logout}/route.ts`, `src/components/TokenRefreshManager.tsx`, `src/lib/api/fetch-client.ts`, `src/middleware.ts` |
| 웹 로그인 device_id | `frontend/src/app/api/auth/actions.ts`, `src/app/api/auth/callback/{kakao,google,apple}/route.ts` |
| 앱 토큰 관리 | `LevelUpTogetherReactNative/src/hooks/useAuth.ts`, `src/services/authService.ts`, `App.tsx` |
| 웹뷰 브릿지 | `frontend/src/app/auth/mobile/page.tsx`, `src/components/MobileTokenListener.tsx` |
