package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.component.ImageResizer;
import io.pinkspider.global.config.s3.S3ImageProperties;
import io.pinkspider.global.exception.CustomException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@Profile("prod")
@Primary
@Slf4j
@RequiredArgsConstructor
public class S3MissionImageStorageService implements MissionImageStorageService {

    private static final String THUMB_SUFFIX = "_thumb";
    private static final String MEDIUM_SUFFIX = "_medium";

    private final S3Client s3Client;
    private final S3ImageProperties s3Properties;
    private final MissionImageProperties properties;
    private final ImageResizer imageResizer;

    @Override
    public String store(MultipartFile file, String userId, Long missionId, String executionDate) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("MISSION_IMAGE_001", "error.image.empty");
        }

        if (!isValidImage(file)) {
            throw new CustomException("MISSION_IMAGE_002", "error.image.invalid");
        }

        try {
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            String extension = getExtension(originalFilename);
            String newFilename = executionDate + "_" + UUID.randomUUID().toString() + "." + extension;
            String key = "missions/" + userId + "/" + missionId + "/" + newFilename;
            byte[] originalBytes = file.getBytes();

            putObject(key, file.getContentType(), originalBytes);

            uploadVariant(key, extension, file.getContentType(), originalBytes, THUMB_SUFFIX,
                    ImageResizer.THUMBNAIL_MAX_DIMENSION);
            uploadVariant(key, extension, file.getContentType(), originalBytes, MEDIUM_SUFFIX,
                    ImageResizer.MEDIUM_MAX_DIMENSION);

            String cdnUrl = s3Properties.getCdnBaseUrl() + "/" + key;
            log.info("미션 이미지 S3 저장: userId={}, missionId={}, key={}", userId, missionId, key);
            return cdnUrl;

        } catch (IOException e) {
            log.error("미션 이미지 S3 저장 실패: userId={}, missionId={}", userId, missionId, e);
            throw new CustomException("MISSION_IMAGE_003", "error.image.save_failed");
        }
    }

    /** LUT-400: 원본과 같은 디렉터리에 리사이즈 변형(thumb/medium)을 best-effort로 함께 저장한다. */
    private void uploadVariant(String originalKey, String extension, String contentType,
            byte[] originalBytes, String suffix, int maxDimension) {
        try {
            Optional<byte[]> resized = imageResizer.resize(originalBytes, extension, maxDimension);
            if (resized.isEmpty()) {
                return;
            }
            String variantKey = insertSuffix(originalKey, suffix);
            putObject(variantKey, contentType, resized.get());
        } catch (Exception e) {
            log.warn("미션 이미지 변형 저장 실패, 원본만 사용: key={}, suffix={}", originalKey, suffix, e);
        }
    }

    private void putObject(String key, String contentType, byte[] bytes) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(key)
                .contentType(contentType)
                // LUT-406: UUID 파일명 불변 — CloudFront/브라우저 공격적 캐시 허용
                .cacheControl("public, max-age=31536000, immutable")
                .build();
        s3Client.putObject(putRequest, RequestBody.fromBytes(bytes));
    }

    private String insertSuffix(String key, String suffix) {
        int dotIndex = key.lastIndexOf('.');
        if (dotIndex < 0) {
            return key + suffix;
        }
        return key.substring(0, dotIndex) + suffix + key.substring(dotIndex);
    }

    /**
     * LUT-409: 변형(thumb/medium)이 없는 과거 업로드 원본에 변형을 생성한다. 멱등 —
     * 이미 존재하는 변형은 headObject 로 확인해 건너뛰고, 원본은 필요할 때 1회만 내려받는다.
     * 리사이즈 불가 포맷(GIF 등)은 변형 없이 원본 fallback 을 유지한다 (업로드 경로와 동일 정책).
     */
    @Override
    public int backfillVariants(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(s3Properties.getCdnBaseUrl() + "/")) {
            return 0;
        }
        String key = imageUrl.substring(s3Properties.getCdnBaseUrl().length() + 1);
        if (isVariantKey(key)) {
            return 0;
        }

        String extension = getExtension(key);
        byte[] originalBytes = null;
        String contentType = null;
        int created = 0;

        for (Variant variant : List.of(
                new Variant(THUMB_SUFFIX, ImageResizer.THUMBNAIL_MAX_DIMENSION),
                new Variant(MEDIUM_SUFFIX, ImageResizer.MEDIUM_MAX_DIMENSION))) {
            String variantKey = insertSuffix(key, variant.suffix());
            if (objectExists(variantKey)) {
                continue;
            }
            if (originalBytes == null) {
                ResponseBytes<GetObjectResponse> original = s3Client.getObjectAsBytes(
                        GetObjectRequest.builder()
                                .bucket(s3Properties.getBucket())
                                .key(key)
                                .build());
                originalBytes = original.asByteArray();
                contentType = original.response().contentType();
            }
            Optional<byte[]> resized = imageResizer.resize(originalBytes, extension, variant.maxDimension());
            if (resized.isEmpty()) {
                continue;
            }
            putObject(variantKey, contentType, resized.get());
            created++;
        }
        return created;
    }

    private record Variant(String suffix, int maxDimension) {}

    private boolean isVariantKey(String key) {
        int dotIndex = key.lastIndexOf('.');
        String base = dotIndex < 0 ? key : key.substring(0, dotIndex);
        return base.endsWith(THUMB_SUFFIX) || base.endsWith(MEDIUM_SUFFIX);
    }

    private boolean objectExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public void delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        if (imageUrl.startsWith(s3Properties.getCdnBaseUrl())) {
            String key = imageUrl.substring(s3Properties.getCdnBaseUrl().length() + 1);
            deleteObject(key);
            deleteObject(insertSuffix(key, THUMB_SUFFIX));
            deleteObject(insertSuffix(key, MEDIUM_SUFFIX));
            return;
        }

        log.debug("S3 삭제 대상 아님 (로컬/외부 URL): {}", imageUrl);
    }

    private void deleteObject(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .build());
            log.info("미션 이미지 S3 삭제: key={}", key);
        } catch (Exception e) {
            log.warn("미션 이미지 S3 삭제 실패: key={}", key, e);
        }
    }

    @Override
    public boolean isValidImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        if (file.getSize() > properties.getMaxSize()) {
            log.warn("파일 크기 초과: size={}, maxSize={}", file.getSize(), properties.getMaxSize());
            return false;
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        String extension = getExtension(filename).toLowerCase();
        List<String> allowedExtensions = properties.getAllowedExtensionList();
        if (!allowedExtensions.contains(extension)) {
            log.warn("허용되지 않은 확장자: extension={}, allowed={}", extension, allowedExtensions);
            return false;
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            log.warn("유효하지 않은 MIME 타입: contentType={}", contentType);
            return false;
        }

        return true;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
