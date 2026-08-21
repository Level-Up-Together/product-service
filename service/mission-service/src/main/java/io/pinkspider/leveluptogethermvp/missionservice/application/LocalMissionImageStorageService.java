package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.component.ImageResizer;
import io.pinkspider.global.exception.CustomException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬 파일 시스템에 미션 이미지를 저장하는 구현체
 * 추후 S3 등으로 교체 시 S3MissionImageStorageService를 만들어 교체
 */
@Service
@Profile("!prod")
@Slf4j
@RequiredArgsConstructor
public class LocalMissionImageStorageService implements MissionImageStorageService {

    private static final String THUMB_SUFFIX = "_thumb";
    private static final String MEDIUM_SUFFIX = "_medium";

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
            // 저장 디렉토리 생성 (missions/{userId}/{missionId})
            Path uploadDir = Paths.get(properties.getPath(), userId, String.valueOf(missionId));
            Files.createDirectories(uploadDir);

            // 파일 이름 생성 (executionDate_UUID.확장자)
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            String extension = getExtension(originalFilename);
            String newFilename = executionDate + "_" + UUID.randomUUID().toString() + "." + extension;
            byte[] originalBytes = file.getBytes();

            // 파일 저장
            Path targetPath = uploadDir.resolve(newFilename);
            Files.write(targetPath, originalBytes);

            uploadVariant(uploadDir, newFilename, extension, originalBytes, THUMB_SUFFIX,
                    ImageResizer.THUMBNAIL_MAX_DIMENSION);
            uploadVariant(uploadDir, newFilename, extension, originalBytes, MEDIUM_SUFFIX,
                    ImageResizer.MEDIUM_MAX_DIMENSION);

            log.info("미션 이미지 저장: userId={}, missionId={}, path={}", userId, missionId, targetPath);

            // URL 반환 (서버 상대 경로)
            return properties.getUrlPrefix() + "/" + userId + "/" + missionId + "/" + newFilename;

        } catch (IOException e) {
            log.error("미션 이미지 저장 실패: userId={}, missionId={}", userId, missionId, e);
            throw new CustomException("MISSION_IMAGE_003", "error.image.save_failed");
        }
    }

    /** LUT-400: 원본과 같은 디렉터리에 리사이즈 변형(thumb/medium)을 best-effort로 함께 저장한다. */
    private void uploadVariant(Path uploadDir, String originalFilename, String extension,
            byte[] originalBytes, String suffix, int maxDimension) {
        try {
            Optional<byte[]> resized = imageResizer.resize(originalBytes, extension, maxDimension);
            if (resized.isEmpty()) {
                return;
            }
            Path variantPath = uploadDir.resolve(insertSuffix(originalFilename, suffix));
            Files.write(variantPath, resized.get());
        } catch (Exception e) {
            log.warn("미션 이미지 변형 저장 실패, 원본만 사용: file={}, suffix={}", originalFilename, suffix, e);
        }
    }

    private String insertSuffix(String filename, String suffix) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return filename + suffix;
        }
        return filename.substring(0, dotIndex) + suffix + filename.substring(dotIndex);
    }

    /**
     * LUT-409: 변형(thumb/medium)이 없는 과거 업로드 원본에 변형을 생성한다. 멱등 —
     * 이미 존재하는 변형 파일은 건너뛰고, 원본 바이트는 필요할 때 1회만 읽는다.
     * 리사이즈 불가 포맷(GIF 등)은 변형 없이 원본 fallback 을 유지한다 (업로드 경로와 동일 정책).
     */
    @Override
    public int backfillVariants(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(properties.getUrlPrefix() + "/")) {
            return 0;
        }
        String relativePath = imageUrl.substring(properties.getUrlPrefix().length());
        if (isVariantPath(relativePath)) {
            return 0;
        }

        Path originalPath = Paths.get(properties.getPath(), relativePath);
        if (!Files.exists(originalPath)) {
            throw new IllegalStateException("원본 파일 없음: " + originalPath);
        }

        try {
            String extension = getExtension(relativePath);
            byte[] originalBytes = null;
            int created = 0;

            for (Variant variant : List.of(
                    new Variant(THUMB_SUFFIX, ImageResizer.THUMBNAIL_MAX_DIMENSION),
                    new Variant(MEDIUM_SUFFIX, ImageResizer.MEDIUM_MAX_DIMENSION))) {
                Path variantPath = Paths.get(properties.getPath(),
                        insertSuffix(relativePath, variant.suffix()));
                if (Files.exists(variantPath)) {
                    continue;
                }
                if (originalBytes == null) {
                    originalBytes = Files.readAllBytes(originalPath);
                }
                Optional<byte[]> resized =
                        imageResizer.resize(originalBytes, extension, variant.maxDimension());
                if (resized.isEmpty()) {
                    continue;
                }
                Files.write(variantPath, resized.get());
                created++;
            }
            return created;
        } catch (IOException e) {
            throw new IllegalStateException("변형 백필 실패: " + originalPath, e);
        }
    }

    private record Variant(String suffix, int maxDimension) {}

    private boolean isVariantPath(String path) {
        int dotIndex = path.lastIndexOf('.');
        String base = dotIndex < 0 ? path : path.substring(0, dotIndex);
        return base.endsWith(THUMB_SUFFIX) || base.endsWith(MEDIUM_SUFFIX);
    }

    @Override
    public void delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        // 외부 URL은 삭제하지 않음
        if (!imageUrl.startsWith(properties.getUrlPrefix())) {
            log.debug("외부 이미지 URL은 삭제하지 않음: {}", imageUrl);
            return;
        }

        String relativePath = imageUrl.substring(properties.getUrlPrefix().length());
        deleteIfExists(relativePath);
        deleteIfExists(insertSuffix(relativePath, THUMB_SUFFIX));
        deleteIfExists(insertSuffix(relativePath, MEDIUM_SUFFIX));
    }

    private void deleteIfExists(String relativePath) {
        try {
            Path filePath = Paths.get(properties.getPath(), relativePath);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("미션 이미지 삭제: path={}", filePath);
            }
        } catch (IOException e) {
            log.warn("미션 이미지 삭제 실패: path={}", relativePath, e);
            // 삭제 실패는 치명적이지 않으므로 예외를 던지지 않음
        }
    }

    @Override
    public boolean isValidImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        // 파일 크기 검사
        if (file.getSize() > properties.getMaxSize()) {
            log.warn("파일 크기 초과: size={}, maxSize={}", file.getSize(), properties.getMaxSize());
            return false;
        }

        // 확장자 검사
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

        // MIME 타입 검사
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            log.warn("유효하지 않은 MIME 타입: contentType={}", contentType);
            return false;
        }

        return true;
    }

    /**
     * 파일 이름에서 확장자 추출
     */
    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
