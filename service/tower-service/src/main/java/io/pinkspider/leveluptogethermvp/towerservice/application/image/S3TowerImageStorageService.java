package io.pinkspider.leveluptogethermvp.towerservice.application.image;

import io.pinkspider.global.config.s3.S3ImageProperties;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerImageKind;
import java.io.IOException;
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

/**
 * LUT-635: S3 + CloudFront CDN 저장 (prod). 키 {@code tower/{kind}/{uuid}.{ext}}, URL 은 {@code
 * cdn-base-url} 기준 — 운영 허용 호스트와 같다.
 */
@Service
@Profile("prod")
@Primary
@Slf4j
@RequiredArgsConstructor
public class S3TowerImageStorageService implements TowerImageStorageService {
    static final String KEY_PREFIX = "tower/";

    private final S3Client s3Client;
    private final S3ImageProperties s3Properties;
    private final TowerImageProperties properties;

    @Override
    public String store(MultipartFile file, TowerImageKind kind) {
        String extension = TowerImageFiles.validatedExtension(file, kind, properties);
        String key = KEY_PREFIX + kind.folder() + "/" + TowerImageFiles.newFilename(extension);
        try {
            PutObjectRequest request =
                    PutObjectRequest.builder()
                            .bucket(s3Properties.getBucket())
                            .key(key)
                            .contentType(file.getContentType())
                            .cacheControl("public, max-age=31536000, immutable")
                            .build();
            s3Client.putObject(
                    request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("탑 층 이미지 S3 저장: key={}", key);
            return s3Properties.getCdnBaseUrl() + "/" + key;
        } catch (IOException e) {
            log.error("탑 층 이미지 S3 저장 실패: key={}", key, e);
            throw TowerFloorException.imageSaveFailed();
        }
    }

    @Override
    public boolean isOwned(String url) {
        return url != null && url.startsWith(s3Properties.getCdnBaseUrl() + "/" + KEY_PREFIX);
    }

    @Override
    public void delete(String url) {
        if (!isOwned(url)) {
            log.debug("탑 층 이미지 S3 삭제 대상 아님: {}", url);
            return;
        }
        String key = url.substring(s3Properties.getCdnBaseUrl().length() + 1);
        if (key.contains("..")) {
            log.warn("탑 층 이미지 S3 삭제 요청 거부: key={}", key);
            return;
        }
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(s3Properties.getBucket())
                            .key(key)
                            .build());
            log.info("탑 층 이미지 S3 삭제: key={}", key);
        } catch (RuntimeException e) {
            log.warn("탑 층 이미지 S3 삭제 실패: key={}", key, e);
        }
    }
}
