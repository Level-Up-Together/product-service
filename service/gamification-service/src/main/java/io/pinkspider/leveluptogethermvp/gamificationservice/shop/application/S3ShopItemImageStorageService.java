package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

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

/**
 * S3 + CloudFront CDN에 상점 아이템 이미지를 저장하는 구현체 (QA-225, prod 전용)
 */
@Service
@Profile("prod")
@Primary
@Slf4j
@RequiredArgsConstructor
public class S3ShopItemImageStorageService implements ShopItemImageStorageService {

    private static final String KEY_PREFIX = "shop-items/";

    private final S3Client s3Client;
    private final S3ImageProperties s3Properties;
    private final ShopItemImageProperties properties;

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("SHOP_ITEM_IMAGE_001", "error.image.empty");
        }

        if (!isValidImage(file)) {
            throw new CustomException("SHOP_ITEM_IMAGE_002", "error.image.invalid");
        }

        try {
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            String extension = getExtension(originalFilename);
            String newFilename = UUID.randomUUID().toString() + "." + extension;
            String key = KEY_PREFIX + newFilename;

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            String cdnUrl = s3Properties.getCdnBaseUrl() + "/" + key;
            log.info("상점 아이템 이미지 S3 저장: key={}", key);
            return cdnUrl;

        } catch (IOException e) {
            log.error("상점 아이템 이미지 S3 저장 실패", e);
            throw new CustomException("SHOP_ITEM_IMAGE_003", "error.image.save_failed");
        }
    }

    @Override
    public void delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        if (imageUrl.startsWith(s3Properties.getCdnBaseUrl())) {
            String key = imageUrl.substring(s3Properties.getCdnBaseUrl().length() + 1);
            // shop-items/ prefix 밖 오브젝트(프로필/길드 이미지 등) 삭제 차단
            if (!key.startsWith(KEY_PREFIX) || key.contains("..")) {
                log.warn("shop-items/ 밖 S3 오브젝트 삭제 요청 거부: key={}", key);
                return;
            }
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(s3Properties.getBucket())
                        .key(key)
                        .build());
                log.info("상점 아이템 이미지 S3 삭제: key={}", key);
            } catch (Exception e) {
                log.warn("상점 아이템 이미지 S3 삭제 실패: key={}", key, e);
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

        // 클라이언트 제공 MIME은 신뢰하지 않고 실제 바이트 시그니처로 검증
        if (!hasValidImageSignature(file)) {
            log.warn("이미지 시그니처 불일치: filename={}", filename);
            return false;
        }

        return true;
    }

    private boolean hasValidImageSignature(MultipartFile file) {
        try (java.io.InputStream is = file.getInputStream()) {
            byte[] h = is.readNBytes(12);
            if (h.length < 4) {
                return false;
            }
            // PNG
            if ((h[0] & 0xFF) == 0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47) {
                return true;
            }
            // JPEG
            if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
                return true;
            }
            // GIF8
            if (h[0] == 0x47 && h[1] == 0x49 && h[2] == 0x46 && h[3] == 0x38) {
                return true;
            }
            // WEBP (RIFF....WEBP)
            return h.length >= 12
                && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
        } catch (IOException e) {
            return false;
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
