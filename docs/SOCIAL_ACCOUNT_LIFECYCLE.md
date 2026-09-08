# 소셜 로그인 계정 라이프사이클 — 연동 해제 정합 (LUT-476/477)

회원 탈퇴와 소셜(apple/kakao/google) 연동 해제가 **양방향**으로 정합을 이루도록 하는 구조 정리.
코드는 dev/prod 동일하며 환경 차이는 **콘솔 등록·config 값**뿐이다.

## 1. 방향별 요약

| | 우리 → 소셜 (탈퇴 시 발신) | 소셜 → 우리 (수신 웹훅) |
|---|---|---|
| **Apple** | `/auth/revoke` — **App Store 5.1.1(v) 필수**. refresh token 미보유라 미구현 → **LUT-477** | S2S Notification — **구현·prod 등록 완료** (`POST /api/v1/oauth/apple/webhook`) |
| **Kakao** | 어드민 키 `POST /v1/user/unlink` — **구현 완료** (best-effort) | 연결 끊기 웹훅 + SSF — **구현·dev/prod 등록 완료** |
| **Google** | token revoke — **의도적 생략** (토큰 미저장, 필수 아님. 유저가 구글 계정 설정에서 자체 해제 가능) | RISC(Cross-Account Protection) — **선택사항, 미구현 결정** |

"탈퇴 시 애플에 알림을 줘야 한다"의 정확한 실체는 ① `/auth/revoke` 호출(발신)이고,
S2S Notification Endpoint는 ② 반대 방향(Apple→우리, 유저가 Apple ID 설정에서 연결 해제/계정 삭제 시 통지)이다.

## 2. 유저 매핑 — `users.provider_user_id` (V006)

웹훅에는 이메일 없이 **공급자 사용자 ID**(kakao 회원번호 / apple·google sub)만 오므로 매핑 컬럼이 필요하다.

- 저장 시점: **가입 시**(`SignupSessionData.providerUserId` → INSERT) + **로그인 시 백필**(`Oauth2Service.findExistingUser`)
- 컬럼 신설 전 가입자는 다음 로그인까지 null → 그 사이 수신된 웹훅은 매핑 실패로 **로그만 남긴다** (커버리지는 시간이 지나며 수렴)
- 조회: `UserRepository.findActiveByProviderAndProviderUserId` (인덱스 `idx_users_provider_user_id`)

## 3. 탈퇴 플로우 (발신)

`MyPageService.withdrawUser()` → `SocialUnlinkService.unlinkOnWithdrawal(user)`

- **전부 best-effort**: 예외를 삼키고 탈퇴는 계속 진행 (소셜 측 설정·수신 웹훅으로도 정합이 맞춰짐)
- kakao: `KakaoAdminFeignClient.unlink("KakaoAK {adminKey}", "user_id", 회원번호)` — 어드민 키/회원번호 미보유 시 스킵(로그)
- apple: 스킵 + 로그 (LUT-477에서 revoke 구현)
- google: 스킵 + 로그 (설계상 생략)

## 4. 수신 웹훅

### 4.1 Kakao — `KakaoWebhookController` (`/api/v1/oauth/kakao/webhook`)

| 엔드포인트 | 메서드 | 인증 | 처리 |
|---|---|---|---|
| `/unlink` | GET/POST | `Authorization: KakaoAK {adminKey}` + app_id 검증 | 연결 해제 → **탈퇴 처리** (`withdrawUser` 재사용, 멱등) |
| `/status` | POST (`application/secevent+jwt`) | SET(JWT) 서명·iss·aud 검증 | SSF 이벤트 분기 |

SSF 이벤트 매핑:
- `USER_UNLINKED` → 탈퇴 처리 (unlink 웹훅과 동일)
- `ACCOUNT_DISABLED` / `TOKENS_REVOKED` / `SESSIONS_REVOKED` / `CREDENTIAL_*` → **전 기기 로그아웃만** (탈퇴 아님)
- `ACCOUNT_ENABLED` 등 → no-op

### 4.2 Apple — `AppleWebhookController` (`POST /api/v1/oauth/apple/webhook`)

- 본문: `{"payload": "<JWS>"}` (방어적으로 `signedPayload` 키도 허용)
- 검증: Apple JWKS(https://appleid.apple.com/auth/keys, kid 캐시)로 RS256 서명 + `iss=https://appleid.apple.com` + exp
  + aud는 `app.oauth2.apple-webhook.audiences` **설정 시에만** 검증 (kakao appId null-스킵 관례)
- `events` 클레임은 이벤트 하나를 담은 **JSON 문자열**: `{"type","sub","event_time"}`
- `consent-revoked` / `account-delete` → sub 매핑 탈퇴 처리. `email-disabled/enabled` 등 → 로그만
- 위조/파싱 실패는 `000720` + `error.apple.webhook.*` 로 거부

### 4.3 보안 설정

- SecurityConfig permitAll: `/api/v1/oauth/kakao/webhook/**`(메서드 무제한), `POST /api/v1/oauth/apple/webhook`
  — 페이로드 자체 검증(어드민 키/SET/JWS)이 인증을 대신한다. `SecurityConfigPublicEndpointTest`에 가드 등록됨

## 5. 콘솔·config 등록 현황 (2026-09-08)

### 등록된 URL

| 대상 | prod | dev |
|---|---|---|
| Kakao 연결 끊기 (GET) | `https://api.level-up-together.com/api/v1/oauth/kakao/webhook/unlink` | `https://dev.level-up-together.com:8443/...` |
| Kakao SSF (POST, User Unlinked+Tokens Revoked) | `.../webhook/status` | `.../webhook/status` |
| Apple S2S | `https://api.level-up-together.com/api/v1/oauth/apple/webhook` | **등록 불가** |

- ⚠️ **Apple S2S는 443 포트만 허용** — dev(8443)는 콘솔이 거부한다("invalid Port '8443', Expected: 443").
  dev 검증은 단위 테스트(서명 검증 포함)로 갈음, 실동작 확인은 prod
- 카카오는 8443 포트 허용 (dev 등록 정상)
- 콘솔 위치: Kakao Developers > 앱 > 웹훅 / Apple Developer > Identifiers > App ID > Sign in with Apple 편집

### config (config-repository, `app.oauth2.*`)

```yaml
kakao-webhook:
  admin-key: "{cipher}..."   # 각 환경 config-server /encrypt 로 암호화 (prod 는 외부 접근 불가 — SSM 으로 lut-ec2-1 내부에서)
  rest-api-key: ...          # SSF aud 검증용
  app-id: "1380711"          # prod=1380711 / dev=1379072
apple-webhook:               # prod 만 (dev 는 Apple 등록 불가라 불필요)
  audiences:
    - com.level-up-together.prod
```

미설정 시 동작: admin-key 없으면 unlink 발신 스킵 + 웹훅 인증 실패, app-id/audiences 없으면 해당 검증만 생략.

## 6. 알려진 한계·후속

1. **LUT-477 — Apple 탈퇴 revoke**: 로그인이 id_token만 수신해 refresh token이 없다.
   클라(RN/웹)가 authorizationCode를 전달 → 서버 code 교환 → refresh token 암호화 저장 → 탈퇴 시 `/auth/revoke`.
   SIWA 전용 .p8 키 필요(IAP 키와 별개). 심사 5.1.1(v) 컴플라이언스라 우선순위 높음
2. **백필 전 유저**: provider_user_id 가 없는 유저의 웹훅은 처리 불가(로그만) — 로그인 백필로 자연 해소
3. **prod config-server 스테일 가능성**: config push 후 반영 안 되면 클론 fetch 필요 (dev 에서 동일 이슈 확인됨 — 메모리 `dev-config-server-stale-clone` 참조)
4. Google RISC 는 보안 강화가 필요해지면 별도 티켓으로 도입
