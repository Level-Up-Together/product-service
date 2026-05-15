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

| 서비스 | S3 구현체 (prod)                  | Local 구현체 (!prod)                 |
|-----|--------------------------------|-----------------------------------|
| 프로필 | `S3ProfileImageStorageService` | `LocalProfileImageStorageService` |
| 길드  | `S3GuildImageStorageService`   | `LocalGuildImageStorageService`   |
| 미션  | `S3MissionImageStorageService` | `LocalMissionImageStorageService` |
| 이벤트 | `S3EventImageStorageService`   | `LocalEventImageStorageService`   |

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
