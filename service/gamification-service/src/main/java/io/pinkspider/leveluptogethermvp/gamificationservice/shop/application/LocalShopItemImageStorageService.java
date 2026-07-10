package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import io.pinkspider.global.exception.CustomException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬 파일 시스템에 상점 아이템 이미지를 저장하는 구현체 (QA-225)
 */
@Service
@Profile("!prod")
@Slf4j
@RequiredArgsConstructor
public class LocalShopItemImageStorageService implements ShopItemImageStorageService {

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
            Path uploadDir = Paths.get(properties.getPath());
            Files.createDirectories(uploadDir);

            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            String extension = getExtension(originalFilename);
            String newFilename = UUID.randomUUID().toString() + "." + extension;

            Path targetPath = uploadDir.resolve(newFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("상점 아이템 이미지 저장: path={}", targetPath);
            return properties.getUrlPrefix() + "/" + newFilename;

        } catch (IOException e) {
            log.error("상점 아이템 이미지 저장 실패", e);
            throw new CustomException("SHOP_ITEM_IMAGE_003", "error.image.save_failed");
        }
    }

    @Override
    public void delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        if (!imageUrl.startsWith(properties.getUrlPrefix())) {
            log.debug("외부 이미지 URL은 삭제하지 않음: {}", imageUrl);
            return;
        }

        try {
            String relativePath = imageUrl.substring(properties.getUrlPrefix().length());
            while (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            // 경로 이탈 방지: 업로드 루트 밖 파일 삭제 차단 (저장 파일명은 UUID.ext 단일 세그먼트)
            if (relativePath.isEmpty() || relativePath.contains("..")
                || relativePath.contains("/") || relativePath.contains("\\")) {
                log.warn("업로드 루트 밖 삭제 요청 거부: url={}", imageUrl);
                return;
            }

            Path uploadRoot = Paths.get(properties.getPath()).toAbsolutePath().normalize();
            Path filePath = uploadRoot.resolve(relativePath).normalize();
            if (!filePath.startsWith(uploadRoot)) {
                log.warn("업로드 루트 밖 삭제 요청 거부: path={}", filePath);
                return;
            }

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("상점 아이템 이미지 삭제: path={}", filePath);
            }
        } catch (IOException e) {
            log.warn("상점 아이템 이미지 삭제 실패: url={}", imageUrl, e);
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
