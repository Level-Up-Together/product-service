package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.image;

import io.pinkspider.global.config.s3.S3ImageProperties;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.WidgetMessageImageException;
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
 * LUT-797: S3 + CloudFront CDN 저장 (prod). 키 {@code widget/{uuid}.{ext}}, URL 은 {@code cdn-base-url}
 * 기준.
 */
@Service
@Profile("prod")
@Primary
@Slf4j
@RequiredArgsConstructor
public class S3WidgetImageStorageService implements WidgetImageStorageService {
    static final String KEY_PREFIX = "widget/";

    private final S3Client s3Client;
    private final S3ImageProperties s3Properties;
    private final WidgetImageProperties properties;

    @Override
    public String store(MultipartFile file) {
        String extension = WidgetImageFiles.validatedExtension(file, properties);
        String key = KEY_PREFIX + WidgetImageFiles.newFilename(extension);
        try {
            PutObjectRequest request =
                    PutObjectRequest.builder()
                            .bucket(s3Properties.getBucket())
                            .key(key)
                            .contentType(WidgetImageFiles.contentTypeOf(extension))
                            .cacheControl("public, max-age=31536000, immutable")
                            .build();
            s3Client.putObject(
                    request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("위젯 배경 이미지 S3 저장: key={}", key);
            return s3Properties.getCdnBaseUrl() + "/" + key;
        } catch (IOException e) {
            log.error("위젯 배경 이미지 S3 저장 실패: key={}", key, e);
            throw WidgetMessageImageException.saveFailed();
        }
    }

    @Override
    public boolean isOwned(String url) {
        return url != null && url.startsWith(s3Properties.getCdnBaseUrl() + "/" + KEY_PREFIX);
    }

    @Override
    public void delete(String url) {
        if (!isOwned(url)) {
            log.debug("위젯 배경 이미지 S3 삭제 대상 아님: {}", url);
            return;
        }
        String key = url.substring(s3Properties.getCdnBaseUrl().length() + 1);
        if (key.contains("..")) {
            log.warn("위젯 배경 이미지 S3 삭제 요청 거부: key={}", key);
            return;
        }
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(s3Properties.getBucket())
                            .key(key)
                            .build());
            log.info("위젯 배경 이미지 S3 삭제: key={}", key);
        } catch (RuntimeException e) {
            log.warn("위젯 배경 이미지 S3 삭제 실패: key={}", key, e);
        }
    }
}
