# Internal API (Admin Backend ↔ MVP 연동)

`/api/internal/**` 경로는 `SecurityConfig`에서 `permitAll`이지만 **공유 시크릿 헤더 인증 + VPC 내부 접근**으로 보호. Admin Backend가 MVP의 도메인 데이터를 읽거나 액션을 실행할 때 사용.

## 인증 (LUT-244)

네트워크 격리(VPC)만으로는 dev(공인망 홈서버)가 무인증 노출되므로, 애플리케이션 계층에서 공유 시크릿 헤더를 검증한다.

- **product-service**: `InternalApiKeyFilter`가 `/api/internal/**` 요청의 `X-Internal-Api-Key` 헤더를 `app.security.internal-api.key`와 상수시간 비교. 불일치/누락 시 401(`000401`).
- **admin-service**: `MvpFeignConfig.mvpInternalApiKeyInterceptor`가 모든 MVP 내부 Feign 호출에 `app.mvp.internal-api-key`를 헤더로 주입.
- **시크릿**: config-repository에 환경별 `{cipher}` 암호화 저장(dev/prod 각각 다른 값, product ↔ admin 동일 값이어야 매칭).
- **fail-open**: 키 미설정(공백)이면 검증/주입을 건너뛴다 → config 선배포 없이 무중단 롤아웃 가능. 배포 후 config에 키가 있으면 자동 활성화.

## 도메인별 베이스 경로

| 도메인          | 베이스 경로                                                                                                                                                     | 용도                                             |
|--------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------|
| user         | `/api/internal/users`                                                                                                                                      | 유저 검색/조회/통계, blacklist, 신고 처리 (suspend/warn)   |
| user         | `/api/internal/daily-mvp-exclusions`                                                                                                                       | MVP 제외 명단 관리                                   |
| user         | `/api/internal/terms`                                                                                                                                      | 약관 관리                                          |
| guild        | `/api/internal/guilds`                                                                                                                                     | 길드 검색, 통계, 활성화 토글, **신고 처리 (ban-from-report)** |
| guild        | `/api/internal/guilds/{guildId}`                                                                                                                           | 길드 게시글 어드민                                     |
| mission      | `/api/internal/missions`, `/api/internal/mission-templates`, `/api/internal/mission-participants`, `/api/internal/mission-comments`                        | 미션 어드민                                         |
| feed         | `/api/internal/feeds`, `/api/internal/feed-comments`                                                                                                       | 피드 어드민                                         |
| meta         | `/api/internal/{user,guild}-level-configs`, `/api/internal/attendance-reward-configs`, `/api/internal/mission-categories`, `/api/internal/profanity-words` | 메타 설정                                          |
| gamification | `/api/internal/{achievements,achievement-categories,titles,title-grants,events,seasons,check-logic-types,experience-history,mvp-history}`                  | 게임화 어드민                                        |
| gamification | `/api/internal/seasons/{id}/rank-rewards`                                                                                                                  | 시즌 순위 보상 어드민                                   |

## 신고 처리 워크플로우 (Admin → MVP)

| 단계                       | 엔드포인트                                                                 | 처리                                                 |
|--------------------------|-----------------------------------------------------------------------|----------------------------------------------------|
| 신고 접수 (User → MVP)       | `POST /api/v1/reports`                                                | `ReportService.createReport()` (Admin Backend로 전달) |
| 신고 상태 확인                 | `GET /api/v1/reports/check?targetType=&targetId=`                     | 처리 대기 중 여부                                         |
| **WARNING (PR3)**        | `POST /api/internal/users/{userId}/warn-from-report`                  | 누적 경고 +1, 임계치 도달 시 자동 USER_SUSPENDED               |
| **USER_SUSPENDED (PR2)** | `POST /api/internal/users/{userId}/suspend-from-report`               | 30일 정지, 누적 3회면 영구강퇴                                |
| **GUILD_BANNED (PR1c)**  | `POST /api/internal/guilds/{guildId}/ban-from-report`                 | 길드 차단 처리                                           |

처리 결과는 `notification-service`의 Redis Streams (`AppPushMessageProducer`)를 통해 사용자에게 푸시 + in-app 알림 발송.
