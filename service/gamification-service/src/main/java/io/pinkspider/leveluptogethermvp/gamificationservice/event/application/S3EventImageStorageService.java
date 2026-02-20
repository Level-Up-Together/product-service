package io.pinkspider.leveluptogethermvp.gamificationservice.event.application;

import io.pinkspider.global.config.s3.S3ImageProperties;
import io.pinkspider.global.exception.CustomException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@Profile("prod")
@Primary
@Slf4j
@RequiredArgsConstructor
public class S3EventImageStorageService implements EventImageStorageService {

    private final S3Client s3Client;
    private final S3ImageProperties s3Properties;
    private final EventImageProperties properties;

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("EVENT_IMAGE_001", "업로드할 이미지 파일이 없습니다.");
        }

        if (!isValidImage(file)) {
            throw new CustomException("EVENT_IMAGE_002", "유효하지 않은 이미지 파일입니다.");
        }

        try {
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            String extension = getExtension(originalFilename);
            String newFilename = UUID.randomUUID().toString() + "." + extension;
            String key = "events/" + newFilename;

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            String cdnUrl = s3Properties.getCdnBaseUrl() + "/" + key;
            log.info("이벤트 이미지 S3 저장: key={}", key);
            return cdnUrl;

        } catch (IOException e) {
            log.error("이벤트 이미지 S3 저장 실패", e);
            throw new CustomException("EVENT_IMAGE_003", "이미지 저장에 실패했습니다.");
        }
    }

    @Override
    public void delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        if (imageUrl.startsWith(s3Properties.getCdnBaseUrl())) {
            String key = imageUrl.substring(s3Properties.getCdnBaseUrl().length() + 1);
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(s3Properties.getBucket())
                        .key(key)
                        .build());
                log.info("이벤트 이미지 S3 삭제: key={}", key);
            } catch (Exception e) {
                log.warn("이벤트 이미지 S3 삭제 실패: key={}", key, e);
            }
            return;
        }

        log.debug("S3 삭제 대상 아님 (로컬/외부 URL): {}", imageUrl);
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
