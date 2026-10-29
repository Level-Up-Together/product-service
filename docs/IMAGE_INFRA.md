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
- `ChatImageService.uploadGuildChatImage` / `uploadDirectMessageImage` — 길드 채팅·DM 이미지 (LUT-590)

상점 아이템·다이아 묶음 이미지(`ShopItemAdminService`/`DiamondBundleAdminService` — 어드민 내부 API 업로드, 둘 다 `ShopItemImageStorageService` 사용)는 검증 대상이 아니다.

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
- S3 키 패턴: `profile/{userId}/{uuid}.ext`, `guild/{guildId}/{uuid}.ext`, `missions/{userId}/{missionId}/{date}_{uuid}.ext`, `events/{uuid}.ext`, `shop-items/{uuid}.ext`, `chats/{guildId}/{userId}/{uuid}.ext`(LUT-590)
- CDN URL 반환: `https://images.level-up-together.com/{key}`

### 서비스별 구현체

| 서비스     | S3 구현체 (prod)                    | Local 구현체 (!prod)                   |
|---------|-----------------------------------|--------------------------------------|
| 프로필     | `S3ProfileImageStorageService`    | `LocalProfileImageStorageService`    |
| 길드      | `S3GuildImageStorageService`      | `LocalGuildImageStorageService`      |
| 미션      | `S3MissionImageStorageService`    | `LocalMissionImageStorageService`    |
| 이벤트     | `S3EventImageStorageService`      | `LocalEventImageStorageService`      |
| 상점 아이템  | `S3ShopItemImageStorageService`   | `LocalShopItemImageStorageService`   |
| 길드 채팅·DM | `S3ChatImageStorageService`      | `LocalChatImageStorageService`       |

각 서비스가 독립된 인터페이스(`*ImageStorageService`)를 정의하며 공유 상위 타입은 없다 — 프로필·길드·미션·이벤트·상점 5종은 동일 패턴(`store`/`delete`/`isValidImage`)을 반복한다.
채팅(LUT-590)만 검증·파일명 유틸을 로컬/S3 공용 `ChatImageFileValidator` 로 뽑았고, 인터페이스에 `isOwnedBy` 가 추가돼 있다(아래 절).

**캐시 헤더** (LUT-406): 파일명이 UUID 라 콘텐츠가 불변이므로 6종 모두 공격적 캐시를 허용한다 — 로컬은 `*ImageWebConfig` 리소스 핸들러의
`setCacheControl(1년, public, immutable)`(Cache-Control 이 이미 있으면 Spring Security 가 기본 no-cache 헤더로 덮지 않는다), S3 는 `putObject` 의
`Cache-Control: public, max-age=31536000, immutable` 메타데이터. S3 쪽은 신규 업로드부터 적용된다(기존 객체 소급 없음).

### 리사이즈 변형 (LUT-400 미션 · LUT-590 채팅)

목록/그리드 화면(홈 피드, 프로필 피드 탭)이 수 MB 원본을 그대로 받아 디코딩하던 문제를 줄이기 위해, **미션 인증사진**은 업로드 시점에 `thumb`/`medium` 변형을 원본과 함께 S3(또는 로컬 디스크)에 저장한다. 홈 피드/피드 상세/프로필 피드 탭에 노출되는 사진은 전부 이 미션 인증사진 파이프라인 하나로 귀결되므로(피드는 `ActivityFeedImage`가 미션 실행 이미지 URL을 그대로 복제) 미션 이미지 저장소 계층만 수정하면 된다. 길드 로고/상점 아이템 스프라이트/프로필 아바타는 별도 자산군(작고 어드민/아바타성)이라 변형 대상이 아니다.

**네이밍 규칙** — 원본 URL/키 그대로 두고, 파일명 확장자 앞에 접미사만 삽입한다 (백엔드는 원본 1개 URL만 반환/저장 — 엔티티·이벤트·DTO 무변경):

```
원본:  missions/{userId}/{missionId}/{executionDate}_{uuid}.{ext}
thumb: missions/{userId}/{missionId}/{executionDate}_{uuid}_thumb.{ext}   (긴 변 ≤ 360px)
medium:missions/{userId}/{missionId}/{executionDate}_{uuid}_medium.{ext} (긴 변 ≤ 1080px)
```

프론트는 원본 URL 문자열에서 확장자 앞에 `_thumb`/`_medium`을 삽입해 변형 URL을 유도한다(`getImageVariantUrl`, `src/lib/api/image-utils.ts`). 원본이 목표 크기보다 이미 작으면 해당 변형은 생성되지 않으므로(불필요한 업스케일 방지) 변형 URL이 404가 날 수 있다 — 소비 측에서 원본으로 폴백해야 한다(과거 업로드 이미지도 동일하게 변형이 없다).

리사이즈는 `global.component.ImageResizer`(Thumbnailator 기반, `THUMBNAIL_MAX_DIMENSION=360` / `MEDIUM_MAX_DIMENSION=1080`)가 담당하며, 디코딩/리사이즈 실패(`ImageIO` 가 읽지 못하는 포맷 포함)는 예외를 던지지 않고 원본 업로드만 진행한다(변형 생성은 부가 최적화).

길드 채팅·DM 이미지(LUT-590)도 같은 `ImageResizer`·같은 접미사 규칙으로 `_thumb`/`_medium` 을 원본 옆에 만든다(`chats/{guildId}/{userId}/{uuid}_thumb.{ext}`). 삭제 시 원본과 두 변형을 함께 지운다.

**과거 이미지 변형 백필** (LUT-409): `POST /api/internal/mission-images/backfill-variants?limit=`(`MissionImageVariantBackfillInternalController` →
`MissionImageVariantBackfillService`) — LUT-400 이전 업로드분에 변형을 만든다. 스캔 대상은 `mission_execution_image.image_url` + `mission_execution.image_url`
(레거시 대표 이미지) distinct 합집합이고, 변형 존재 여부를 저장소에서 직접 확인하므로 멱등(재실행 시 생성분은 skip, 실패분은 다음 실행에서 재시도).
트랜잭션 없이 스토리지 I/O 만 하며 결과는 `total/scanned/created/skipped/failed` 건수. 고정 미션 인스턴스 이미지(`DailyMissionInstanceImage`)와 채팅 이미지는
스캔하지 않는다.

### 길드 채팅·DM 이미지 (LUT-590) — `chatservice/application/ChatImage*`

업로드와 전송이 분리된 **2단계**다. 전송 API·데이터 모델은 원래 `image_url` 을 받고 있었고 업로드 경로만 없었다.

1. 업로드 — 파일을 저장하고 `ChatImageUploadResponse{image_url}`(원본 URL)을 돌려준다.

   | 엔드포인트 | 서비스 | 권한 |
   |---|---|---|
   | `POST /api/v1/guilds/{guildId}/chat/images` | `ChatImageService.uploadGuildChatImage` | 길드 존재 + 업로더가 활성 길드원 |
   | `POST /api/v1/guilds/{guildId}/dm/images/{recipientId}` | `ChatImageService.uploadDirectMessageImage` | 양쪽 모두 활성 길드원 + 발신자가 상대를 차단했으면 거부(`error.dm.blocked_user`) |

   multipart 파트 이름은 `image`, 두 메서드 모두 `@ModerateImage`. 파일 검증(`ChatImageFileValidator`): 비었으면 `error.image.empty`, 5MB 초과·확장자
   (`jpg,jpeg,png,gif,webp`) 밖·`image/*` 아닌 Content-Type 이면 `error.image.invalid`.
2. 전송 — 클라이언트가 그 URL 을 기존 전송(REST `POST .../chat`·`.../dm/send/{recipientId}`, WS 동일)의 `image_url` 로 보낸다.
   `GuildChatService.sendMessage` / `GuildDirectMessageService.sendMessage` 가 `ChatImageService.validateImageUrl` 로 검증한다.

- **소유 URL 검증** — `image_url` 이 비면 통과(텍스트), 값이 있으면 저장소 `isOwnedBy(url, guildId, senderId)` 가 참이어야 한다 = 이 저장소 접두어
  + `/{guildId}/{senderId}/` 로 시작하고 `..` 미포함. 아니면 `CustomException("CHAT_IMAGE_004", "error.chat.invalid_image_url")`. 막는 것: ① 임의 외부 URL
  로 추적 이미지 주입 ② 남이 올린 사진 URL 로 메시지를 보낸 뒤 삭제해 그 사람 파일을 지우기.
- **저장 경로** — 로컬(`!prod`) `{app.upload.chat-image.path}/{guildId}/{userId}/{uuid}.{ext}`(yml 기본 `${user.dir}/uploads/chats`, URL `/uploads/chats/...`,
  `ChatImageWebConfig` 가 정적 서빙) / S3(`prod`) 키 `chats/{guildId}/{userId}/{uuid}.{ext}`(`s3-key-prefix`) → CDN URL.
- **S3 MIME 고정** — 클라이언트 Content-Type 을 쓰지 않고 확장자별 고정 MIME(`ChatImageFileValidator.contentTypeOf`) + `Content-Disposition: inline`
  으로 저장한다(`photo.jpg` 에 `image/svg+xml` 을 붙여 올려 CDN 이 SVG 로 서빙하는 것 방지). 다른 5종 S3 구현체는 아직 `file.getContentType()` 을 그대로 쓴다.
- **삭제** — 메시지 삭제(`GuildChatMessage.delete()` / `GuildDirectMessage.delete()`)가 본문을 `[삭제된 메시지입니다]` 로 바꾸고 `imageUrl=null` 로 걷어낸 뒤,
  `ChatImageService.deleteAfterCommit` 이 **삭제 트랜잭션 커밋 후** 원본+변형 파일을 지운다(롤백 시 파일만 사라지는 일 방지, 트랜잭션 밖이면 즉시).
  메시지 발신자 본인 경로(`isOwnedBy`)가 아닌 URL 은 삭제를 건너뛴다(검증 도입 전 저장된 메시지 대비).
- **DM 본문 완화** — `DirectMessageRequest` 의 본문 `@NotBlank` 를 "본문 또는 사진" `@AssertTrue`(`isContentOrImagePresent`)로 바꿨다(구앱은 항상 본문을
  보내므로 호환). 사진만 보낸 DM 은 대화방 `lastMessageContent` 가 빈 문자열이다(웹이 "사진" 라벨로 표시).
- **푸시 미리보기** — 본문 없이 사진만 보낸 길드 채팅·DM 은 `NotificationEventListener` 가 빈 본문 대신 `notification.preview.photo`
  (ko "사진을 보냈습니다." 등 4개 로케일)를 **수신자 locale** 로 현지화해 넣는다(`sendLocalizedNotification`).

### 설정

```yaml
# application.yml (기본값)
app:
  upload:
    chat-image: # LUT-590 (profile-image / mission-image 도 같은 형태)
      path: ${user.dir}/uploads/chats
      max-size: 5242880
      allowed-extensions: jpg,jpeg,png,gif,webp
      url-prefix: /uploads/chats
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
