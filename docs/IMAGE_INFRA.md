# Image Moderation & Storage

## Image Moderation (이미지 검증)

ONNX Runtime 기반 NSFW 이미지 자동 검증 시스템 (`global.moderation`).

### 아키텍처: Strategy Pattern + AOP

- `@ModerateImage` 어노테이션을 메서드에 적용하면 `MultipartFile` 파라미터를 자동 탐색하여 검증
- `ImageModerationAspect`가 `@Around` 어드바이스로 검증 실행
- `ModerationConfig`가 `moderation.image.provider` 설정에 따라 구현체 선택

### Provider 구현체

| Provider          | 클래스                               | 설명                               |
|-------------------|-----------------------------------|----------------------------------|
| `none` (기본값)      | `NoOpImageModerationService`      | 비활성화 (dev/test 환경)               |
| `onnx-nsfw`       | `OnnxNsfwModerationService`       | ONNX Runtime + OpenNSFW2 모델 ($0) |
| `aws-rekognition` | `AwsRekognitionModerationService` | AWS Rekognition (스켈레톤)           |

### 설정

```yaml
moderation:
  image:
    provider: onnx-nsfw   # none | onnx-nsfw | aws-rekognition
    onnx:
      model-path: classpath:models/nsfw.onnx
      nsfw-threshold: 0.8
```

### 적용된 서비스

- `GuildService` — 길드 이미지 업로드
- `MyPageService` — 프로필 이미지 업로드
- `EventController` — 이벤트 이미지 업로드
- `PinnedMissionExecutionStrategy` / `RegularMissionExecutionStrategy` — 미션 이미지

### 에러 코드

부적절 이미지 감지 시: `CustomException("000010", "error.moderation.inappropriate_image")`

---

## Image Storage (이미지 저장)

`@Profile` 기반 Strategy Pattern으로 환경별 이미지 저장소 분기.

| 환경      | 구현체                         | 저장소                                     |
|---------|-----------------------------|-----------------------------------------|
| `prod`  | `S3*ImageStorageService`    | S3 (`lut-images-prod`) + CloudFront CDN |
| `!prod` | `Local*ImageStorageService` | 로컬 파일시스템 + Spring MVC 리소스 핸들러           |

### S3 구현체 (prod)

- `S3Config` — `S3Client` Bean (`@Profile("prod")`, EC2 IAM Role 자동 인증)
- `S3ImageProperties` — `app.upload.s3.bucket` + `app.upload.s3.cdn-base-url`
- S3 키 패턴: `profile/{userId}/{uuid}.ext`, `guild/{guildId}/{uuid}.ext`, `missions/{userId}/{missionId}/{date}_{uuid}.ext`, `events/{uuid}.ext`
- CDN URL 반환: `https://images.level-up-together.com/{key}`

### 서비스별 구현체

| 서비스     | S3 구현체 (prod)                    | Local 구현체 (!prod)                   |
|---------|-----------------------------------|--------------------------------------|
| 프로필     | `S3ProfileImageStorageService`    | `LocalProfileImageStorageService`    |
| 길드      | `S3GuildImageStorageService`      | `LocalGuildImageStorageService`      |
| 미션      | `S3MissionImageStorageService`    | `LocalMissionImageStorageService`    |
| 이벤트     | `S3EventImageStorageService`      | `LocalEventImageStorageService`      |
| 상점 아이템  | `S3ShopItemImageStorageService`   | `LocalShopItemImageStorageService`   |

각 서비스가 독립된 인터페이스(`*ImageStorageService`)를 정의하며 공유 상위 타입은 없다 — 5개 구현체가 동일 패턴(`store`/`delete`/`isValidImage`)을 반복한다.

### 리사이즈 변형 (LUT-400, 미션 이미지 한정)

목록/그리드 화면(홈 피드, 프로필 피드 탭)이 수 MB 원본을 그대로 받아 디코딩하던 문제를 줄이기 위해, **미션 인증사진**은 업로드 시점에 `thumb`/`medium` 변형을 원본과 함께 S3(또는 로컬 디스크)에 저장한다. 홈 피드/피드 상세/프로필 피드 탭에 노출되는 사진은 전부 이 미션 인증사진 파이프라인 하나로 귀결되므로(피드는 `ActivityFeedImage`가 미션 실행 이미지 URL을 그대로 복제) 미션 이미지 저장소 계층만 수정하면 된다. 길드 로고/상점 아이템 스프라이트/프로필 아바타는 별도 자산군(작고 어드민/아바타성)이라 변형 대상이 아니다.

**네이밍 규칙** — 원본 URL/키 그대로 두고, 파일명 확장자 앞에 접미사만 삽입한다 (백엔드는 원본 1개 URL만 반환/저장 — 엔티티·이벤트·DTO 무변경):

```
원본:  missions/{userId}/{missionId}/{executionDate}_{uuid}.{ext}
thumb: missions/{userId}/{missionId}/{executionDate}_{uuid}_thumb.{ext}   (긴 변 ≤ 360px)
medium:missions/{userId}/{missionId}/{executionDate}_{uuid}_medium.{ext} (긴 변 ≤ 1080px)
```

프론트는 원본 URL 문자열에서 확장자 앞에 `_thumb`/`_medium`을 삽입해 변형 URL을 유도한다(`getImageVariantUrl`, `src/lib/api/image-utils.ts`). 원본이 목표 크기보다 이미 작으면 해당 변형은 생성되지 않으므로(불필요한 업스케일 방지) 변형 URL이 404가 날 수 있다 — 소비 측에서 원본으로 폴백해야 한다(과거 업로드 이미지도 동일하게 변형이 없다).

리사이즈는 `global.component.ImageResizer`(Thumbnailator 기반)가 담당하며, 디코딩/리사이즈 실패는 예외를 던지지 않고 원본 업로드만 진행한다(변형 생성은 부가 최적화).

### 설정

```yaml
# application.yml (기본값)
app:
  upload:
    s3:
      bucket: ""
      cdn-base-url: ""

# product-service-prod.yml (Config Server)
app:
  upload:
    s3:
      bucket: lut-images-prod
      cdn-base-url: https://images.level-up-together.com
```
