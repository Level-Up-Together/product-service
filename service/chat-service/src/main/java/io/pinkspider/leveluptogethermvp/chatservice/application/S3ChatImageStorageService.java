package io.pinkspider.leveluptogethermvp.chatservice.application;

import static io.pinkspider.leveluptogethermvp.chatservice.application.ChatImageFileValidator.MEDIUM_SUFFIX;
import static io.pinkspider.leveluptogethermvp.chatservice.application.ChatImageFileValidator.THUMB_SUFFIX;
import static io.pinkspider.leveluptogethermvp.chatservice.application.ChatImageFileValidator.insertSuffix;

import io.pinkspider.global.component.ImageResizer;
import io.pinkspider.global.config.s3.S3ImageProperties;
import io.pinkspider.global.exception.CustomException;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** S3 + CloudFront 채팅 이미지 저장소 (prod, LUT-590). 키: chats/{guildId}/{userId}/{uuid}.{ext} */
@Service
@Profile("prod")
@Primary
@Slf4j
@RequiredArgsConstructor
public class S3ChatImageStorageService implements ChatImageStorageService {

    private final S3Client s3Client;
    private final S3ImageProperties s3Properties;
    private final ChatImageProperties properties;
    private final ChatImageFileValidator validator;
    private final ImageResizer imageResizer;

    @Override
    public String store(MultipartFile file, Long guildId, String userId) {
        String extension = validator.validate(file);
        try {
            String key =
                    properties.getS3KeyPrefix()
                            + "/"
                            + guildId
                            + "/"
                            + userId
                            + "/"
                            + UUID.randomUUID()
                            + "."
                            + extension;
            byte[] originalBytes = file.getBytes();
            // 클라이언트 Content-Type 은 신뢰하지 않는다 — image/svg+xml 로 저장되면 CDN 에서 스크립트가 실행될 수 있다
            String contentType = ChatImageFileValidator.contentTypeOf(extension);
            putObject(key, contentType, originalBytes);

            putVariant(
                    key,
                    extension,
                    contentType,
                    originalBytes,
                    THUMB_SUFFIX,
                    ImageResizer.THUMBNAIL_MAX_DIMENSION);
            putVariant(
                    key,
                    extension,
                    contentType,
                    originalBytes,
                    MEDIUM_SUFFIX,
                    ImageResizer.MEDIUM_MAX_DIMENSION);

            log.info("채팅 이미지 S3 저장: guildId={}, userId={}, key={}", guildId, userId, key);
            return s3Properties.getCdnBaseUrl() + "/" + key;
        } catch (IOException e) {
            log.error("채팅 이미지 S3 저장 실패: guildId={}, userId={}", guildId, userId, e);
            throw new CustomException("CHAT_IMAGE_003", "error.image.save_failed");
        }
    }

    private void putVariant(
            String originalKey,
            String extension,
            String contentType,
            byte[] originalBytes,
            String suffix,
            int maxDimension) {
        try {
            Optional<byte[]> resized = imageResizer.resize(originalBytes, extension, maxDimension);
            if (resized.isPresent()) {
                putObject(insertSuffix(originalKey, suffix), contentType, resized.get());
            }
        } catch (Exception e) {
            log.warn("채팅 이미지 변형 저장 실패, 원본만 사용: key={}, suffix={}", originalKey, suffix, e);
        }
    }

    private void putObject(String key, String contentType, byte[] bytes) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(s3Properties.getBucket())
                        .key(key)
                        .contentType(contentType)
                        // 브라우저가 내용으로 타입을 추측하지 않도록 inline 이미지로 고정
                        .contentDisposition("inline")
                        // UUID 파일명이라 콘텐츠 불변 — CloudFront/브라우저 공격적 캐시 허용 (LUT-406 관례)
                        .cacheControl("public, max-age=31536000, immutable")
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    @Override
    public void delete(String imageUrl) {
        if (!isStoredUrl(imageUrl)) {
            return;
        }
        String key = imageUrl.substring(s3Properties.getCdnBaseUrl().length() + 1);
        deleteObject(key);
        deleteObject(insertSuffix(key, THUMB_SUFFIX));
        deleteObject(insertSuffix(key, MEDIUM_SUFFIX));
    }

    private void deleteObject(String key) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(s3Properties.getBucket())
                            .key(key)
                            .build());
        } catch (Exception e) {
            log.warn("채팅 이미지 S3 삭제 실패: key={}", key, e);
        }
    }

    @Override
    public boolean isOwnedBy(String imageUrl, Long guildId, String userId) {
        return guildId != null
                && userId != null
                && isStoredUrl(imageUrl)
                && imageUrl.startsWith(
                        s3Properties.getCdnBaseUrl()
                                + "/"
                                + properties.getS3KeyPrefix()
                                + "/"
                                + guildId
                                + "/"
                                + userId
                                + "/");
    }

    /** 이 저장소의 채팅 이미지 URL 인지 (상위 경로 {@code ..} 거부) — 삭제 대상 한정용 */
    private boolean isStoredUrl(String imageUrl) {
        return imageUrl != null
                && imageUrl.startsWith(
                        s3Properties.getCdnBaseUrl() + "/" + properties.getS3KeyPrefix() + "/")
                && !imageUrl.contains("..");
    }
}
