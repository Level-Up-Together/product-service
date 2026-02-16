# PLAN.md

> Source of Truth: [Notion - Platform 분리 + Admin 멀티모듈 + BFF 구성 플랜](https://www.notion.so/linkpark/Platform-Admin-BFF-3068c64c554381558c91ea67edf37a1d)

## 완료된 작업

- **Phase 1**: Platform 별도 레포지토리 분리 (2026-02-12)
- **platform/infra 모듈 제거**: 76개 파일 `service/src/`로 통합 + `includeBuild` 설정 (2026-02-15)
- **Phase 3**: 서비스 간 순환 의존 제거 — Facade 인터페이스 + DTO 전환 (2026-02-15)
  - 4개 순환 쌍 해소: user↔guild, user↔gamification, user↔support, guild↔gamification
  - platform kernel에 Facade 인터페이스 3개 + DTO 23개 추가
  - MVP 72개 파일 전환, 전체 테스트 통과
  - 잔여 정리 완료: BffSeasonService/BffHomeService → GamificationQueryFacade 전환 (2026-02-15)

## 다음 작업

> 현재 작업은 Admin Backend PLAN.md Phase 8로 이동.
> Admin Backend: `/Users/pink-spider/Code/github/Level-Up-Together/level-up-together-mvp-admin/PLAN.md`

### 기타 개선 사항

- [ ] platform 레포 GitHub Packages publish (facade 포함 버전)
- [ ] 테스트 커버리지 확인 (JaCoCo 70% 목표)
