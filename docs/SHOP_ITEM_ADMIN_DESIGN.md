# QA-225 상점 아이템 관리 설계안

> 어드민에서 상점 아이템을 관리(목록/생성/수정/삭제/활성화)하는 기능.
> 위치: 시스템 운영 > 사용자 설정 > **아이템 관리** > **아이템 목록**
> 작성: 2026-07-10 (조사 기반 설계, 구현 전)

## 1. 요구사항 요약 (Jira)

- **목록 컬럼**: ID, 미리보기(이미지), 아이템명, 희귀도(일반~신화), 생성일, 활성화, 관리(활성화/수정(모달)/삭제 버튼)
- **생성/수정 모달**: 아이템명, 이미지 업로드(사이즈 가이드 하드코딩), 아이템 타입, 희귀도
- **아이템 타입**: `BASIC` / `FULL` / `HEAD` / `EFFECT`
- **희귀도**: `COMMON` / `UNCOMMON` / `RARE` / `EPIC` / `LEGENDARY` / `MYTHIC`
- **이미지 사이즈 가이드**: BASIC 240×180 png, FULL 260×260 png, HEAD 50×50 png (EFFECT는 티켓에 미기재)

## 2. 아키텍처 결정

### 2.1 데이터 위치: product-service `gamification-service` (gamification_db)

상점 아이템은 **앱(상점)이 소비할 도메인 데이터**이므로 admin_db가 아닌 gamification_db에 둔다.

- QA-220 다이아 도메인이 이미 상점 연동을 준비해 둠: `DiamondType.SHOP`, `diamond_history.source_id`(= 상점 아이템 ID)
- 구매 트랜잭션(다이아 차감 + 아이템 지급)이 같은 서비스/DB에서 처리 가능
- 어드민 연동은 **칭호 관리와 동일한 위임 패턴**: admin-frontend → admin-service(Feign) → product-service `/api/internal/**`

### 2.2 재사용 요소

| 요소 | 재사용 대상 |
|---|---|
| 희귀도 enum | kernel `TitleRarity` (COMMON~MYTHIC, 한글명 일반~신화 + colorCode 보유 — 티켓 요구와 정확히 일치) |
| Internal API 템플릿 | `TitleAdminInternalController` (GET 검색 / POST / PUT / PATCH toggle-active / DELETE) |
| 이미지 인프라 | Strategy 패턴 (`S3*ImageStorageService` @Profile("prod") / `Local*ImageStorageService`), S3 키 `shop-items/{uuid}.ext`, CloudFront CDN |
| admin-service CRUD 템플릿 | `FeaturedGuildController`(컨트롤러 형태) + gamification Feign 위임 패턴 |
| admin-frontend 템플릿 | `notice/page.tsx` (DataTable + FormModal + toggle), `lib/api.ts`의 `noticesApi` 패턴 |

## 3. 백엔드 — product-service (gamification-service)

### 3.1 신규 패키지: `gamificationservice/shop/`

```
shop/
├── api/ShopItemAdminInternalController.java   # /api/internal/shop-items
├── api/dto/ShopItemAdminRequest.java          # record, snake_case
├── api/dto/ShopItemAdminResponse.java
├── application/ShopItemAdminService.java      # @Transactional("gamificationTransactionManager")
├── application/ShopItemImageStorageService.java   # 인터페이스
├── application/S3ShopItemImageStorageService.java  # @Profile("prod")
├── application/LocalShopItemImageStorageService.java
├── domain/entity/ShopItem.java
├── domain/enums/ShopItemType.java             # BASIC | FULL | HEAD | EFFECT
└── infrastructure/ShopItemRepository.java
```

### 3.2 `shop_item` 스키마 (gamification_db)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGSERIAL PK | |
| name | VARCHAR(50) NOT NULL | 아이템명 (한글, 기본) |
| name_en / name_ar / name_ja | VARCHAR(50) | 아이템명 다국어 (Title 패턴) |
| item_type | VARCHAR(20) NOT NULL | BASIC/FULL/HEAD/EFFECT |
| rarity | VARCHAR(20) NOT NULL | TitleRarity |
| image_url | VARCHAR(500) | 업로드 후 CDN URL |
| price | INTEGER NOT NULL DEFAULT 0 | 다이아 가격 |
| is_active | BOOLEAN NOT NULL DEFAULT true | |
| created_at / modified_at | TIMESTAMP | UTC (JPA Auditing) |

- DDL: `level-up-together-sql/queries/developing/0.1.3/V003__qa225_shop_item.sql`
- 인덱스: `(item_type)`, `(is_active)`
- 삭제는 물리 삭제로 시작하되, **판매 이력 발생 이후에는 soft delete로 전환 필요** (§6)

### 3.3 Internal API (`/api/internal/shop-items`, permitAll — VPC 내부)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/internal/shop-items?page&size&itemType&rarity&isActive&keyword` | 페이징 목록 |
| GET | `/api/internal/shop-items/{id}` | 단건 |
| POST | `/api/internal/shop-items` | 생성 |
| PUT | `/api/internal/shop-items/{id}` | 수정 |
| PATCH | `/api/internal/shop-items/{id}/toggle-active` | 활성화 토글 |
| DELETE | `/api/internal/shop-items/{id}` | 삭제 |
| POST | `/api/internal/shop-items/images` | multipart 이미지 업로드 → `{image_url}` 반환 |

이미지 업로드 흐름: admin-frontend(FormData) → admin-service(multipart 프록시, Feign form encoder 필요) → product-service(S3/Local Strategy) → CDN URL 반환 → 생성/수정 요청에 `image_url`로 전달.
`@ModerateImage`는 어드민 업로드이므로 미적용(내부 신뢰), 필요 시 추후 적용.

## 4. 백엔드 — admin-service

```
adminservice/gamification/shopitem/
├── api/ShopItemController.java            # /api/admin/shop-items
├── api/dto/...                            # Request/Response (String enum + 검증)
├── application/ShopItemService.java       # Feign 호출 + 예외 매핑
└── infrastructure/ShopItemFeignClient.java  # → product-service /api/internal/shop-items
```

- 권한: `@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('SHOP_ITEM_READ|WRITE|DELETE')")` (칭호/시즌 패턴)
- 메뉴/권한 시드 SQL (admin_db): `menu`에 "아이템 관리"(그룹) > "아이템 목록"(path `/item`) 추가 + `permission`에 SHOP_ITEM_READ/WRITE/DELETE + SUPER_ADMIN 역할 매핑
  - 파일: `queries/developing/0.1.3/V004__qa225_admin_menu_shop_item.sql`
  - 사이드바는 `/api/admin/menus/me` 기반 동적 렌더링이라 **프론트 메뉴 코드 수정 불필요, path만 일치하면 됨**

## 5. 프론트 — admin-frontend

- **라우트**: `src/app/(afterLogin)/item/page.tsx` (메뉴 path `/item`과 일치)
- **API 클라이언트**: `lib/api.ts`에 `shopItemsApi` 추가 (`getList/get/create/update/delete/toggle/uploadImage`)
  - `uploadImage`는 FormData 전송 (기존 `fetchApi`가 JSON 고정이면 multipart 분기 추가)
- **목록**: `DataTable` — ID / 미리보기(`<img>` 썸네일 48px) / 아이템명 / 희귀도(TitleRarity 한글명 + colorCode 뱃지) / 생성일 / 활성화(뱃지) / 관리(활성화·수정·삭제 버튼)
- **생성/수정 모달** (`notice` 페이지 `NoticeFormModal` 패턴):
  - 아이템명(text), 아이템 타입(select 4종), 희귀도(select 6종), 이미지(파일 선택 + FileReader 미리보기)
  - 아이템명 다국어 입력(ko 필수, en/ar/ja 선택), 가격(다이아, number)
  - 타입 선택에 따라 **사이즈 가이드 하드코딩 표시**: BASIC 240×180 / FULL 260×260 / HEAD 50×50 / EFFECT 260×260 (png)
  - 삭제는 confirm 후 실행
- i18n: next-intl 메시지 키 추가 (`item.*`)

## 6. 확정 사항 (2026-07-10 결정)

| # | 항목 | 결정 |
|---|---|---|
| 1 | 가격(다이아) 필드 | **포함** — `price INTEGER NOT NULL DEFAULT 0`, 모달 입력란 추가 |
| 2 | EFFECT 이미지 가이드 | **260×260 png** (FULL과 동일)로 하드코딩 — 디자인 확정 시 조정 |
| 3 | 아이템명 다국어 | **포함** — name_en/name_ar/name_ja (Title 패턴) |
| 4 | 판매 기간/노출 순서 | 1차 범위 제외 |
| 5 | 삭제 정책 | 판매 기능 구현 전까지 물리 삭제 허용 |

## 6.1 구현 노트 (2026-07-10 구현 완료)

- 어드민 이미지 업로드는 admin-service가 Feign multipart(`consumes=multipart/form-data`)로 product-service에 프록시한다. Spring Cloud OpenFeign 4.x SpringEncoder의 MultipartFile 지원 사용 — **dev 배포 후 실업로드 1회 확인 필요** (인코딩 이슈 시 RestTemplate 폴백 예정).
- dev(로컬 스토리지) 이미지 URL은 product-service 상대 경로라, 어드민 프론트는 `NEXT_PUBLIC_MVP_IMAGE_BASE_URL` env로 프리픽스를 보정한다 (미설정 시 프리뷰만 깨짐, prod CDN은 절대 URL이라 무관).
- admin-frontend `fetchApi`에 FormData 분기 추가 (Content-Type 자동 설정).

## 7. 구현 순서

1. **DDL**: V003(shop_item), V004(admin 메뉴/권한) — dev 적용
2. **product-service**: enum → entity → repository → service → internal controller → 이미지 storage → 테스트(`*Test` + RestDocs 불필요, internal은 컨트롤러 테스트만)
3. **admin-service**: Feign → service → controller(@PreAuthorize) → 테스트
4. **admin-frontend**: `shopItemsApi` → `/item` 페이지 (목록 + 모달)
5. dev 검증 → prod SQL 적용 → 배포

## 8. 테스트 체크리스트

- [ ] 생성: 타입/희귀도별 생성, 이미지 업로드 → CDN URL 저장
- [ ] 목록: 페이징/필터(타입·희귀도·활성화)/정렬(생성일)
- [ ] 수정: 이미지 교체 시 기존 파일 삭제
- [ ] 활성화 토글, 삭제
- [ ] 권한: SHOP_ITEM_* 없는 어드민 접근 차단, 메뉴 미노출
- [ ] 사이즈 가이드 문구가 타입 선택에 따라 갱신되는지
